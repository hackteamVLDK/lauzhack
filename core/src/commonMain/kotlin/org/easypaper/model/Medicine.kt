package org.easypaper.model

import kotlinx.serialization.Serializable

/**
 * Where a confirmed medicine card came from: one record of an official registry. [recordId] is the registry's own
 * key (French CIS code, Swiss authorisation + dosage number, …), [url] the official page of exactly this product,
 * [updated] the date of the registry file the card was built from (ISO date).
 */
@Serializable
data class RegistrySource(
    val registry: String, // "fr-bdpm", "ch-swissmedic", "eu-ema-art57"
    val registryName: String, // "Base de données publique des médicaments", …
    val country: String, // ISO 3166-1 alpha-2 of the market: "FR", "CH", "DE", …
    val recordId: String,
    val url: String,
    val updated: String,
)

/**
 * One active substance with its amount per unit as the registry states it. [name] is the registry's name
 * (French DCI for France, Latin for Switzerland); [amount] and [unit] are copied, never computed.
 */
@Serializable
data class Ingredient(
    val name: String,
    val amount: String? = null, // "500", "2,5" — as written by the registry
    val unit: String? = null, // "mg", "µg", "UI"
    /** Registry note on the active moiety, e.g. "équivalant à phéniramine 15,8 mg". */
    val equivalent: String? = null,
)

/**
 * The confirmed medicine as it was on the day the patient confirmed it: a snapshot, so a later catalog update never
 * changes what the doctor reads in an old entry. Composition comes only from the registry record of this version.
 */
@Serializable
data class MedicineProduct(
    val name: String, // official name with variant: "FERVEX ADULTES, granulés pour solution buvable en sachet"
    val brand: String, // short title for the card: "FERVEX ADULTES"
    val form: String? = null, // "granulés pour solution buvable"
    val ingredients: List<Ingredient> = emptyList(),
    /** What one unit of the ingredient amounts is: "un sachet", "pro compresso", "5 ml". */
    val perUnit: String? = null,
    /** The unit the patient counts when taking it: "sachet", "tablet", "ml"; null when the registry does not say. */
    val doseUnit: String? = null,
    /** The pack found by its code or chosen by the patient (optional for recording an intake). */
    val pack: String? = null,
    val gtin: String? = null,
    /** Short official text on what the medicine is used for, quoted from the registry/leaflet, in [indicationLanguage]. */
    val indication: String? = null,
    val indicationLanguage: String? = null,
    val source: RegistrySource,
    /** False when the registry lists the substances but not their amounts (EU Article 57 list). */
    val strengthVerified: Boolean = true,
)

/** What the patient says they actually took: never derived from the pack size. */
@Serializable
data class Intake(
    val amount: String, // "1", "0.5", "10" — as the patient entered it
    val unit: String, // "sachet", "tablet", "ml", or the patient's word
    val takenAt: Long, // epoch millis UTC
)
