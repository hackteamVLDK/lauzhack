package org.easypaper.catalog

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

/**
 * The catalog import command (task 71): downloads the official files and writes one snapshot per source.
 *
 *   ./gradlew :server:importCatalog --args="fr ch eu"            download and import
 *   ./gradlew :server:importCatalog --args="fr --from C:/dumps"   import files already downloaded
 *
 * Every run rebuilds a source from its files and replaces its snapshot, keyed by the registry's record ids, so it
 * can be repeated any time without creating duplicates. One source failing does not stop the others.
 */
fun main(args: Array<String>) {
    val from = args.indexOf("--from").takeIf { it >= 0 }?.let { File(args[it + 1]) }
    val wanted = args.filterIndexed { i, a -> !a.startsWith("--") && (i == 0 || args[i - 1] != "--from") }.ifEmpty { listOf("fr", "ch", "eu") }
    val store = CatalogStore.fromEnv()
    val downloads = from ?: File(store.dir, "downloads")
    var failed = 0
    for (source in wanted) {
        val started = System.currentTimeMillis()
        try {
            val snapshot = when (source) {
                "fr" -> importFrance(File(downloads, "fr"), download = from == null)
                "ch" -> importSwitzerland(File(downloads, "ch"), download = from == null)
                "eu" -> importEma(File(downloads, "eu"), download = from == null)
                else -> error("Unknown source '$source' (fr, ch, eu)")
            }
            store.write(snapshot)
            println("$source: ${snapshot.info.records} products from ${snapshot.info.dataDate} -> ${store.file(snapshot.info.registry).path} (${(System.currentTimeMillis() - started) / 1000} s)")
        } catch (e: Exception) {
            failed++
            System.err.println("$source: import failed, previous snapshot kept: ${e.message}")
        }
    }
    if (failed > 0) exitProcess(1)
}

fun importFrance(dir: File, download: Boolean): SourceSnapshot {
    var date = ""
    if (download) for (name in France.FILES) date = fetch("${France.BASE}/download/file/$name", File(dir, name)) ?: date
    if (date.isEmpty()) date = fileDate(File(dir, France.FILES.first()))
    val products = France.read(dir, date)
    return SourceSnapshot(info(France.REGISTRY, France.NAME, listOf("FR"), "${France.BASE}/telechargement", date, products.size,
        "Products (CIS), substances with dosage per unit, CIP13 packs. Indication from the official product page."), products)
}

fun importSwitzerland(dir: File, download: Boolean): SourceSnapshot {
    val xlsx = File(dir, "zugelassene_packungen_ham.xlsx")
    val sai = File(dir, "SAI-Packungen.XML")
    if (download) {
        fetch(Switzerland.PACKS_URL, xlsx)
        // the newest monthly export is the first link on the SAI download page
        val page = String(get(Switzerland.SAI_DOWNLOAD_PAGE))
        val id = Regex("""data-url="(/en/download/structuredexportzip/\d+)"""").find(page)?.groupValues?.get(1)
            ?: error("No SAI export link on ${Switzerland.SAI_DOWNLOAD_PAGE}")
        ZipInputStream(get("https://sai.refdata.ch$id").inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name.endsWith("SAI-Packungen.XML") }
                ?: error("SAI export without SAI-Packungen.XML")
            dir.mkdirs()
            sai.outputStream().use { zip.copyTo(it) }
        }
    }
    val (products, stand) = Switzerland.read(xlsx, sai.takeIf { it.isFile })
    return SourceSnapshot(info(Switzerland.REGISTRY, Switzerland.NAME, listOf("CH"), "https://www.swissmedic.ch/swissmedic/de/home/services/listen_neu.html",
        stand.ifEmpty { fileDate(xlsx) }, products.size,
        "Authorised human medicine packs with composition per unit and indication field (Swissmedic); box names and GTINs in trade (Refdata SAI); professional information on swissmedicinfo."),
        products)
}

fun importEma(dir: File, download: Boolean): SourceSnapshot {
    val xlsx = File(dir, "article-57-product-data_en.xlsx")
    if (download) fetch(Ema.URL, xlsx)
    val (products, date) = Ema.read(xlsx)
    return SourceSnapshot(info(Ema.REGISTRY, Ema.NAME, products.map { it.card.source.country }.distinct().sorted(), Ema.PAGE,
        date.ifEmpty { fileDate(xlsx) }, products.size,
        "Names, active substances and country of authorisation only: no strength per unit, so cards stay unverified."), products)
}

private fun info(registry: String, name: String, countries: List<String>, url: String, date: String, records: Int, note: String) =
    SourceInfo(registry, name, countries, url, date, DateTimeFormatter.ISO_INSTANT.format(Instant.now()), records, note)

private val http: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()

private fun request(url: String) = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(5))
    .header("User-Agent", "EasyPaper catalog import (open-source medicine diary)").GET().build()

private fun get(url: String): ByteArray {
    val response = http.send(request(url), HttpResponse.BodyHandlers.ofByteArray())
    check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
    return response.body()
}

/** Downloads [url] to [target]; returns the server's Last-Modified date (ISO) when it sends one. */
private fun fetch(url: String, target: File): String? {
    target.parentFile.mkdirs()
    val tmp = File(target.path + ".part")
    val response = http.send(request(url), HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
    check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
    if (!tmp.renameTo(target)) { target.delete(); check(tmp.renameTo(target)) }
    return response.headers().firstValue("Last-Modified").map {
        ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate().toString()
    }.orElse(null)
}

private fun fileDate(file: File): String =
    LocalDate.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneOffset.UTC).toString()
