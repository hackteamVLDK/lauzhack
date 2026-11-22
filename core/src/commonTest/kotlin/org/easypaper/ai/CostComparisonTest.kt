package org.easypaper.ai

import kotlinx.serialization.encodeToString
import org.easypaper.model.EasyPaperJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(0.50, result.perStep.getValue(AiStep.MedicineLabel).cloudUsd, 1e-12)
        assertEquals(13.00, result.cloudUsd, 1e-12)
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
        assertEquals(8.0, result.perStep.getValue(AiStep.MedicineLabel).cloudUsd)
        assertEquals(74.0, result.cloudUsd)
    }

    @Test
    fun emptyUsageHasZeroTotalsAndNoSteps() {
        assertEquals(CostComparison(0, 0, 0, 0, 0.0, 0.0, perStep = emptyMap()), costComparison(emptyList()))
    }

    @Test
    fun comparisonIncludingStepKeysCanBeSerialized() {
        val result = costComparison(listOf(usage(AiStep.Summary, 100, 20, 0.001).copy(reportId = "req-1", estimated = true)))
        assertEquals(result, EasyPaperJson.decodeFromString(CostComparison.serializer(), EasyPaperJson.encodeToString(result)))
    }

    @Test
    fun reportComparisonExcludesOtherReportsAndUnassignedCalls() {
        val photo = usage(AiStep.PhotoDescription, 100, 20, 0.001).copy(reportId = "req-1", estimated = true)
        val summary = usage(AiStep.Summary, 500, 100, 0.004).copy(reportId = "req-1")
        val other = summary.copy(reportId = "req-2", inputTokens = 300, costUsd = 0.003)
        val document = usage(AiStep.DocumentExtraction, 700, 50, 0.003)
        val cached = summary.copy(cached = true)
        val all = listOf(photo, summary, other, cached, document)

        val report = costComparison(all, reportId = "req-1")
        assertEquals(costComparison(listOf(photo, summary, cached)), report)
        assertEquals(2, report.calls)
        assertEquals(1, report.cacheHits)
        assertEquals(600, report.inputTokens)
        assertEquals(120, report.outputTokens)
        assertEquals(0.005, report.actualUsd, 1e-12)
        assertTrue(report.hasEstimates)
        assertTrue(report.perStep.getValue(AiStep.PhotoDescription).hasEstimates)
        assertFalse(report.perStep.getValue(AiStep.Summary).hasEstimates)
        assertFalse(costComparison(all, reportId = "req-2").hasEstimates)
        assertEquals(costComparison(emptyList()), costComparison(all, reportId = "missing"))

        val session = costComparison(all)
        assertEquals(4, session.calls)
        assertEquals(1, session.cacheHits)
        assertEquals(1_600, session.inputTokens)
        assertEquals(270, session.outputTokens)
        assertEquals(0.011, session.actualUsd, 1e-12)
        assertTrue(session.hasEstimates)
    }

    @Test
    fun latestReportUsesLastAssignedIdAndIncludesItsEarlierCalls() {
        val first = usage(AiStep.Summary, 100, 20).copy(reportId = "req-1")
        val second = first.copy(reportId = "req-2")
        val cachedFirst = first.copy(cached = true)
        val document = first.copy(step = AiStep.DocumentExtraction, reportId = null)

        assertEquals(listOf(first, cachedFirst), latestReportUsage(listOf(first, second, cachedFirst, document)))
        assertEquals(listOf(second), latestReportUsage(listOf(first, second, document)))
        assertTrue(latestReportUsage(listOf(document)).isEmpty())
        assertTrue(latestReportUsage(emptyList()).isEmpty())
    }

    @Test
    fun cachedEstimatesDoNotMakeZeroTokenChargesEstimated() {
        val cached = usage(AiStep.Summary, 100, 20, 0.001).copy(cached = true, estimated = true)
        val result = costComparison(listOf(cached))
        assertFalse(result.hasEstimates)
        assertFalse(result.perStep.getValue(AiStep.Summary).hasEstimates)
        assertEquals(0.0, result.actualUsd)
    }

    @Test
    fun usageSupportsOldJsonAndRoundTripsReportAndEstimate() {
        val oldJson = """{"step":"Summary","provider":"test","model":"test","inputTokens":1,"outputTokens":2,"costUsd":0.0,"cached":false,"millis":0}"""
        val old = EasyPaperJson.decodeFromString(AiUsage.serializer(), oldJson)
        assertNull(old.reportId)
        assertFalse(old.estimated)
        val current = old.copy(reportId = "req-1", estimated = true)
        assertEquals(current, EasyPaperJson.decodeFromString(AiUsage.serializer(), EasyPaperJson.encodeToString(current)))
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
