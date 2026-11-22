package org.easypaper.catalog

import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import java.io.File
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/**
 * Belarus: the State Register of Medicinal Products (УП «Центр экспертиз и испытаний в здравоохранении»,
 * rceth.by/Refbank). There is no download, so the import pages through the register's public search the way its
 * own page does (registration type "national" and "EAEU rules", 100 rows a page, one request a second) and keeps
 * the rows in "rceth-rows.tsv", so a later run can rebuild from that file with --from. The card links to the
 * record's page on rceth.by.
 */
object Belarus {
    const val REGISTRY = "by-rceth"
    const val NAME = "Государственный реестр лекарственных средств Республики Беларусь"
    const val BASE = "https://www.rceth.by"
    private const val LIST = "$BASE/Refbank/reestr_lekarstvennih_sredstv"
    private const val RESULTS = "$LIST/results"
    const val ROWS_FILE = "rceth-rows.tsv"

    /** One register row: trade name, international name, release forms, holder, number, link to the record. */
    data class Row(val trade: String, val inn: String, val forms: String, val holder: String, val number: String, val link: String)

    /** Pages through the public search and writes [ROWS_FILE] into [dir]; [pause] between requests. */
    fun crawl(dir: File, pause: Duration = Duration.ofSeconds(1)): File {
        val http = HttpClient.newBuilder().cookieHandler(CookieManager()).followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30)).build()
        fun get(url: String) = http.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofString()).body()
        fun post(fields: List<Pair<String, String>>): String {
            val body = fields.joinToString("&") { (k, v) -> URLEncoder.encode(k, Charsets.UTF_8) + "=" + URLEncoder.encode(v, Charsets.UTF_8) }
            Thread.sleep(pause.toMillis())
            return http.send(request(RESULTS).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).body()
        }
        val rows = LinkedHashMap<String, Row>()
        for (kind in listOf("1", "2")) { // national registration, EAEU rules
            val first = get(LIST)
            var page = post(formFields(first).map { (k, v) -> k to if (k == "FProps[2].CritElems[0].Val") kind else v }
                .filter { it.first !in setOf("FOpt.PageC", "FOpt.VEField1") } + listOf("FOpt.PageC" to "100", "FOpt.VEField1" to "true")) // show the release forms
            val pages = Regex("""Найдено.*?(\d+)\s*запис\p{L}*\s*-\s*(\d+)\s*страниц""", RegexOption.DOT_MATCHES_ALL).find(page)
                ?.groupValues?.get(2)?.toInt() ?: error("rceth.by: no result count for registration type $kind")
            for (n in 1..pages) {
                if (n > 1) {
                    val fields = formFields(page).filter { it.first !in setOf("IsPostBack", "PropSubmit", "ValueSubmit") }
                    page = post(fields + listOf("IsPostBack" to "true", "PropSubmit" to "FOpt_PageN", "ValueSubmit" to n.toString()))
                }
                for (row in rows(page)) rows[row.number + "|" + row.forms] = row
            }
        }
        check(rows.isNotEmpty()) { "rceth.by: no rows" }
        dir.mkdirs()
        val file = File(dir, ROWS_FILE)
        file.writeText(rows.values.joinToString("\n") { r -> listOf(r.trade, r.inn, r.forms, r.holder, r.number, r.link).joinToString("\t") { it.replace('\t', ' ') } })
        return file
    }

    /** Builds the products from the rows file; one version per release form with its doses. */
    fun read(rowsFile: File, dataDate: String): List<CatalogProduct> {
        val products = LinkedHashMap<String, CatalogProduct>()
        for (line in rowsFile.readLines()) {
            val f = line.split('\t')
            if (f.size < 6) continue
            val row = Row(f[0], f[1], f[2], f[3], f[4], f[5])
            for (release in row.forms.split(";").map { it.trim() }.filter { it.isNotEmpty() }) {
                if (release.startsWith("субстанция", ignoreCase = true)) continue
                val form = EastForms.parse(release)
                val key = sha("${row.number}|${form.form}|${form.doses}")
                products.getOrPut("$REGISTRY:$key") {
                    CatalogProduct(
                        id = "$REGISTRY:$key",
                        card = MedicineProduct(
                            name = listOf(row.trade, form.form, form.doses.joinToString("+") { "${it.first} ${it.second}" })
                                .filter { it.isNotEmpty() }.joinToString(", "),
                            brand = row.trade,
                            form = form.form,
                            ingredients = EastForms.ingredients(row.inn, form.doses),
                            perUnit = form.perVolume ?: form.form,
                            doseUnit = EastForms.countedUnit(form),
                            source = RegistrySource(REGISTRY, NAME, "BY", row.number, row.link.ifEmpty { LIST }, dataDate),
                        ),
                    )
                }
            }
        }
        return products.values.toList()
    }

    /** The result rows, columns found by their headers (the page shows or hides columns by its options). */
    internal fun rows(page: String): List<Row> {
        val trs = Regex("""<tr.*?</tr>""", RegexOption.DOT_MATCHES_ALL).findAll(page).map { it.value }.toList()
        // the header cells are <td> on this site
        val header = trs.map { tr -> Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", RegexOption.DOT_MATCHES_ALL).findAll(tr).map { clean(it.groupValues[1]) }.toList() }
            .firstOrNull { h -> h.any { it.startsWith("Торговое наименование") } } ?: return emptyList()
        fun col(prefix: String) = header.indexOfFirst { it.startsWith(prefix) }
        val trade = col("Торговое наименование")
        val inn = col("Международное наименование")
        val forms = col("Формы выпуска")
        val holder = col("Держатель")
        val number = col("Номер удостоверения")
        check(trade >= 0 && inn >= 0 && forms >= 0 && number >= 0) { "rceth.by: unexpected columns $header" }
        return trs.mapNotNull { tr ->
            val cells = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL).findAll(tr).map { clean(it.groupValues[1]) }.toList()
            // the last header column ("Дата приведения …") is often missing in a data row
            if (cells.size <= maxOf(trade, inn, forms, number) || cells[0].isBlank() || !cells[0].trim().all(Char::isDigit)) return@mapNotNull null
            val link = Regex("""href="(/Refbank/reestr_lekarstvennih_sredstv/details/[^"]+)"""").find(tr)?.groupValues?.get(1)
                ?.replace("&amp;", "&")?.let { BASE + it }.orEmpty()
            Row(cells[trade].replace("инструкция пациента", "").replace("инструкция специалиста", "").trim(), cells[inn], cells[forms],
                cells.getOrElse(holder) { "" }, cells[number], link)
        }
    }

    private fun formFields(page: String): List<Pair<String, String>> {
        val start = page.indexOf("<form action=\"/Refbank/reestr_lekarstvennih_sredstv/results\"")
        check(start >= 0) { "rceth.by: search form not found" }
        val form = page.substring(start, page.indexOf("</form>", start))
        val inputs = Regex("""<input([^>]*)>""").findAll(form).mapNotNull { m ->
            val a = m.groupValues[1]
            val name = Regex("""name="([^"]*)"""").find(a)?.groupValues?.get(1) ?: return@mapNotNull null
            if (Regex("""type="checkbox"""").containsMatchIn(a)) return@mapNotNull null
            name to (Regex("""value="([^"]*)"""").find(a)?.groupValues?.get(1)?.let(::unescape) ?: "")
        }
        val selects = Regex("""<select[^>]*name="([^"]*)"[^>]*>(.*?)</select>""", RegexOption.DOT_MATCHES_ALL).findAll(form).map { m ->
            m.groupValues[1] to (Regex("""<option[^>]*selected[^>]*value="([^"]*)"|<option[^>]*value="([^"]*)"[^>]*selected""").find(m.groupValues[2])
                ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } ?: "")
        }
        return (inputs + selects).toList()
    }

    private fun clean(html: String) = unescape(html.replace(Regex("""<[^>]+>"""), " ")).replace(Regex("""\s+"""), " ").trim()
    private fun unescape(s: String) = s.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#174;", "®").replace("&#171;", "«")
        .replace("&#187;", "»").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    private fun request(url: String) = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(60))
        .header("User-Agent", "EasyPaper catalog import (open-source medicine diary)")
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
}
