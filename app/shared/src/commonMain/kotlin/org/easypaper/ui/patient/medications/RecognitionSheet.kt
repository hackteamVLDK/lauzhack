package org.easypaper.ui.patient.medications

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import org.easypaper.catalog.ResolutionKind
import org.easypaper.catalog.ResolutionQuestion
import org.easypaper.model.Intake
import org.easypaper.model.Medication
import org.easypaper.model.MedicineProduct
import org.easypaper.ui.components.ChoiceList
import org.easypaper.ui.components.GrayButton
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.SegmentedControl
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.components.TextFieldRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.medicine.amountWithUnit
import org.easypaper.ui.medicine.cardTitle
import org.easypaper.ui.medicine.ingredientText
import org.easypaper.ui.medicine.perUnitLabel
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Countries offered for "Bought in"; null = any market. Markets without a connected registry say so in their name. */
val PURCHASE_COUNTRIES: List<Pair<String?, StringResource>> = listOf(
    null to Res.string.rec_country_any, "CH" to Res.string.country_CH, "FR" to Res.string.country_FR, "DE" to Res.string.country_DE,
    "AT" to Res.string.country_AT, "IT" to Res.string.country_IT, "BE" to Res.string.country_BE, "ES" to Res.string.country_ES,
    "NL" to Res.string.country_NL, "PL" to Res.string.country_PL, "UA" to Res.string.country_UA, "RU" to Res.string.country_RU,
    "BY" to Res.string.country_BY,
)

/**
 * The content of the recognition sheet for the current [Recognition.step] (task 71). [onSave] records the entry;
 * it is called with the same entry id however often the person taps.
 */
@Composable
fun ColumnScope.RecognitionContent(
    recognition: Recognition,
    takePhoto: (() -> Unit)?,
    pickPhoto: (() -> Unit)?,
    saving: Boolean,
    timeZone: TimeZone,
    onCountry: (String?) -> Unit,
    onSave: (entryId: String, medication: Medication, intake: Intake?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val photoButtons: @Composable () -> Unit = {
        if (takePhoto != null) TintedButton(stringResource(Res.string.rec_take_photo), onClick = takePhoto, modifier = Modifier.fillMaxWidth())
        if (pickPhoto != null) GrayButton(stringResource(Res.string.rec_choose_image), onClick = pickPhoto, modifier = Modifier.fillMaxWidth())
        PlainButton(stringResource(Res.string.rec_type_label), onClick = recognition::typeLabel, modifier = Modifier.fillMaxWidth())
    }
    when (val step = recognition.step) {
        RecognitionStep.Start -> {
            Note(stringResource(Res.string.rec_intro))
            CountryPicker(recognition.country, onCountry)
            Spacer(Modifier.height(EasyPaperTheme.spacing.l))
            photoButtons()
            PlainButton(stringResource(Res.string.rec_words), onClick = { recognition.words(null) }, modifier = Modifier.fillMaxWidth())
            Note(stringResource(Res.string.rec_privacy))
        }
        RecognitionStep.Reading -> Note(stringResource(Res.string.med_reading), live = true)
        RecognitionStep.Searching -> Note(stringResource(Res.string.rec_searching), live = true)
        RecognitionStep.TypeLabel -> {
            var text by remember { mutableStateOf("") }
            GroupedSection { TextAreaRow(text, { text = it }, placeholder = stringResource(Res.string.rec_label_hint)) }
            Spacer(Modifier.height(EasyPaperTheme.spacing.l))
            ProminentButton(stringResource(Res.string.rec_search), onClick = { scope.launch { recognition.onLabelText(text) } },
                enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
            Note(stringResource(Res.string.rec_privacy))
        }
        is RecognitionStep.Card -> CardStep(step, saving, recognition)
        is RecognitionStep.Result -> ResultStep(step, recognition, photoButtons)
        is RecognitionStep.Words -> WordsStep(step, recognition)
        is RecognitionStep.Intake -> IntakeStep(step, saving, timeZone, onSave)
        is RecognitionStep.Unavailable -> {
            Note(stringResource(Res.string.rec_unavailable), live = true)
            ProminentButton(stringResource(Res.string.rec_words), onClick = { recognition.words(step.readName) }, modifier = Modifier.fillMaxWidth())
        }
        null -> Unit
    }
}

@Composable
private fun CountryPicker(country: String?, onCountry: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val names = PURCHASE_COUNTRIES.map { (code, name) -> code to stringResource(name) }
    val current = names.first { it.first == country }.second
    GroupedSection(footer = stringResource(Res.string.rec_country_note)) {
        ListRow(stringResource(Res.string.rec_country), subtitle = current, onClick = { open = !open })
    }
    if (open) ChoiceList(names.map { it.second }, current, onSelect = { picked ->
        onCountry(names.first { it.second == picked }.first)
        open = false
    })
}

/** The registry card to confirm: exact name and version, form, every substance per unit, pack, source. */
@Composable
private fun ColumnScope.CardStep(step: RecognitionStep.Card, saving: Boolean, recognition: Recognition) {
    val card = step.candidate.product.card
    if (step.gtin != null && step.candidate.pack == null) Note(stringResource(Res.string.rec_unknown_code, step.gtin))
    MedicineCard(card, step.candidate.pack?.description)
    Spacer(Modifier.height(EasyPaperTheme.spacing.xl))
    ProminentButton(stringResource(Res.string.rec_confirm), onClick = { recognition.confirm(step) }, enabled = !saving, modifier = Modifier.fillMaxWidth())
    PlainButton(stringResource(Res.string.rec_not_this), onClick = { recognition.words(card.brand) }, modifier = Modifier.fillMaxWidth())
}

/** The card as the patient checks it; the doctor sees the same fields in the brief. */
@Composable
fun MedicineCard(card: MedicineProduct, pack: String?) {
    val spacing = EasyPaperTheme.spacing
    val uri = LocalUriHandler.current
    Text(card.name, style = EasyPaperTheme.typography.title3, color = EasyPaperTheme.colors.label,
        modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.s).semantics { heading() })
    GroupedSection {
        card.form?.let { ListRow(stringResource(Res.string.rec_form), subtitle = it, chevron = false); RowSeparator() }
        ListRow(stringResource(Res.string.rec_substances), subtitle = perUnitLabel(card), chevron = false)
        for (ingredient in card.ingredients) {
            RowSeparator()
            ListRow(ingredientText(ingredient), chevron = false)
        }
        pack?.let { RowSeparator(); ListRow(stringResource(Res.string.rec_pack), subtitle = it, chevron = false) }
    }
    if (!card.strengthVerified) Note(stringResource(Res.string.rec_unverified))
    Note(stringResource(Res.string.rec_source, card.source.registryName, card.source.updated))
    PlainButton(stringResource(Res.string.rec_open_source), onClick = { runCatching { uri.openUri(card.source.url) } }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.ResultStep(step: RecognitionStep.Result, recognition: Recognition, photoButtons: @Composable () -> Unit) {
    val r = step.resolution
    val gtin = r.gtin
    if (gtin != null && !r.gtinKnown && r.kind != ResolutionKind.TOO_LITTLE) Note(stringResource(Res.string.rec_unknown_code, gtin))
    when (r.kind) {
        ResolutionKind.CANDIDATES -> {
            Note(stringResource(questionText(r.question)), live = true)
            GroupedSection {
                r.candidates.forEachIndexed { i, candidate ->
                    if (i > 0) RowSeparator()
                    ListRow(candidate.product.card.name, subtitle = candidate.differences.joinToString(" · "),
                        onClick = { recognition.choose(candidate, r.gtin) })
                }
            }
        }
        ResolutionKind.CONFLICT -> Note(stringResource(Res.string.rec_conflict, r.codeProduct?.card?.name.orEmpty()), live = true)
        ResolutionKind.TOO_LITTLE -> Note(stringResource(Res.string.rec_too_little), live = true)
        ResolutionKind.EXACT -> Unit // shown as the card step directly
    }
    Spacer(Modifier.height(EasyPaperTheme.spacing.l))
    photoButtons()
    PlainButton(stringResource(Res.string.rec_words), onClick = { recognition.words() }, modifier = Modifier.fillMaxWidth())
}

private fun questionText(q: ResolutionQuestion?): StringResource = when (q) {
    ResolutionQuestion.WHICH_STRENGTH -> Res.string.rec_q_strength
    ResolutionQuestion.WHICH_VARIANT -> Res.string.rec_q_variant
    ResolutionQuestion.WHICH_FORM -> Res.string.rec_q_form
    else -> Res.string.rec_q_product
}

@Composable
private fun ColumnScope.WordsStep(step: RecognitionStep.Words, recognition: Recognition) {
    var words by remember(step) { mutableStateOf(step.readName.orEmpty()) }
    var ingredients by remember(step) { mutableStateOf("") }
    GroupedSection { TextAreaRow(words, { words = it }, placeholder = stringResource(Res.string.rec_words_hint)) }
    Spacer(Modifier.height(EasyPaperTheme.spacing.s))
    GroupedSection { TextAreaRow(ingredients, { ingredients = it }, placeholder = stringResource(Res.string.rec_label_ingredients)) }
    Note(stringResource(Res.string.rec_words_footer))
    ProminentButton(stringResource(Res.string.rec_next), onClick = { recognition.confirmWords(step, words, ingredients) },
        enabled = words.isNotBlank(), modifier = Modifier.fillMaxWidth())
}

/**
 * How much was actually taken and when, asked separately: a photographed package is not an intake, and the pack
 * size never becomes the amount.
 */
@Composable
private fun ColumnScope.IntakeStep(step: RecognitionStep.Intake, saving: Boolean, timeZone: TimeZone,
                                   onSave: (String, Medication, Intake?) -> Unit) {
    val medication = step.medication
    val fixedUnit = countUnitKey(medication.product?.doseUnit)
    var amount by remember(step) { mutableStateOf("1") }
    var unit by remember(step) { mutableStateOf("") }
    var whenChoice by remember(step) { mutableIntStateOf(0) } // now, today, yesterday
    var time by remember(step) { mutableStateOf("") }
    var tried by remember(step) { mutableStateOf(false) }
    val spacing = EasyPaperTheme.spacing

    Text(medication.product?.let(::cardTitle) ?: medication.name, style = EasyPaperTheme.typography.headline,
        color = EasyPaperTheme.colors.label, modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.s).semantics { heading() })
    Note(stringResource(Res.string.rec_intake_note))
    val amountLabel = stringResource(Res.string.rec_amount)
    GroupedSection(header = amountLabel) {
        TextFieldRow(amount, { amount = it.take(6) }, placeholder = "1", description = amountLabel, keyboardType = KeyboardType.Decimal)
        if (fixedUnit != null) {
            RowSeparator()
            // the registry's counting unit, e.g. "1 sachet"
            ListRow(amountWithUnit(intakeAmount(amount) ?: "1", fixedUnit), chevron = false)
        } else {
            RowSeparator()
            TextFieldRow(unit, { unit = it.take(30) }, placeholder = stringResource(Res.string.rec_unit_free))
        }
    }
    Spacer(Modifier.height(spacing.l))
    Text(stringResource(Res.string.rec_when), style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = spacing.m))
    SegmentedControl(listOf(stringResource(Res.string.rec_now), stringResource(Res.string.rec_today), stringResource(Res.string.rec_yesterday)),
        whenChoice, { whenChoice = it }, modifier = Modifier.padding(horizontal = spacing.m))
    if (whenChoice > 0) GroupedSection {
        TextFieldRow(time, { time = it.take(5) }, placeholder = stringResource(Res.string.rec_time), keyboardType = KeyboardType.Number)
    }

    val now = Clock.System.now().toEpochMilliseconds()
    val today = org.easypaper.ui.format.today()
    val parsedAmount = intakeAmount(amount)
    val takenAt: Long? = if (whenChoice == 0) now else timeOfDayMinutes(time)?.let { minutes ->
        val day: LocalDate = if (whenChoice == 1) today else today.minus(1, DateTimeUnit.DAY)
        LocalDateTime(day, LocalTime(minutes / 60, minutes % 60)).toInstant(timeZone).toEpochMilliseconds()
    }
    val unitText = fixedUnit ?: unit.trim()
    val problem = when {
        parsedAmount == null -> Res.string.rec_amount_error
        takenAt == null -> Res.string.rec_time_error
        takenAt > now + FUTURE_TOLERANCE_MS -> Res.string.rec_future_error
        unitText.isEmpty() -> Res.string.rec_unit_free
        else -> null
    }
    if (tried && problem != null) Note(stringResource(problem), live = true, error = true)
    Spacer(Modifier.height(spacing.xl))
    ProminentButton(stringResource(Res.string.rec_save_intake), enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
        tried = true
        if (problem == null && parsedAmount != null && takenAt != null) onSave(step.entryId, medication, Intake(parsedAmount, unitText, takenAt))
    })
    PlainButton(stringResource(Res.string.rec_only_list), enabled = !saving, modifier = Modifier.fillMaxWidth(),
        onClick = { onSave(step.entryId, medication, null) })
}

@Composable
private fun Note(text: String, live: Boolean = false, error: Boolean = false) {
    val spacing = EasyPaperTheme.spacing
    Text(text, style = EasyPaperTheme.typography.subheadline,
        color = if (error) EasyPaperTheme.colors.destructive else EasyPaperTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.s)
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier))
}

private const val FUTURE_TOLERANCE_MS = 5 * 60 * 1000L
