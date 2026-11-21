package org.easypaper.ai

import kotlinx.serialization.encodeToString
import org.easypaper.model.EasyPaperJson
import kotlin.test.Test
import kotlin.test.assertEquals

class CostComparisonTest {
    @Test
    fun sumsCallsTokensAndActualChargesByStep() {
        val result = costComparison(listOf(
            usage(AiStep.PhotoDescription, 100, 20, 0.001),
            usage(AiStep.PhotoDescription, 200, 30, 0.002),
            usage(AiStep.Summary, 500, 100, 0.004),
            usage(AiStep.DocumentExtraction, 700, 50, 0.003),
        ))

        assertEquals(4, result.calls)
        assertEquals(0, result.cacheHits)
        assertEquals(1_500, result.inputTokens)
        assertEquals(200, result.outputTokens)
        assertEquals(0.010, result.actualUsd, 1e-12)
        val photos = result.perStep.getValue(AiStep.PhotoDescription)
        assertEquals(2, photos.calls)
        assertEquals(300, photos.inputTokens)
        assertEquals(50, photos.outputTokens)
        assertEquals(0.003, photos.actualUsd, 1e-12)
        assertEquals(result.inputTokens, result.perStep.values.sumOf { it.inputTokens })
        assertEquals(result.outputTokens, result.perStep.values.sumOf { it.outputTokens })
        assertEquals(result.actualUsd, result.perStep.values.sumOf { it.actualUsd }, 1e-12)
        assertEquals(result.cloudUsd, result.perStep.values.sumOf { it.cloudUsd }, 1e-12)
    }

    @Test
    fun cacheHitsAddNoCallsTokensOrCharges() {
        val call = usage(AiStep.Summary, 1_000, 200, 0.002)
        val result = costComparison(listOf(call, call.copy(cached = true)))

        assertEquals(1, result.calls)
        assertEquals(1, result.cacheHits)
        assertEquals(1_000, result.inputTokens)
        assertEquals(200, result.outputTokens)
        assertEquals(0.002, result.actualUsd, 1e-12)
        assertEquals(0.002, result.cloudUsd, 1e-12)
        assertEquals(1, result.perStep.getValue(AiStep.Summary).cacheHits)

        val cachedOnly = costComparison(listOf(call.copy(cached = true)))
        assertEquals(0, cachedOnly.calls)
        assertEquals(1, cachedOnly.cacheHits)
        assertEquals(0, cachedOnly.inputTokens)
        assertEquals(0, cachedOnly.outputTokens)
        assertEquals(0.0, cachedOnly.actualUsd)
        assertEquals(0.0, cachedOnly.cloudUsd)
    }

    @Test
    fun clinicHasNoTokenChargesButStillHasACloudComparison() {
        val clinic = AiConfig.clinic()
        val result = costComparison(listOf(
            usage(AiStep.PhotoDescription, 1_000, 200).copy(provider = clinic.providerName, model = clinic.small.id),
            usage(AiStep.Summary, 1_000, 200).copy(provider = clinic.providerName, model = clinic.big.id),
        ))

        assertEquals(0.0, result.actualUsd)
        assertEquals(0.0, result.clinicUsd)
        assertEquals(0.00218, result.cloudUsd, 1e-12)
        result.perStep.values.forEach { assertEquals(0.0, it.clinicUsd) }
    }

    @Test
    fun cloudUsesSmallModelForPhotosAndBigModelForOtherSteps() {
        val result = costComparison(AiStep.entries.map { usage(it, 1_000_000, 1_000_000) })

        assertEquals(0.50, result.perStep.getValue(AiStep.PhotoDescription).cloudUsd, 1e-12)
        assertEquals(6.00, result.perStep.getValue(AiStep.Summary).cloudUsd, 1e-12)
        assertEquals(6.00, result.perStep.getValue(AiStep.DocumentExtraction).cloudUsd, 1e-12)
        assertEquals(12.50, result.cloudUsd, 1e-12)
    }

    @Test
    fun cloudUsesTheSuppliedPriceTableRatherThanTheRecordedModel() {
        val defaults = AiConfig.cloud()
        val cloud = defaults.copy(prices = mapOf(
            defaults.small.id to ModelPrice(2.0, 3.0),
            defaults.big.id to ModelPrice(7.0, 11.0),
        ))
        val result = costComparison(AiStep.entries.map { usage(it, 1_000_000, 2_000_000) }, cloud)

        assertEquals(8.0, result.perStep.getValue(AiStep.PhotoDescription).cloudUsd)
        assertEquals(29.0, result.perStep.getValue(AiStep.Summary).cloudUsd)
        assertEquals(29.0, result.perStep.getValue(AiStep.DocumentExtraction).cloudUsd)
        assertEquals(66.0, result.cloudUsd)
    }

    @Test
    fun emptyUsageHasZeroTotalsAndNoSteps() {
        assertEquals(CostComparison(0, 0, 0, 0, 0.0, 0.0, perStep = emptyMap()), costComparison(emptyList()))
    }

    @Test
    fun comparisonIncludingStepKeysCanBeSerialized() {
        val result = costComparison(listOf(usage(AiStep.Summary, 100, 20, 0.001)))
        assertEquals(result, EasyPaperJson.decodeFromString(CostComparison.serializer(), EasyPaperJson.encodeToString(result)))
    }

    private fun usage(step: AiStep, input: Int, output: Int, cost: Double = 0.0) = AiUsage(
        step = step,
        provider = "Ollama (clinic module)",
        model = "local-model",
        inputTokens = input,
        outputTokens = output,
        costUsd = cost,
        cached = false,
        millis = 10,
    )
}
