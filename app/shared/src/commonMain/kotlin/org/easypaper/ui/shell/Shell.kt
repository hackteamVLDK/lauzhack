package org.easypaper.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.app_name
import easypaper.app.shared.generated.resources.role_doctor
import easypaper.app.shared.generated.resources.role_doctor_detail
import easypaper.app.shared.generated.resources.role_patient
import easypaper.app.shared.generated.resources.role_patient_detail
import easypaper.app.shared.generated.resources.role_question
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

/** Largest system font size the layouts must survive (docs/design.md: up to 200 %). */
private const val LARGE_TEXT_SCALE = 2f

/**
 * Easy Paper for one device. [role] fixes the app (Android: patient, web: doctor); null lets the
 * person choose (Desktop), and then the developer menu can switch roles.
 */
@Composable
fun EasyPaperApp(
    role: Role? = null,
    settings: ShellSettings = remember { ShellSettings() },
    demo: Boolean = false,
) {
    val state = remember { ShellState(role).also { if (demo) it.fillWithDemoData() } }
    val canSwitch = role == null
    ShellTheme(settings) {
        when (state.role) {
            null -> RoleChooser(onChoose = { state.role = it })
            Role.Patient -> PatientApp(state, settings, onSwitchRole = if (canSwitch) ({ state.role = Role.Doctor }) else null)
            Role.Doctor -> DoctorApp(state, settings, onSwitchRole = if (canSwitch) ({ state.role = Role.Patient }) else null)
        }
    }
}

/** EasyPaperTheme plus the developer settings: forced appearance and 200 % text. */
@Composable
fun ShellTheme(settings: ShellSettings, content: @Composable () -> Unit) {
    EasyPaperTheme(darkTheme = settings.dark ?: isSystemInDarkTheme()) {
        val density = LocalDensity.current
        if (settings.largeText) {
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_TEXT_SCALE), content = content)
        } else {
            content()
        }
    }
}

/** Desktop start: who uses this computer. */
@Composable
fun RoleChooser(onChoose: (Role) -> Unit) {
    LargeTitleScaffold(
        title = stringResource(Res.string.app_name),
        header = {
            Text(
                stringResource(Res.string.role_question),
                style = EasyPaperTheme.typography.subheadline,
                color = EasyPaperTheme.colors.secondaryLabel,
            )
        },
    ) {
        item {
            GroupedSection {
                ListRow(stringResource(Res.string.role_patient), subtitle = stringResource(Res.string.role_patient_detail),
                    onClick = { onChoose(Role.Patient) })
                RowSeparator(EasyPaperTheme.spacing.m)
                ListRow(stringResource(Res.string.role_doctor), subtitle = stringResource(Res.string.role_doctor_detail),
                    onClick = { onChoose(Role.Doctor) })
            }
        }
    }
}
