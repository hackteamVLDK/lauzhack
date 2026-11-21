package org.easypaper.ui.cost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.cost_big_model
import easypaper.app.shared.generated.resources.cost_budget
import easypaper.app.shared.generated.resources.cost_budget_amount
import easypaper.app.shared.generated.resources.cost_budget_unlimited
import easypaper.app.shared.generated.resources.cost_cache_hit
import easypaper.app.shared.generated.resources.cost_clinic
import easypaper.app.shared.generated.resources.cost_cloud
import easypaper.app.shared.generated.resources.cost_comparison_footer
import easypaper.app.shared.generated.resources.cost_decimal_separator
import easypaper.app.shared.generated.resources.cost_demo_capture
import easypaper.app.shared.generated.resources.cost_empty_message
import easypaper.app.shared.generated.resources.cost_empty_title
import easypaper.app.shared.generated.resources.cost_estimated
import easypaper.app.shared.generated.resources.cost_key_configured
import easypaper.app.shared.generated.resources.cost_mode
import easypaper.app.shared.generated.resources.cost_mode_clinic
import easypaper.app.shared.generated.resources.cost_mode_cloud
import easypaper.app.shared.generated.resources.cost_mode_off
import easypaper.app.shared.generated.resources.cost_no
import easypaper.app.shared.generated.resources.cost_no_report_selected
import easypaper.app.shared.generated.resources.cost_provider
import easypaper.app.shared.generated.resources.cost_report
import easypaper.app.shared.generated.resources.cost_server
import easypaper.app.shared.generated.resources.cost_session
import easypaper.app.shared.generated.resources.cost_small_model
import easypaper.app.shared.generated.resources.cost_step_document
import easypaper.app.shared.generated.resources.cost_step_photo
import easypaper.app.shared.generated.resources.cost_step_summary
import easypaper.app.shared.generated.resources.cost_title
import easypaper.app.shared.generated.resources.cost_tokens
import easypaper.app.shared.generated.resources.cost_total
import easypaper.app.shared.generated.resources.cost_unavailable
import easypaper.app.shared.generated.resources.cost_usd
import easypaper.app.shared.generated.resources.cost_yes
import org.easypaper.ai.AiStatus
import org.easypaper.ai.AiStep
import org.easypaper.ai.AiUsage
import org.easypaper.ai.costComparison
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/** Read-only usage details. The caller supplies server data; this screen never fetches it. */
@Composable
fun AiCostScreen(
    status: AiStatus?,
    usage: List<AiUsage>,
    selectedRequestId: String?,
    reportComplaint: String?,
    reportPeriod: String?,
    modifier: Modifier = Modifier,
    back: BackAction? = null,
    demoCapture: Boolean = false,
) {
    val spacing = EasyPaperTheme.spacing
    // AiUsage.reportId is the request id supplied by SummaryPipeline, not Report.id.
    // An unselected report must never pick up unassigned calls or another report's usage.
    val reportUsage = usage.filter { selectedRequestId != null && it.reportId == selectedRequestId }
    LargeTitleScaffold(
        title = stringResource(Res.string.cost_title),
        modifier = modifier,
        back = back,
        header = {
            if (demoCapture) {
                Text(
                    stringResource(Res.string.cost_demo_capture),
                    style = EasyPaperTheme.typography.callout,
                    color = EasyPaperTheme.colors.label,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        },
    ) {
        item {
            GroupedSection(
                header = stringResource(Res.string.cost_report),
                footer = stringResource(Res.string.cost_comparison_footer),
            ) {
                ListRow(
                    title = reportComplaint ?: stringResource(Res.string.cost_no_report_selected),
                    subtitle = reportPeriod,
                )
                RowSeparator(spacing.m)
                UsageRows(reportUsage)
            }
        }
        item {
            GroupedSection(
                modifier = Modifier.padding(top = spacing.sectionGap),
                header = stringResource(Res.string.cost_session),
                footer = stringResource(Res.string.cost_comparison_footer),
            ) {
                UsageRows(usage)
            }
        }
        item {
            GroupedSection(
                modifier = Modifier.padding(top = spacing.sectionGap),
                header = stringResource(Res.string.cost_server),
            ) {
                if (status == null) {
                    ListRow(stringResource(Res.string.cost_unavailable))
                } else {
                    ServerRows(status)
                }
            }
        }
    }
}

@Composable
private fun UsageRows(usage: List<AiUsage>) {
    val spacing = EasyPaperTheme.spacing
    val comparison = costComparison(usage)
    val estimated = if (comparison.hasEstimates) stringResource(Res.string.cost_estimated) else null
    if (usage.isEmpty()) {
        ListRow(
            title = stringResource(Res.string.cost_empty_title),
            subtitle = stringResource(Res.string.cost_empty_message),
        )
    } else {
        usage.forEachIndexed { index, call ->
            if (index > 0) RowSeparator(spacing.m)
            UsageRow(call)
        }
    }
    RowSeparator(spacing.m)
    CostValueRow(
        label = stringResource(Res.string.cost_total),
        value = usd(comparison.actualUsd),
        detail = listOfNotNull(
            stringResource(Res.string.cost_tokens, comparison.inputTokens, comparison.outputTokens),
            estimated,
        ).joinToString("\n"),
    )
    RowSeparator(spacing.m)
    CostValueRow(stringResource(Res.string.cost_cloud), usd(comparison.cloudUsd), estimated)
    RowSeparator(spacing.m)
    CostValueRow(stringResource(Res.string.cost_clinic), usd(comparison.clinicUsd))
}

@Composable
private fun UsageRow(call: AiUsage) {
    val type = EasyPaperTheme.typography
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    Column(
        Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight)
            .padding(horizontal = spacing.m, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            stringResource(when (call.step) {
                AiStep.PhotoDescription -> Res.string.cost_step_photo
                AiStep.Summary -> Res.string.cost_step_summary
                AiStep.DocumentExtraction -> Res.string.cost_step_document
            }),
            style = type.headline,
            color = colors.label,
        )
        Text(call.model, style = type.subheadline, color = colors.secondaryLabel)
        Text(
            stringResource(Res.string.cost_tokens, if (call.cached) 0 else call.inputTokens, if (call.cached) 0 else call.outputTokens),
            style = type.callout.tabular(),
            color = colors.secondaryLabel,
        )
        Text(usd(if (call.cached) 0.0 else call.costUsd), style = type.body.tabular(), color = colors.label)
        if (call.estimated) {
            Text(stringResource(Res.string.cost_estimated), style = type.footnote, color = colors.secondaryLabel)
        }
        if (call.cached) {
            Text(stringResource(Res.string.cost_cache_hit), style = type.footnote, color = colors.secondaryLabel)
        }
    }
}

/** Values sit below their labels so long model IDs and large text can wrap without truncation. */
@Composable
private fun CostValueRow(label: String, value: String, detail: String? = null) {
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    val colors = EasyPaperTheme.colors
    Column(
        Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight)
            .padding(horizontal = spacing.m, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(label, style = type.body, color = colors.label)
        Text(value, style = type.headline.tabular(), color = colors.label)
        if (detail != null) Text(detail, style = type.callout.tabular(), color = colors.secondaryLabel)
    }
}

@Composable
private fun ServerRows(status: AiStatus) {
    val spacing = EasyPaperTheme.spacing
    val unavailable = stringResource(Res.string.cost_unavailable)
    val budget = status.budgetUsd
    CostValueRow(
        stringResource(Res.string.cost_mode),
        stringResource(when (status.mode) {
            "cloud" -> Res.string.cost_mode_cloud
            "clinic" -> Res.string.cost_mode_clinic
            "off" -> Res.string.cost_mode_off
            else -> Res.string.cost_unavailable
        }),
    )
    RowSeparator(spacing.m)
    CostValueRow(stringResource(Res.string.cost_provider), status.provider ?: unavailable)
    RowSeparator(spacing.m)
    CostValueRow(stringResource(Res.string.cost_small_model), status.smallModel ?: unavailable)
    RowSeparator(spacing.m)
    CostValueRow(stringResource(Res.string.cost_big_model), status.bigModel ?: unavailable)
    RowSeparator(spacing.m)
    CostValueRow(
        stringResource(Res.string.cost_budget),
        if (budget == null) stringResource(Res.string.cost_budget_unlimited)
        else stringResource(Res.string.cost_budget_amount, usd(status.spentUsd), usd(budget)),
    )
    RowSeparator(spacing.m)
    CostValueRow(
        stringResource(Res.string.cost_key_configured),
        stringResource(if (status.keyConfigured) Res.string.cost_yes else Res.string.cost_no),
    )
}

/** Fixed four decimal places, using the resource locale's decimal mark and USD placement. */
@Composable
private fun usd(value: Double): String {
    val scaled = (value * 10_000).roundToLong()
    val fraction = (scaled % 10_000).toString().padStart(4, '0')
    val number = (scaled / 10_000).toString() + stringResource(Res.string.cost_decimal_separator) + fraction
    return stringResource(Res.string.cost_usd, number)
}
