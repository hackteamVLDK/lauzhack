package org.easypaper.catalog

import kotlinx.serialization.Serializable
import org.easypaper.model.MedicineProduct
import org.easypaper.scan.parseScanCode

/** One registered pack of a product: its GTIN (13 digits, leading zeros kept, as a string) and its own label. */
@Serializable
data class CatalogPack(val gtin: String, val description: String)

/**
 * One version of a medicine as a registry defines it (French CIS, Swiss authorisation + dosage number, one EU
 * Article 57 row): the card shown to the patient, its packs, and extra names it is sold under (Refdata articles).
 */
@Serializable
data class CatalogProduct(
    val id: String, // "<registry>:<recordId>", unique across all sources
    val card: MedicineProduct,
    val packs: List<CatalogPack> = emptyList(),
    val otherNames: List<String> = emptyList(),
    /** Still marketed / authorised according to the registry; withdrawn products are kept for old boxes at home. */
    val active: Boolean = true,
)

/** What one source of the catalog holds, as written by the import command. */
@Serializable
data class SourceInfo(
    val registry: String,
    val name: String,
    val countries: List<String>,
    val url: String,
    /** Date of the registry's own file (ISO), as stated in or with the download. */
    val dataDate: String,
    /** When the import ran (ISO date-time, UTC). */
    val importedAt: String,
    val records: Int,
    /** What this source can and cannot tell, shown in the coverage list. */
    val note: String,
)

@Serializable
data class SourceSnapshot(val info: SourceInfo, val products: List<CatalogProduct>)

/** A source that could not be loaded or queried; the others keep working. */
@Serializable
data class SourceStatus(val registry: String, val ok: Boolean, val detail: String)

/** A market the catalog does not cover, and why (shown in the coverage list, never replaced by guesses). */
@Serializable
data class NotCovered(val country: String, val registry: String, val url: String, val reason: String)

/** What the server's catalog holds: the sources with their dates, their load status, and the markets not covered. */
@Serializable
data class Coverage(val sources: List<SourceInfo>, val status: List<SourceStatus>, val notCovered: List<NotCovered>)

/** What the device read on the photo: barcode contents (any format) and the printed text in reading order. */
@Serializable
data class CatalogQuery(
    val codes: List<String> = emptyList(),
    val text: String = "",
    /** Optional registry filter for API callers; patient recognition always uses null (every loaded registry). */
    val country: String? = null,
)

@Serializable
enum class ResolutionKind {
    /** One registry version fits the code and/or the label: show the card for confirmation. */
    EXACT,
    /** Several versions fit: show what differs and ask [Resolution.question]. */
    CANDIDATES,
    /** The barcode names one product, the printed name or strength another: ask for a new photo. */
    CONFLICT,
    /** Nothing usable on the photo: ask for the foil or the box, or typing the name. */
    TOO_LITTLE,
    /** A readable label had no matching registry entry; online reading can retry the package photo. */
    NOT_FOUND,
    /** No registry products have been loaded on the server yet. */
    NOT_LOADED,
}

@Serializable
enum class ResolutionQuestion {
    WHICH_STRENGTH, // "500 mg or 1000 mg?" — and a photo of the strength helps
    WHICH_VARIANT, // Forte / Junior / Sans sucre …
    WHICH_FORM, // tablets or sachets …
    WHICH_PRODUCT, // different products of one brand
    NEW_PHOTO, // conflict between code and label
    PHOTO_PACKAGE, // too little: foil or box, or type the name
}

/** A version to show, with what sets it apart from the other candidates ("1000 mg", "comprimé effervescent"). */
@Serializable
data class Candidate(val product: CatalogProduct, val differences: List<String> = emptyList(), val pack: CatalogPack? = null)

@Serializable
data class Resolution(
    val kind: ResolutionKind,
    val candidates: List<Candidate> = emptyList(),
    val question: ResolutionQuestion? = null,
    /** The GTIN read from the barcode or DataMatrix (01), whether or not a registry knows it. */
    val gtin: String? = null,
    val gtinKnown: Boolean = false,
    /** The product the barcode points to when the label disagrees ([ResolutionKind.CONFLICT]). */
    val codeProduct: CatalogProduct? = null,
    /** The likely name read on the label, for typing it by hand when nothing matched. */
    val labelName: String? = null,
    val sources: List<SourceStatus> = emptyList(),
)

/**
 * In-memory index over the products of one or more sources: exact pack lookup by GTIN and text search by name.
 * Pure Kotlin, so the same matching runs on the server and in tests.
 */
class CatalogIndex(val products: List<CatalogProduct>) {
    private val byGtin: Map<String, List<CatalogProduct>> = buildMap<String, MutableList<CatalogProduct>> {
        for (p in products) for (pack in p.packs) {
            val gtin = parseScanCode(pack.gtin)?.gtin ?: pack.gtin
            getOrPut(gtin) { mutableListOf() }.add(p)
        }
    }
    private val names: Map<String, List<List<String>>> = products.associate { p ->
        p.id to (listOf(p.card.name, p.card.brand) + p.otherNames).map { n -> comparisonTokens(n) }
    }
    private val byFirstWord: Map<String, List<CatalogProduct>> = buildMap<String, MutableSet<CatalogProduct>> {
        for (p in products) for (tokens in names.getValue(p.id)) {
            // the brand word: the first real word, not a variant ("Junior …") or a form word
            tokens.firstOrNull { it.any(Char::isLetter) && it.length >= 2 && it !in COMPARISON_VARIANTS && it !in COMPARISON_FILLERS }
                ?.let { getOrPut(it) { mutableSetOf() }.add(p) }
        }
    }.mapValues { it.value.toList() }

    private val rootsByProduct = products.associate { p ->
        p.id to p.card.ingredients.flatMap { ingredientRoots(it.name) }.toSet()
    }
    private val byIngredient: Map<String, List<CatalogProduct>> = buildMap<String, MutableSet<CatalogProduct>> {
        for (p in products) for (root in rootsByProduct.getValue(p.id)) getOrPut(root) { mutableSetOf() }.add(p)
    }.mapValues { it.value.toList() }

    fun findByGtin(gtin: String): List<CatalogProduct> = byGtin[parseScanCode(gtin)?.gtin ?: gtin].orEmpty().distinctBy { it.id }

    /** Whole brand, then partial brand with strength, then registry INN with strength; all markets by default. */
    fun search(text: String, country: String?): List<Scored> {
        val label = parseLabel(text) ?: return emptyList()
        val seen = comparisonTokens(text)
        val seenSet = seen.toSet()
        val roots = seen.map(::innRoot).toSet()
        val labelVariants = variantsIn(text)
        val labelStrengths = label.strengths
        val brandProducts = seenSet.flatMap { byFirstWord[it].orEmpty() }.toSet()
        val substanceProducts = if (labelStrengths.isEmpty()) emptyList() else roots.flatMap { byIngredient[it].orEmpty() }
        return (brandProducts + substanceProducts).distinctBy { it.id }
            .filter { country == null || marketFits(it.card.source.country, country) }
            .mapNotNull { p ->
                val productVariants = (listOf(p.card.name, p.card.brand) + p.otherNames).flatMap { variantsIn(it) }.toSet()
                // a variant printed on the box must be part of the product (FORTE on the box is never plain DOLO)
                if (!productVariants.containsAll(labelVariants)) return@mapNotNull null
                val best = names.getValue(p.id).maxOf { tokens ->
                    val words = tokens.filter { it !in COMPARISON_FILLERS }
                    words.count { it in seenSet } * 10 - words.count { it !in seenSet }
                }
                val strengths = p.strengths()
                val strengthOk = labelStrengths.isEmpty() || labelStrengths.any { it in strengths }
                val brandMatches = p in brandProducts
                val wholeBrand = (listOf(p.card.brand) + p.otherNames).any { name ->
                    val words = comparisonTokens(name).filter { it.any(Char::isLetter) && it !in COMPARISON_FILLERS }
                    words.isNotEmpty() && words.all { it in seenSet }
                }
                val innMatches = roots.any { it in rootsByProduct.getValue(p.id) }
                if (!brandMatches && (!innMatches || !strengthOk)) return@mapNotNull null
                val rank = when {
                    brandMatches && wholeBrand -> 300
                    brandMatches && labelStrengths.isNotEmpty() && strengthOk -> 200
                    brandMatches -> 150
                    else -> 100
                }
                val capitalisedBrand = names.getValue(p.id).any { tokens -> tokens.firstOrNull() in label.brandTokens.map(::comparisonWord) }
                Scored(p, rank + best.coerceIn(-40, 40) + if (capitalisedBrand) 5 else 0,
                    strengthOk, labelStrengths.isNotEmpty(), brandMatches)
            }
            .sortedWith(compareByDescending<Scored> { it.strengthMatches }.thenByDescending { it.score }.thenBy { it.product.id })
    }

    data class Scored(val product: CatalogProduct, val score: Int, val strengthMatches: Boolean, val labelHasStrength: Boolean,
                      val brandMatches: Boolean = true)
}

/** EU/EEA countries: a centrally authorised product (EMA country "EU") is sold in all of them. */
val EU_COUNTRIES: Set<String> = setOf("AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IE", "IT", "LV",
    "LI", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO", "SK", "SI", "ES", "SE")

/** Whether a product registered for [productCountry] is on sale in [purchase]. */
fun marketFits(productCountry: String, purchase: String): Boolean =
    productCountry == purchase || (productCountry == "EU" && purchase in EU_COUNTRIES)

/** The strengths of a product's active substances, normalised for comparison with a label. */
fun CatalogProduct.strengths(): Set<Strength> = card.ingredients.mapNotNull { strengthOf(it.amount, it.unit) }.toSet() +
    strengthsIn(card.name)

/**
 * Turns what the camera read into a decision (task 71). The code wins when a registry knows it, unless the label
 * clearly names something else; a label alone is enough only when exactly one registry version fits; when the
 * strength is not readable, the strength is never chosen for the person.
 */
fun resolve(index: CatalogIndex, query: CatalogQuery, sources: List<SourceStatus> = emptyList()): Resolution {
    val labelName = query.text.lines().map { it.trim() }.firstOrNull { line -> line.count { it.isLetter() } >= 3 }?.take(80)
    if (index.products.isEmpty()) return Resolution(ResolutionKind.NOT_LOADED, labelName = labelName, sources = sources)
    val codes = query.codes.mapNotNull(::parseScanCode)
    val byText = if (query.text.isBlank()) emptyList() else index.search(query.text, query.country)
        .ifEmpty { if (query.country != null) index.search(query.text, null) else emptyList() }

    for (code in codes) {
        val found = index.findByGtin(code.gtin)
        val inMarket = found.filter { query.country == null || marketFits(it.card.source.country, query.country) }.ifEmpty { found }
        if (inMarket.isEmpty()) continue
        val compatible = inMarket.filterNot { labelContradicts(it, query.text, byText) }
        val product = compatible.firstOrNull() ?: inMarket.first()
        val pack = product.packs.first { (parseScanCode(it.gtin)?.gtin ?: it.gtin) == code.gtin }
        if (compatible.isEmpty()) {
            val other = byText.firstOrNull { it.product.id != product.id && it.score > 0 }?.product
            return Resolution(
                ResolutionKind.CONFLICT,
                candidates = listOfNotNull(Candidate(product, pack = pack), other?.let { Candidate(it) }),
                question = ResolutionQuestion.NEW_PHOTO, gtin = code.gtin, gtinKnown = true, codeProduct = product,
                labelName = labelName, sources = sources,
            )
        }
        if (compatible.size > 1) {
            val result = candidates(compatible, null, code.gtin, labelName, sources)
            return result.copy(gtinKnown = true, candidates = result.candidates.map { candidate ->
                candidate.copy(pack = candidate.product.packs.first { (parseScanCode(it.gtin)?.gtin ?: it.gtin) == code.gtin })
            })
        }
        return Resolution(ResolutionKind.EXACT, listOf(Candidate(product, pack = pack)), gtin = code.gtin, gtinKnown = true,
            labelName = labelName, sources = sources)
    }
    val gtin = codes.firstOrNull()?.gtin

    val strengthMatches = byText.filter { it.score > 0 && it.strengthMatches }
    // A readable brand takes precedence over other medicines sharing its active substance: when the brand is on the
    // label, a medicine found only by its substance is never offered instead (DAFALGAN 280 mg is not FERVEX ENFANTS).
    val brandSeen = byText.any { it.score > 0 && it.brandMatches }
    val fitting = if (brandSeen) strengthMatches.filter { it.brandMatches } else strengthMatches
    if (fitting.isEmpty()) {
        // the brand is there but the printed strength fits none of its versions: show them, never pick one
        val brandOnly = byText.filter { it.score > 0 && (!brandSeen || it.brandMatches) }
        if (brandOnly.isNotEmpty()) return candidates(brandOnly.map { it.product }, ResolutionQuestion.WHICH_STRENGTH, gtin, labelName, sources)
        return Resolution(ResolutionKind.TOO_LITTLE, question = ResolutionQuestion.PHOTO_PACKAGE, gtin = gtin, gtinKnown = false,
            labelName = labelName, sources = sources)
    }
    val top = fitting.maxOf { it.score }
    // the best-ranked versions only, whichever registries they come from (a whole brand outranks a partial one)
    val best = fitting.filter { it.score == top }.map { it.product }
    val labelHasStrength = fitting.first().labelHasStrength
    // without a readable strength, every version of the brand stays a candidate if their strengths differ
    val sameBrand = fitting.map { it.product }
    if (!labelHasStrength && sameBrand.map { it.strengthSignature() }.distinct().size > 1) {
        return candidates(sameBrand, ResolutionQuestion.WHICH_STRENGTH, gtin, labelName, sources)
    }
    if (best.size == 1) return Resolution(ResolutionKind.EXACT, listOf(Candidate(best.single())), gtin = gtin, gtinKnown = false,
        labelName = labelName, sources = sources)
    return candidates(best, null, gtin, labelName, sources)
}

private fun labelContradicts(product: CatalogProduct, text: String, byText: List<CatalogIndex.Scored>): Boolean {
    if (text.isBlank()) return false
    val labelStrengths = strengthsIn(text)
    if (labelStrengths.isNotEmpty() && labelStrengths.none { it in product.strengths() }) {
        // a strength on the label that this product does not have: only a conflict if it is a medicine strength
        if (labelStrengths.any { it.unit == "mg" }) return true
    }
    val ownBrand = byText.any { it.brandMatches && (it.product.id == product.id || it.product.card.brand.equals(product.card.brand, ignoreCase = true)) }
    val otherBrand = byText.firstOrNull { it.brandMatches }?.takeIf { it.product.card.brand != product.card.brand }
    return !ownBrand && otherBrand != null
}

private fun CatalogProduct.strengthSignature(): String =
    card.ingredients.joinToString("+") { "${it.amount}${it.unit}" }.ifEmpty { strengthsIn(card.name).joinToString("+") }

/** Several versions: up to 5, each with its registry country and the attributes that differ between them. */
private fun candidates(products: List<CatalogProduct>, question: ResolutionQuestion?, gtin: String?, labelName: String?,
                       sources: List<SourceStatus>): Resolution {
    val shown = products.distinctBy { it.id }.take(MAX_CANDIDATES)
    val strengthDiffers = shown.map { it.strengthSignature() }.distinct().size > 1
    val formDiffers = shown.map { it.card.form }.distinct().size > 1
    val variantDiffers = shown.map { variantsIn(it.card.name) }.distinct().size > 1
    val asked = question ?: when {
        strengthDiffers -> ResolutionQuestion.WHICH_STRENGTH
        variantDiffers -> ResolutionQuestion.WHICH_VARIANT
        formDiffers -> ResolutionQuestion.WHICH_FORM
        else -> ResolutionQuestion.WHICH_PRODUCT
    }
    val list = shown.map { p ->
        Candidate(p, listOfNotNull(
            p.card.source.country,
            p.strengthLabel().takeIf { strengthDiffers && it.isNotEmpty() },
            variantsIn(p.card.name).joinToString(" ").lowercase().takeIf { variantDiffers && it.isNotEmpty() },
            p.card.form.takeIf { formDiffers },
        ))
    }
    return Resolution(ResolutionKind.CANDIDATES, list, asked, gtin = gtin, gtinKnown = false, labelName = labelName, sources = sources)
}

/** "500 mg" or "500 mg + 200 mg + 25 mg", as the registry writes the amounts. */
fun CatalogProduct.strengthLabel(): String =
    card.ingredients.filter { it.amount != null }.joinToString(" + ") { "${it.amount} ${it.unit.orEmpty()}".trim() }
        .ifEmpty { strengthsIn(card.name).joinToString(" + ") }

private const val MAX_CANDIDATES = 5
