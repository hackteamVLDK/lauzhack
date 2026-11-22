package org.easypaper.catalog

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.easypaper.model.EasyPaperJson
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Registries asked for but not integrated, with the reason checked on 2026-11-22. */
val NOT_COVERED = listOf(
    NotCovered("UA", "Державний реєстр лікарських засобів України", "https://www.drlz.gov.ua/",
        "Not reachable from the import host (connection timed out for drlz.gov.ua, drlz.com.ua and data.gov.ua); no import yet."),
    NotCovered("RU", "ГРЛС", "https://grls.rosminzdrav.ru/",
        "Site answered once and then timed out from the import host; its Excel export could not be downloaded or checked."),
    NotCovered("BY", "Государственный реестр лекарственных средств", "https://www.rceth.by/Refbank/",
        "Only an HTML search form, no documented download or API; not imported."),
)

/**
 * The catalog the server answers with: every snapshot of [store] loaded on its own (a broken source is reported
 * in [Resolution.sources] and the others keep answering), one index over all of them.
 */
class CatalogService(private val store: CatalogStore, private val indications: Indications? = Indications(store.dir)) {
    private val loaded = store.loadAll()
    private val snapshots = loaded.mapNotNull { it.getOrNull() }
    val status: List<SourceStatus> = snapshots.map { SourceStatus(it.info.registry, true, "${it.info.records} products, data of ${it.info.dataDate}") } +
        loaded.mapNotNull { r -> r.exceptionOrNull()?.let { SourceStatus("unreadable", false, it.message ?: "cannot load") } }
    val index = CatalogIndex(snapshots.flatMap { it.products })

    fun coverage() = Coverage(snapshots.map { it.info }, status, NOT_COVERED)

    fun resolve(query: CatalogQuery): Resolution {
        val result = resolve(index, query, status)
        // the official indication of the French products shown (fetched once, then cached)
        val withIndication = result.candidates.take(MAX_INDICATIONS).map { c ->
            val card = c.product.card
            if (card.indication != null || card.source.registry != France.REGISTRY || indications == null) c
            else indications.french(card.source.recordId)?.let { c.copy(product = c.product.copy(card = card.copy(indication = it, indicationLanguage = "fr"))) } ?: c
        }
        return result.copy(candidates = withIndication + result.candidates.drop(MAX_INDICATIONS))
    }

    private companion object {
        const val MAX_INDICATIONS = 3
    }
}

/**
 * Short official indications for French products, taken from the leaflet section 1 or the SmPC section 4.1 on the
 * product's BDPM page and cached in "indications-fr.json". The sentence is quoted, never rewritten or made up.
 */
class Indications(dir: File, private val fetchPage: (String) -> String? = ::download) {
    private val file = File(dir, "indications-fr.json")
    private val cache: MutableMap<String, String> = runCatching {
        EasyPaperJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), file.readText())
    }.getOrDefault(emptyMap()).toMutableMap()

    @Synchronized
    fun french(cis: String): String? {
        cache[cis]?.let { return it.ifEmpty { null } }
        val html = fetchPage(France.productUrl(cis)) ?: return null // network failure: try again next time
        val sentence = frenchIndication(html).orEmpty()
        cache[cis] = sentence
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(EasyPaperJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), cache))
        }
        return sentence.ifEmpty { null }
    }

    companion object {
        private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(6)).build()

        fun download(url: String): String? = runCatching {
            val response = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "EasyPaper catalog (open-source medicine diary)").GET().build(), HttpResponse.BodyHandlers.ofString())
            response.body().takeIf { response.statusCode() == 200 }
        }.getOrNull()
    }
}

/**
 * The first sentence that says what the medicine is indicated for: the leaflet's "Ce médicament est indiqué …"
 * (section 1) or, failing that, the first paragraph of SmPC 4.1. Cut at the colon that starts a symptom list.
 */
fun frenchIndication(html: String): String? {
    val lines = html.replace(Regex("""<(script|style)[^>]*>.*?</\1>""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""<[^>]+>"""), "\n").let(::decodeEntities)
        .lines().map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
    // only inside leaflet section 1, right after its heading: the page also carries generic health-education texts
    // ("Conseil d'éducation sanitaire") whose sentences are not about this product
    val section1 = lines.indexOfLast { it.startsWith("1. QU") && it.contains("DANS QUELS CAS", ignoreCase = true) }
    val leaflet = section1.takeIf { it >= 0 }?.let { start ->
        lines.subList(start + 1, minOf(lines.size, start + LEAFLET_WINDOW))
            .firstOrNull { (" indiqué" in it || " utilisé" in it) && !it.startsWith("Classe") && !it.startsWith("1. QU") }
    }
    val smpc = lines.indexOfLast { it.startsWith("4.1. Indications thérapeutiques") }.takeIf { it >= 0 }
        ?.let { i -> lines.drop(i + 1).firstOrNull { l -> l.length > 30 && !l.startsWith("4.") && !l.startsWith("Redirection") } }
    val text = leaflet ?: smpc ?: return null
    return shorten(text)
}

private const val LEAFLET_WINDOW = 12

private fun shorten(text: String): String {
    var t = text.substringBefore(" :").substringBefore(":").trim().trimEnd(',', ';')
    if (t.length > 240) t = t.take(240).substringBeforeLast(',').trimEnd() + " …"
    return if (t.endsWith(".") || t.endsWith("…")) t else "$t."
}

private fun decodeEntities(s: String) = s.replace("&#039;", "'").replace("&#39;", "'").replace("&nbsp;", " ")
    .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
