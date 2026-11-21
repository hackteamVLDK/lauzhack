package org.easypaper.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.cat_blood_pressure
import easypaper.app.shared.generated.resources.cat_diary
import easypaper.app.shared.generated.resources.cat_food
import easypaper.app.shared.generated.resources.cat_medicines
import easypaper.app.shared.generated.resources.cat_question
import easypaper.app.shared.generated.resources.cat_questionnaire
import easypaper.app.shared.generated.resources.cat_water
import easypaper.app.shared.generated.resources.days
import easypaper.app.shared.generated.resources.decimal_separator
import easypaper.app.shared.generated.resources.done
import easypaper.app.shared.generated.resources.err_complaint
import easypaper.app.shared.generated.resources.err_days
import easypaper.app.shared.generated.resources.err_doctor
import easypaper.app.shared.generated.resources.err_no_widgets
import easypaper.app.shared.generated.resources.err_other
import easypaper.app.shared.generated.resources.err_questionnaire
import easypaper.app.shared.generated.resources.err_start
import easypaper.app.shared.generated.resources.field_complaint
import easypaper.app.shared.generated.resources.ic_add
import easypaper.app.shared.generated.resources.ic_remove
import easypaper.app.shared.generated.resources.less
import easypaper.app.shared.generated.resources.more
import easypaper.app.shared.generated.resources.new_request
import easypaper.app.shared.generated.resources.q_default_1
import easypaper.app.shared.generated.resources.q_default_2
import easypaper.app.shared.generated.resources.rb_add_question
import easypaper.app.shared.generated.resources.rb_copied
import easypaper.app.shared.generated.resources.rb_copy
import easypaper.app.shared.generated.resources.rb_create
import easypaper.app.shared.generated.resources.rb_doctor_header
import easypaper.app.shared.generated.resources.rb_doctor_placeholder
import easypaper.app.shared.generated.resources.rb_invite_footer
import easypaper.app.shared.generated.resources.rb_invite_title
import easypaper.app.shared.generated.resources.rb_kind_scale
import easypaper.app.shared.generated.resources.rb_kind_text
import easypaper.app.shared.generated.resources.rb_kind_yesno
import easypaper.app.shared.generated.resources.rb_length
import easypaper.app.shared.generated.resources.rb_period
import easypaper.app.shared.generated.resources.rb_question_placeholder
import easypaper.app.shared.generated.resources.rb_complaint_placeholder
import easypaper.app.shared.generated.resources.rb_start
import easypaper.app.shared.generated.resources.rb_times_per_day
import easypaper.app.shared.generated.resources.rb_too_long_for_qr
import easypaper.app.shared.generated.resources.rb_water_no_target
import easypaper.app.shared.generated.resources.rb_water_target
import easypaper.app.shared.generated.resources.rb_widgets
import easypaper.app.shared.generated.resources.rb_widgets_footer
import easypaper.app.shared.generated.resources.remove
import easypaper.app.shared.generated.resources.scale_none
import easypaper.app.shared.generated.resources.scale_worst
import kotlinx.datetime.LocalDate
import org.easypaper.invite.DraftError
import org.easypaper.invite.MAX_REQUEST_DAYS
import org.easypaper.invite.inviteFitsQr
import org.easypaper.invite.inviteLink
import org.easypaper.model.CareRequest
import org.easypaper.model.QuestionKind
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.GrayButton
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.IosSwitch
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.QrCodeImage
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.SegmentedControl
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.components.StepperRow
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.components.TextFieldRow
import org.easypaper.ui.format.longDate
import org.easypaper.ui.format.today
import org.easypaper.ui.patient.litres
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val WidgetKind.category: Category
    get() = when (this) {
        WidgetKind.Questionnaire -> Category.Questionnaire
        WidgetKind.BloodPressure -> Category.BloodPressure
        WidgetKind.Water -> Category.Water
        WidgetKind.Food -> Category.Food
        WidgetKind.Medicines -> Category.Medicines
        WidgetKind.Diary -> Category.Diary
        WidgetKind.DoctorQuestions -> Category.DoctorQuestion
    }

private val WidgetKind.titleRes: StringResource
    get() = when (this) {
        WidgetKind.Questionnaire -> Res.string.cat_questionnaire
        WidgetKind.BloodPressure -> Res.string.cat_blood_pressure
        WidgetKind.Water -> Res.string.cat_water
        WidgetKind.Food -> Res.string.cat_food
        WidgetKind.Medicines -> Res.string.cat_medicines
        WidgetKind.Diary -> Res.string.cat_diary
        WidgetKind.DoctorQuestions -> Res.string.cat_question
    }

private fun DraftError.message(): StringResource = when (this) {
    DraftError.BLANK_DOCTOR -> Res.string.err_doctor
    DraftError.BLANK_COMPLAINT -> Res.string.err_complaint
    DraftError.BAD_START_DATE -> Res.string.err_start
    DraftError.DAYS_OUT_OF_RANGE -> Res.string.err_days
    DraftError.NO_WIDGETS -> Res.string.err_no_widgets
    DraftError.QUESTIONNAIRE_INCOMPLETE -> Res.string.err_questionnaire
    DraftError.DUPLICATE_WIDGET_ID, DraftError.BLANK_WIDGET_TITLE -> Res.string.err_other
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * "New request" (task 5): the doctor sets the complaint, their name, the period and what the patient records,
 * then creates the invite: a QR code and a link that carry exactly this request.
 */
@Composable
fun RequestBuilderScreen(back: BackAction?, onCreated: (CareRequest) -> Unit = {}) {
    val q1 = stringResource(Res.string.q_default_1)
    val q2 = stringResource(Res.string.q_default_2)
    val state = remember(q1, q2) { RequestBuilderState(startQuestions = listOf(QuestionDraft(q1, QuestionKind.YES_NO), QuestionDraft(q2, QuestionKind.SCALE))) }
    val titles = WidgetKind.entries.associateWith { stringResource(it.titleRes) }
    val scaleEnds = listOf(stringResource(Res.string.scale_none), stringResource(Res.string.scale_worst))
    val locale = Locale.current.language
    val today = remember { today() }
    var created by remember { mutableStateOf<CareRequest?>(null) }
    val spacing = EasyPaperTheme.spacing

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = stringResource(Res.string.new_request), back = back) {
            item(key = "complaint") {
                GroupedSection(header = stringResource(Res.string.field_complaint)) {
                    TextAreaRow(state.complaint, { state.complaint = it }, placeholder = stringResource(Res.string.rb_complaint_placeholder))
                }
            }
            item(key = "doctor") {
                GroupedSection(Modifier.padding(top = spacing.sectionGap), header = stringResource(Res.string.rb_doctor_header)) {
                    TextFieldRow(state.doctorName, { state.doctorName = it }, placeholder = stringResource(Res.string.rb_doctor_placeholder))
                }
            }
            item(key = "period") { PeriodSection(state, today) }
            item(key = "widgets") { WidgetsSection(state, titles) }
            item(key = "create") {
                Column(Modifier.padding(top = spacing.sectionGap), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    for (error in state.errors) {
                        Text(stringResource(error.message()), style = EasyPaperTheme.typography.subheadline, color = EasyPaperTheme.colors.destructive)
                    }
                    Spacer(Modifier.height(spacing.xs))
                    ProminentButton(stringResource(Res.string.rb_create), modifier = Modifier.fillMaxWidth(), onClick = {
                        state.create(today, titles, locale, now(), scaleEnds)?.let { request ->
                            created = request
                            onCreated(request)
                        }
                    })
                }
            }
        }
        InviteSheet(created, onDismiss = { created = null })
    }
}

@Composable
private fun PeriodSection(state: RequestBuilderState, today: LocalDate) {
    val less = stringResource(Res.string.less)
    val more = stringResource(Res.string.more)
    GroupedSection(Modifier.padding(top = EasyPaperTheme.spacing.sectionGap), header = stringResource(Res.string.rb_period)) {
        StepperRow(
            stringResource(Res.string.rb_start), longDate(state.startDate(today)),
            onLess = { state.changeStart(-1) }, onMore = { state.changeStart(1) }, lessLabel = less, moreLabel = more,
            canLess = state.startOffsetDays > 0,
        )
        RowSeparator(EasyPaperTheme.spacing.m)
        StepperRow(
            stringResource(Res.string.rb_length), pluralStringResource(Res.plurals.days, state.days, state.days),
            onLess = { state.changeDays(-1) }, onMore = { state.changeDays(1) }, lessLabel = less, moreLabel = more,
            canLess = state.days > 1, canMore = state.days < MAX_REQUEST_DAYS,
        )
    }
}

@Composable
private fun WidgetsSection(state: RequestBuilderState, titles: Map<WidgetKind, String>) {
    val spacing = EasyPaperTheme.spacing
    GroupedSection(
        Modifier.padding(top = spacing.sectionGap),
        header = stringResource(Res.string.rb_widgets),
        footer = stringResource(Res.string.rb_widgets_footer),
    ) {
        WidgetKind.entries.forEachIndexed { index, kind ->
            if (index > 0) RowSeparator()
            val on = state.enabled[kind] == true
            CategoryRow(kind.category, titles.getValue(kind), trailing = {
                IosSwitch(on, onCheckedChange = { state.enabled[kind] = it })
            }) {
                if (on) WidgetOptions(state, kind)
            }
        }
    }
}

/** Options that change what the patient is asked for; only where there is a real choice. */
@Composable
private fun WidgetOptions(state: RequestBuilderState, kind: WidgetKind) {
    val less = stringResource(Res.string.less)
    val more = stringResource(Res.string.more)
    when (kind) {
        WidgetKind.BloodPressure -> Options {
            InlineStepper(stringResource(Res.string.rb_times_per_day, state.bpTimesPerDay), less, more,
                canLess = state.bpTimesPerDay > 1, canMore = state.bpTimesPerDay < BP_TIMES_MAX,
                onLess = { state.changeBpTimes(-1) }, onMore = { state.changeBpTimes(1) })
        }
        WidgetKind.Water -> Options {
            val sep = stringResource(Res.string.decimal_separator).firstOrNull() ?: '.'
            val label = if (state.waterTargetMl == 0) stringResource(Res.string.rb_water_no_target)
            else stringResource(Res.string.rb_water_target, litres(state.waterTargetMl, sep))
            InlineStepper(label, less, more, canLess = state.waterTargetMl > 0, canMore = state.waterTargetMl < WATER_TARGET_MAX_ML,
                onLess = { state.changeWaterTarget(-1) }, onMore = { state.changeWaterTarget(1) })
        }
        WidgetKind.Questionnaire -> Options { QuestionsEditor(state) }
        else -> Unit
    }
}

@Composable
private fun Options(content: @Composable () -> Unit) {
    Column(Modifier.padding(top = EasyPaperTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xs)) { content() }
}

@Composable
private fun InlineStepper(label: String, less: String, more: String, canLess: Boolean, canMore: Boolean, onLess: () -> Unit, onMore: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xs)) {
        Text(label, style = EasyPaperTheme.typography.subheadline, color = EasyPaperTheme.colors.label, modifier = Modifier.weight(1f))
        StepperButtons(less, more, canLess, canMore, onLess, onMore)
    }
}

@Composable
private fun StepperButtons(less: String, more: String, canLess: Boolean, canMore: Boolean, onLess: () -> Unit, onMore: () -> Unit) {
    // Same control as StepperRow, without its own row: inside a category row's options.
    Row(horizontalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xs)) {
        GrayButton("", onClick = onLess, icon = Res.drawable.ic_remove, enabled = canLess,
            modifier = Modifier.semanticsLabel(less))
        GrayButton("", onClick = onMore, icon = Res.drawable.ic_add, enabled = canMore,
            modifier = Modifier.semanticsLabel(more))
    }
}

private fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }

@Composable
private fun QuestionsEditor(state: RequestBuilderState) {
    val spacing = EasyPaperTheme.spacing
    val kinds = listOf(QuestionKind.YES_NO, QuestionKind.SCALE, QuestionKind.TEXT)
    val kindLabels = listOf(stringResource(Res.string.rb_kind_yesno), stringResource(Res.string.rb_kind_scale), stringResource(Res.string.rb_kind_text))
    state.questions.forEachIndexed { index, question ->
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            // A gray field inside the white row, so it reads as something to type in.
            Box(Modifier.fillMaxWidth().clip(EasyPaperTheme.shapes.small).background(EasyPaperTheme.colors.fill)) {
                TextFieldRow(question.text, { state.questions[index] = question.copy(text = it) }, placeholder = stringResource(Res.string.rb_question_placeholder))
            }
            SegmentedControl(kindLabels, kinds.indexOf(question.kind).takeIf { it >= 0 }, onSelect = { state.questions[index] = question.copy(kind = kinds[it]) })
            PlainButton(stringResource(Res.string.remove), onClick = { state.questions.removeAt(index) })
        }
    }
    PlainButton("+ " + stringResource(Res.string.rb_add_question), onClick = { state.questions.add(QuestionDraft("", QuestionKind.YES_NO)) })
}

/** The invite: a QR code when the link fits one, the link to copy or send in any case. */
@Composable
private fun InviteSheet(request: CareRequest?, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf<CareRequest?>(null) }
    if (request != null) shown = request
    var copied by remember(request) { mutableStateOf(false) }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val spacing = EasyPaperTheme.spacing
    Sheet(visible = request != null, title = stringResource(Res.string.rb_invite_title), doneLabel = stringResource(Res.string.done), onDismiss = onDismiss) {
        val r = shown ?: return@Sheet
        val link = remember(r) { inviteLink(r) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            if (inviteFitsQr(link)) {
                QrCodeImage(link, description = stringResource(Res.string.rb_invite_title))
            } else {
                Text(stringResource(Res.string.rb_too_long_for_qr), style = EasyPaperTheme.typography.body, color = EasyPaperTheme.colors.label)
            }
            GroupedSection {
                SelectionContainer {
                    Text(link, style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel,
                        maxLines = 3, modifier = Modifier.padding(spacing.m))
                }
            }
            PlainButton(stringResource(if (copied) Res.string.rb_copied else Res.string.rb_copy), onClick = {
                clipboard.setText(AnnotatedString(link))
                copied = true
            })
            Text(stringResource(Res.string.rb_invite_footer), style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel)
        }
    }
}
