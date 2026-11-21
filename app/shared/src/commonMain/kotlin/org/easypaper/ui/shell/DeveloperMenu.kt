package org.easypaper.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.appearance_dark
import easypaper.app.shared.generated.resources.clear_data
import easypaper.app.shared.generated.resources.design_catalog
import easypaper.app.shared.generated.resources.dev_footer
import easypaper.app.shared.generated.resources.dev_server
import easypaper.app.shared.generated.resources.dev_server_footer
import easypaper.app.shared.generated.resources.developer
import easypaper.app.shared.generated.resources.fill_demo
import easypaper.app.shared.generated.resources.large_text
import easypaper.app.shared.generated.resources.switch_to_doctor
import easypaper.app.shared.generated.resources.switch_to_patient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.IosSwitch
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Hidden menu (long press on the large title): design catalog, demo data, appearance and text size
 * for checking screens, and on Desktop the switch between the patient and the doctor app.
 */
@Composable
fun ColumnScope.DeveloperMenuContent(
    state: ShellState,
    settings: ShellSettings,
    onOpenCatalog: () -> Unit,
    onSwitchRole: (() -> Unit)?,
    onDone: () -> Unit,
    /** Outlives this sheet: demo data and clearing keep running after the menu closes (disk storage is slow). */
    scope: CoroutineScope,
) {
    val systemDark = isSystemInDarkTheme()
    val inset = EasyPaperTheme.spacing.m
    GroupedSection(header = stringResource(Res.string.dev_server), footer = stringResource(Res.string.dev_server_footer)) {
        // the relay and AI server; a real phone needs the address of the computer that runs it (task 30)
        TextAreaRow(settings.serverUrl.orEmpty(), { typed ->
            settings.serverUrl = typed.trim().ifEmpty { null }
            settings.saveServerUrl(settings.serverUrl)
        }, placeholder = "http://192.168.1.20:8080")
    }
    GroupedSection(Modifier.padding(top = EasyPaperTheme.spacing.sectionGap), footer = stringResource(Res.string.dev_footer)) {
        ListRow(stringResource(Res.string.design_catalog), onClick = { onOpenCatalog(); onDone() })
        RowSeparator(inset)
        if (state.hasData) {
            ListRow(stringResource(Res.string.clear_data), titleColor = EasyPaperTheme.colors.destructive, chevron = false,
                onClick = { scope.launch { state.clear() }; onDone() })
        } else {
            ListRow(stringResource(Res.string.fill_demo), titleColor = EasyPaperTheme.colors.link, chevron = false,
                onClick = { scope.launch { state.fillWithDemoData() }; onDone() })
        }
        RowSeparator(inset)
        ListRow(stringResource(Res.string.appearance_dark), trailing = {
            IosSwitch(settings.dark ?: systemDark, onCheckedChange = { settings.dark = it })
        })
        RowSeparator(inset)
        ListRow(stringResource(Res.string.large_text), trailing = {
            IosSwitch(settings.largeText, onCheckedChange = { settings.largeText = it })
        })
        if (onSwitchRole != null) {
            RowSeparator(inset)
            val label = if (state.role == Role.Doctor) Res.string.switch_to_patient else Res.string.switch_to_doctor
            ListRow(stringResource(label), titleColor = EasyPaperTheme.colors.link, chevron = false, onClick = { onSwitchRole(); onDone() })
        }
    }
}

/** Title of the developer sheet. */
@Composable
fun developerTitle(): String = stringResource(Res.string.developer)
