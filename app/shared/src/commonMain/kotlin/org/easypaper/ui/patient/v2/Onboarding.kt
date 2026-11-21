package org.easypaper.ui.patient.v2

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import org.easypaper.ui.components.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun Onboarding(settings: PatientSettings, today: LocalDate, saving: Boolean, error: Boolean,
               onFinish: (openInvite: Boolean) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var invalidBirth by remember { mutableStateOf(false) }
    val titles = listOf(Res.string.p2_welcome, Res.string.p2_language, Res.string.p2_about_you,
        Res.string.p2_privacy, Res.string.p2_reminders, Res.string.p2_track)
    val bodies = listOf(Res.string.p2_welcome_body, Res.string.p2_language_body, Res.string.p2_optional_details,
        Res.string.p2_privacy_body, Res.string.p2_reminders_body, Res.string.p2_track_body)
    OnboardingPage(
        icon = if (step == 0) Res.drawable.ic_edit_note else null,
        title = stringResource(titles[step]), body = stringResource(bodies[step]),
        primary = stringResource(when { saving -> Res.string.p2_working; step == 4 -> Res.string.p2_allow_reminders
            step == 5 -> Res.string.p2_later; else -> Res.string.p2_continue }),
        onPrimary = {
            if (!saving) when (step) {
                2 -> { invalidBirth = !validBirth(settings.birth.value, today); if (!invalidBirth) step++ }
                4 -> { settings.reminders.update("true"); step++ }
                5 -> onFinish(false)
                else -> step++
            }
        },
        secondary = if (step == 4) stringResource(Res.string.p2_not_now)
            else if (step > 0) stringResource(Res.string.p2_back) else null,
        onSecondary = { if (!saving) { if (step == 4) { settings.reminders.update("false"); step++ } else step-- } },
    ) {
        when (step) {
            0 -> GroupedSection {
                CategoryRow(org.easypaper.ui.theme.Category.Diary, stringResource(Res.string.p2_welcome_record))
                CategoryRow(org.easypaper.ui.theme.Category.BloodPressure, stringResource(Res.string.p2_welcome_trends))
                CategoryRow(org.easypaper.ui.theme.Category.DoctorQuestion, stringResource(Res.string.p2_welcome_share))
            }
            1 -> LanguagePicker(settings)
            2 -> {
                GroupedSection {
                    TextFieldRow(settings.name.value.orEmpty(), settings.name::update, stringResource(Res.string.p2_name))
                    RowSeparator()
                    TextFieldRow(settings.birth.value.orEmpty(), { settings.birth.update(it); invalidBirth = false }, stringResource(Res.string.p2_birth))
                    RowSeparator()
                    TextFieldRow(settings.sex.value.orEmpty(), settings.sex::update, stringResource(Res.string.p2_sex))
                }
                if (invalidBirth) PatientMessage(stringResource(Res.string.p2_birth_error), error = true)
            }
            5 -> {
                CategorySwitches(settings.pinnedCategories, settings::pin)
                TintedButton(stringResource(Res.string.p2_have_request), { if (!saving) onFinish(true) })
            }
        }
        if (error) PatientMessage(stringResource(Res.string.p2_error), error = true)
    }
}
