package org.easypaper.ui.medicine

import androidx.compose.runtime.Composable
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import org.easypaper.model.Ingredient
import org.easypaper.model.Intake
import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import org.easypaper.ui.format.dayMonth
import org.easypaper.ui.patient.timeOfDay
import org.easypaper.ui.patient.medications.countUnitKey
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlinx.datetime.toLocalDateTime

/**
 * Text for a registry card and an intake, shared by the patient's confirmation and the doctor's brief (task 71).
 * Substance names, numbers and units come straight from the registry fields; only the labels are translated.
 */

private val UNIT_PLURALS: Map<String, PluralStringResource> = mapOf(
    "tablet" to Res.plurals.u_tablet, "capsule" to Res.plurals.u_capsule, "sachet" to Res.plurals.u_sachet,
    "ml" to Res.plurals.u_ml, "g" to Res.plurals.u_g, "dose" to Res.plurals.u_dose,
    "suppository" to Res.plurals.u_suppository, "lozenge" to Res.plurals.u_lozenge,
)

/** "1 sachet", "2 таблетки", "0.5 comprimés"; a unit the app does not know is shown as written. */
@Composable
fun amountWithUnit(amount: String, unit: String): String {
    val plural = UNIT_PLURALS[countUnitKey(unit) ?: unit] ?: return "$amount $unit" // never split over two lines
    val whole = amount.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()
    return pluralStringResource(plural, whole ?: FRACTION_QUANTITY, amount)
}

/** "Per sachet", "Per 5 ml", or the registry's own words ("Per unit (pro compresso obducto)"). */
@Composable
fun perUnitLabel(card: MedicineProduct): String? {
    val perUnit = card.perUnit ?: return null
    Regex("""(\d+(?:[.,]\d+)?)\s*ml""").find(perUnit)?.let { return stringResource(Res.string.brief_per_volume, it.groupValues[1]) }
    return when (countUnitKey(card.doseUnit)) {
        "tablet" -> stringResource(Res.string.brief_per_tablet)
        "capsule" -> stringResource(Res.string.brief_per_capsule)
        "sachet" -> stringResource(Res.string.brief_per_sachet)
        "dose" -> stringResource(Res.string.brief_per_dose)
        "suppository" -> stringResource(Res.string.brief_per_suppository)
        "lozenge" -> stringResource(Res.string.brief_per_lozenge)
        else -> stringResource(Res.string.brief_per_raw, perUnit)
    }
}

/** "paracétamol 500 mg" — or the name with "amount not stated in the registry". */
@Composable
fun ingredientText(ingredient: Ingredient): String {
    val amount = listOfNotNull(ingredient.amount, ingredient.unit).joinToString(" ")
    val main = if (ingredient.amount != null) "${ingredient.name} $amount" else "${ingredient.name} (${stringResource(Res.string.rec_amount_missing)})"
    return ingredient.equivalent?.let { "$main, $it" } ?: main
}

/** "Per sachet: acide ascorbique 200 mg, paracétamol 500 mg, maléate de phéniramine 25 mg." */
@Composable
fun substancesLine(card: MedicineProduct): String? {
    if (card.ingredients.isEmpty()) return null
    val list = card.ingredients.map { ingredientText(it) }.joinToString(", ")
    val per = perUnitLabel(card).takeIf { card.ingredients.any { it.amount != null } }
    return (if (per != null) "$per: $list" else list) + "."
}

/** "today at 08:30", "yesterday at 21:00", "14 Nov at 08:30", in the reader's time zone. */
@Composable
fun whenText(at: Long, timeZone: TimeZone, today: LocalDate): String {
    val date = Instant.fromEpochMilliseconds(at).toLocalDateTime(timeZone).date
    val time = timeOfDay(at, timeZone)
    return when (date) {
        today -> stringResource(Res.string.brief_today_at, time)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.brief_yesterday_at, time)
        else -> stringResource(Res.string.brief_date_at, dayMonth(date), time)
    }
}

/** "Patient reports: 1 sachet, today at 08:30." */
@Composable
fun intakeLine(intake: Intake, timeZone: TimeZone, today: LocalDate): String =
    stringResource(Res.string.brief_patient_reports, amountWithUnit(intake.amount, intake.unit), whenText(intake.takenAt, timeZone, today))

/** The source named for the doctor, with the date of the registry data. */
@Composable
fun sourceLine(source: RegistrySource): String {
    val name = when (source.registry) {
        "fr-bdpm" -> stringResource(Res.string.brief_source_fr)
        "ch-swissmedic" -> stringResource(Res.string.brief_source_ch)
        "eu-ema-art57" -> stringResource(Res.string.brief_source_eu)
        else -> source.registryName
    }
    return if (source.updated.isNotBlank()) "$name, ${stringResource(Res.string.brief_data_of, source.updated)}" else name
}

/** Title of the card: "FERVEX ADULTES · granulés pour solution buvable". */
fun cardTitle(card: MedicineProduct): String = listOfNotNull(card.brand, card.form).joinToString(" · ")

private const val FRACTION_QUANTITY = 2 // 0.5 tablets: the plural form ("few" in Slavic languages)
