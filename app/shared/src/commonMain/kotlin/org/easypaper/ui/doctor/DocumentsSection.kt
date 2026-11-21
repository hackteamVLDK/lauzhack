package org.easypaper.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.choose_photo
import easypaper.app.shared.generated.resources.doc_clinic_only
import easypaper.app.shared.generated.resources.doc_failed
import easypaper.app.shared.generated.resources.doc_field_allergy
import easypaper.app.shared.generated.resources.doc_field_diagnosis
import easypaper.app.shared.generated.resources.doc_field_lab
import easypaper.app.shared.generated.resources.doc_field_medication
import easypaper.app.shared.generated.resources.doc_field_procedure
import easypaper.app.shared.generated.resources.doc_from
import easypaper.app.shared.generated.resources.doc_hint
import easypaper.app.shared.generated.resources.doc_in_form
import easypaper.app.shared.generated.resources.doc_no_server
import easypaper.app.shared.generated.resources.doc_none
import easypaper.app.shared.generated.resources.doc_paste
import easypaper.app.shared.generated.resources.doc_quote_checked
import easypaper.app.shared.generated.resources.doc_quote_unchecked
import easypaper.app.shared.generated.resources.doc_read
import easypaper.app.shared.generated.resources.doc_reading
import easypaper.app.shared.generated.resources.doc_title
import easypaper.app.shared.generated.resources.doc_to_form
import easypaper.app.shared.generated.resources.take_photo
import kotlinx.coroutines.launch
import org.easypaper.ai.AiClient
import org.easypaper.ai.AiException
import org.easypaper.ai.DocumentFact
import org.easypaper.ui.capture.rememberPhotoSource
import org.easypaper.ui.components.DraftBadge
import org.easypaper.ui.components.GrayButton
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * Paper documents at the visit (task 41): the doctor pastes the text of a discharge letter or lab report, or adds a
 * photo of it; the clinic module (#8, never the cloud) lists the facts with the document's own words. "Add to form"
 * puts a fact into the clinic form as the doctor's edit — the field stays a draft until the doctor confirms it.
 * [ai] is null when there is no server; [facts] live in ShellState so they survive leaving the screen.
 */
@Composable
fun DocumentsSection(state: VisitState, ai: (() -> AiClient)?, facts: List<DocumentFact>, onFacts: (List<DocumentFact>) -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    val fromDocument = stringResource(Res.string.doc_from)

    fun read(text: String?, image: ByteArray?) {
        val client = ai?.invoke() ?: run { message = Res.string.doc_no_server; return }
        busy = true
        message = Res.string.doc_reading
        scope.launch {
            message = try {
                val id = "doc-" + Random.nextInt(1_000_000)
                val found = client.extractDocument(id, text = text, image = image).facts
                onFacts(facts + found)
                if (found.isEmpty()) Res.string.doc_none else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                if (e.status == 403) Res.string.doc_clinic_only else Res.string.doc_failed
            } catch (e: Exception) {
                Res.string.doc_failed
            } finally {
                busy = false
            }
        }
    }
    val photos = rememberPhotoSource(onError = { message = Res.string.doc_failed }) { read(null, it) }

    Column(Modifier.padding(top = spacing.sectionGap)) {
        Text(stringResource(Res.string.doc_title), style = EasyPaperTheme.typography.title2, color = colors.label,
            modifier = Modifier.padding(start = spacing.m, bottom = spacing.xs))
        GroupedSection(footer = stringResource(Res.string.doc_hint)) {
            TextAreaRow(text, { text = it }, placeholder = stringResource(Res.string.doc_paste))
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.s),
            horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            TintedButton(stringResource(Res.string.doc_read), enabled = !busy && text.isNotBlank(), onClick = { read(text.trim(), null) })
            photos.takePhoto?.let { GrayButton(stringResource(Res.string.take_photo), enabled = !busy, onClick = it) }
            photos.pickPhoto?.let { GrayButton(stringResource(Res.string.choose_photo), enabled = !busy, onClick = it) }
        }
        message?.let {
            Text(stringResource(it), style = EasyPaperTheme.typography.subheadline,
                color = if (it == Res.string.doc_reading || it == Res.string.doc_none) colors.secondaryLabel else colors.destructive,
                modifier = Modifier.padding(horizontal = spacing.m).semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (facts.isNotEmpty()) GroupedSection(Modifier.padding(top = spacing.s)) {
            facts.forEachIndexed { index, fact ->
                if (index > 0) RowSeparator(spacing.m)
                FactRow(fact, inForm = state.hasFact(fact, fromDocument), onAdd = { state.addFact(fact, fromDocument) })
            }
        }
    }
}

@Composable
private fun FactRow(fact: DocumentFact, inForm: Boolean, onAdd: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        DraftBadge(stringResource(fieldName(fact.field)))
        Text(fact.value, style = EasyPaperTheme.typography.body, color = colors.label)
        Text("«${fact.quote}»", style = EasyPaperTheme.typography.footnote, color = colors.secondaryLabel)
        Text(stringResource(if (fact.verified) Res.string.doc_quote_checked else Res.string.doc_quote_unchecked),
            style = EasyPaperTheme.typography.footnote, color = if (fact.verified) colors.confirmed else colors.secondaryLabel)
        TintedButton(stringResource(if (inForm) Res.string.doc_in_form else Res.string.doc_to_form), enabled = !inForm, onClick = onAdd)
    }
}

private fun fieldName(field: String): StringResource = when (field) {
    "medication" -> Res.string.doc_field_medication
    "allergy" -> Res.string.doc_field_allergy
    "procedure" -> Res.string.doc_field_procedure
    "lab_value" -> Res.string.doc_field_lab
    else -> Res.string.doc_field_diagnosis
}
