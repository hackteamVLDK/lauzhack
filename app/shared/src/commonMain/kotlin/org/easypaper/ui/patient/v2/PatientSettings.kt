@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.easypaper.ui.patient.v2

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import org.easypaper.ui.components.*
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.rememberSavedSetting
import org.easypaper.ui.theme.Category
import org.easypaper.model.CareRequest
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.ui.patient.entryText
import org.jetbrains.compose.resources.*

class PatientSettings(
    val onboarding: SavedSetting, val language: SavedSetting, val pinned: SavedSetting,
    val name: SavedSetting, val birth: SavedSetting, val sex: SavedSetting,
    val height: SavedSetting, val weight: SavedSetting, val allergies: SavedSetting,
    val reminders: SavedSetting, val history: SavedSetting,
) {
    val pinnedCategories: List<Category> get() = pinned.value?.split(',')?.mapNotNull { name ->
        Category.entries.firstOrNull { it.name == name }
    }?.distinct() ?: defaultPinned
    fun pin(categories: Collection<Category>) = pinned.update(categories.distinct().joinToString(",") { it.name })
    fun addWidget(category: Category) = pin(pinnedCategories + category)
    fun removeWidget(category: Category) = pin(pinnedCategories - category)
    fun moveWidget(category: Category, offset: Int) {
        val ordered = pinnedCategories.toMutableList()
        val from = ordered.indexOf(category)
        val to = from + offset
        if (from >= 0 && to in ordered.indices) {
            ordered.add(to, ordered.removeAt(from))
            pin(ordered)
        }
    }
    fun reset() {
        listOf(onboarding, language, pinned, name, birth, sex, height, weight, allergies, reminders, history)
            .forEach { it.update(null) }
    }
}

@Composable
fun rememberPatientSettings() = PatientSettings(
    rememberSavedSetting("p2_onboarding"), rememberSavedSetting("p2_language"), rememberSavedSetting("p2_pinned"),
    rememberSavedSetting("p2_name"), rememberSavedSetting("p2_birth"), rememberSavedSetting("p2_sex"),
    rememberSavedSetting("p2_height"), rememberSavedSetting("p2_weight"), rememberSavedSetting("p2_allergies"),
    rememberSavedSetting("p2_reminders"), rememberSavedSetting("p2_history"),
)

fun supportedLanguage(language: String): String = language.substringBefore('-').substringBefore('_').lowercase()
    .takeIf { it in listOf("en", "de", "uk", "fr", "ru") } ?: "en"

/**
 * Compose Resources 1.12 has no public per-subtree locale provider. Keep its internal bridge in
 * this one place; it changes only the patient subtree, including reused entry forms and sheets.
 * Revisit when Compose exposes a public provider. No process-wide locale changes affect doctors.
 */
@OptIn(InternalResourceApi::class)
@Composable
fun PatientLanguage(language: String, content: @Composable () -> Unit) {
    val system = rememberResourceEnvironment()
    val environment = remember(language, system) {
        object : ComposeEnvironment {
            @Composable override fun rememberEnvironment() = ResourceEnvironment(
                LanguageQualifier(language), ScriptQualifier(""), RegionQualifier(""), system.theme, system.density,
            )
        }
    }
    CompositionLocalProvider(LocalComposeEnvironment provides environment, content = content)
}

@Composable
fun categoryTitle(category: Category): String = stringResource(when (category) {
    Category.BloodPressure -> Res.string.p2_pressure
    Category.Water -> Res.string.p2_water
    Category.Food -> Res.string.p2_food
    Category.Medicines -> Res.string.p2_medicines
    Category.Questionnaire -> Res.string.p2_symptoms
    Category.Diary -> Res.string.p2_diary
    Category.DoctorQuestion -> Res.string.p2_questions
    Category.Measurements -> Res.string.p2_measurements
})

@Composable
fun LanguagePicker(settings: PatientSettings) {
    val labels = listOf(stringResource(Res.string.p2_system), stringResource(Res.string.p2_english),
        stringResource(Res.string.p2_german), stringResource(Res.string.p2_ukrainian),
        stringResource(Res.string.p2_french), stringResource(Res.string.p2_russian))
    val codes = listOf(null, "en", "de", "uk", "fr", "ru")
    ChoiceList(labels, labels[codes.indexOf(settings.language.value).coerceAtLeast(0)], onSelect = { selected ->
        settings.language.update(codes[labels.indexOf(selected)])
    })
}

@Composable
fun CategorySwitches(selected: Set<Category>, onChange: (Set<Category>) -> Unit) {
    GroupedSection {
        Category.entries.forEachIndexed { i, category ->
            if (i > 0) RowSeparator()
            val title = categoryTitle(category)
            // trailing, not the content slot: the switch belongs at the end of the row, not under the title
            CategoryRow(category, title, trailing = {
                IosSwitch(category in selected, { checked -> onChange(if (checked) selected + category else selected - category) },
                    Modifier.semantics { contentDescription = title })
            })
        }
    }
}

fun validBirth(value: String?, today: LocalDate): Boolean = value.isNullOrBlank() ||
    runCatching { LocalDate.parse(value) <= today }.getOrDefault(false)

/** Personal prompts are app copy; doctor's questionnaire wording remains the doctor's wording. */
@Composable
fun displayRequest(request: CareRequest): CareRequest {
    if (request.id != PERSONAL_REQUEST_ID) return request
    val symptoms = categoryTitle(Category.Questionnaire)
    return request.copy(widgets = request.widgets.map { widget ->
        if (widget is QuestionnaireWidget) widget.copy(title = symptoms, questions = widget.questions.map { it.copy(text = symptoms) }) else widget
    })
}

@Composable
fun patientEntryText(owned: OwnedEntry): String = entryText(owned.entry, displayRequest(owned.record.request))
