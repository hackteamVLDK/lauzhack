package org.easypaper.ui.patient

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.invite_damaged
import easypaper.app.shared.generated.resources.invite_newer
import easypaper.app.shared.generated.resources.invite_not
import easypaper.app.shared.generated.resources.invite_open
import easypaper.app.shared.generated.resources.invite_placeholder
import easypaper.app.shared.generated.resources.invite_too_large
import org.easypaper.invite.InviteError
import org.easypaper.invite.InviteResult
import org.easypaper.invite.parseInvite
import org.easypaper.model.CareRequest
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun InviteError.message(): StringResource = when (this) {
    InviteError.NOT_INVITE -> Res.string.invite_not
    InviteError.NEWER_VERSION -> Res.string.invite_newer
    InviteError.DAMAGED -> Res.string.invite_damaged
    InviteError.TOO_LARGE -> Res.string.invite_too_large
}

/**
 * The patient pastes the link or code from the doctor (task 5); [initial] comes from a link that opened the
 * app. [onOpen] gets the request exactly as the doctor built it; a wrong text says what to do instead.
 */
@Composable
fun ColumnScope.OpenInviteForm(initial: String?, onOpen: (CareRequest) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial.orEmpty()) }
    var error by remember(initial) { mutableStateOf<InviteError?>(null) }
    GroupedSection {
        TextAreaRow(text, { text = it; error = null }, placeholder = stringResource(Res.string.invite_placeholder))
    }
    error?.let {
        Text(
            stringResource(it.message()),
            style = EasyPaperTheme.typography.subheadline,
            color = EasyPaperTheme.colors.destructive,
            modifier = Modifier.padding(top = EasyPaperTheme.spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(EasyPaperTheme.spacing.xl))
    ProminentButton(stringResource(Res.string.invite_open), modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank(), onClick = {
        when (val result = parseInvite(text)) {
            is InviteResult.Ok -> onOpen(result.request)
            is InviteResult.Error -> error = result.reason
        }
    })
}
