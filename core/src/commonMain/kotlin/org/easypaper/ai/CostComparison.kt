package org.easypaper.ai

import kotlinx.serialization.Serializable

/** Token charges for a report or session, compared with the same work on cloud and clinic models. */
@Serializable
data class CostComparison(
    /** Model calls, excluding cache hits, as in [AiStatus.calls]. */
    val calls: Int,
    val cacheHits: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val actualUsd: Double,
    val cloudUsd: Double,
    val clinicUsd: Double = 0.0,
    val perStep: Map<AiStep, StepCost>,
    val hasEstimates: Boolean = false,
)

@Serializable
data class StepCost(
    val calls: Int,
    val cacheHits: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val actualUsd: Double,
    val cloudUsd: Double,
    val clinicUsd: Double = 0.0,
    val hasEstimates: Boolean = false,
)

/** Latest report with recorded usage; unassigned calls belong only to the session. */
fun latestReportUsage(usage: List<AiUsage>): List<AiUsage> {
    val reportId = usage.lastOrNull { it.reportId != null }?.reportId ?: return emptyList()
    return usage.filter { it.reportId == reportId }
}

/** Local inference has no token charge; null [reportId] compares the entire session. */
fun costComparison(usage: List<AiUsage>, cloud: AiConfig = AiConfig.cloud(), reportId: String? = null): CostComparison {
    val selected = if (reportId == null) usage else usage.filter { it.reportId == reportId }
    val perStep = selected.groupBy { it.step }.mapValues { (step, items) ->
        // Cache records never represent new tokens, even if imported with the original counts.
        val calls = items.filterNot { it.cached }
        val input = calls.sumOf { it.inputTokens }
        val output = calls.sumOf { it.outputTokens }
        val model = when (step) {
            AiStep.PhotoDescription -> cloud.small
            AiStep.Summary, AiStep.DocumentExtraction -> cloud.big
        }
        StepCost(
            calls = calls.size,
            cacheHits = items.count { it.cached },
            inputTokens = input,
            outputTokens = output,
            actualUsd = calls.sumOf { it.costUsd },
            cloudUsd = cloud.price(model)?.cost(input, output) ?: 0.0,
            hasEstimates = calls.any { it.estimated },
        )
    }
    return CostComparison(
        calls = perStep.values.sumOf { it.calls },
        cacheHits = perStep.values.sumOf { it.cacheHits },
        inputTokens = perStep.values.sumOf { it.inputTokens },
        outputTokens = perStep.values.sumOf { it.outputTokens },
        actualUsd = perStep.values.sumOf { it.actualUsd },
        cloudUsd = perStep.values.sumOf { it.cloudUsd },
        perStep = perStep,
        hasEstimates = perStep.values.any { it.hasEstimates },
    )
}
