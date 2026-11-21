package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import org.easypaper.ui.components.*
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun PatientMessage(text: String, error: Boolean = false) {
    Text(text, style = EasyPaperTheme.typography.body,
        color = if (error) EasyPaperTheme.colors.destructive else EasyPaperTheme.colors.secondaryLabel,
        modifier = Modifier.padding(EasyPaperTheme.spacing.m).semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
fun ConfirmSheet(visible: Boolean, title: String, onDismiss: () -> Unit, onConfirm: () -> Unit, busy: Boolean = false) {
    Sheet(visible, title, stringResource(Res.string.p2_cancel), onDismiss) {
        ListRow(title, chevron = false)
        TintedButton(stringResource(if (busy) Res.string.p2_working else Res.string.p2_delete), onConfirm, enabled = !busy)
    }
}

@Composable
fun pressureText(range: PressureRange): String = stringResource(Res.string.p2_pressure_range,
    range.systolicMin, range.systolicMax, range.diastolicMin, range.diastolicMax)
