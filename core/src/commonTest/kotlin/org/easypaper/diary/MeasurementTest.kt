package org.easypaper.diary

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.demo.Demo
import org.easypaper.model.Entry
import org.easypaper.model.Measurement
import org.easypaper.model.PatientRecord
import org.easypaper.report.factDigest
import org.easypaper.report.toCsv
import org.easypaper.report.toFhirBundle
import org.easypaper.report.toLocalizedText
import org.easypaper.report.toText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #52: the person's own measurements (weight and anything else), without a doctor asking. */
class MeasurementTest {
    private val own = ownWidgetId(EntryCategory.MEASUREMENTS)
    private val day0 = Demo.record.entries.minOf { it.at }

    @Test
    fun ownMeasurementsAreCheckedAsTypoGuardsOnly() {
        val diary = Diary()
        assertEquals(emptyList(), diary.check(Entry("w", own, 1, Measurement.weight(72.5))))
        assertEquals(emptyList(), diary.check(Entry("s", own, 1, Measurement("Blood sugar", 5.4, "mmol/L"))))
        assertEquals(listOf(EntryProblem.MEASUREMENT_OUT_OF_RANGE), diary.check(Entry("x", own, 1, Measurement.weight(725.0))))
        assertEquals(listOf(EntryProblem.MEASUREMENT_OUT_OF_RANGE), diary.check(Entry("n", own, 1, Measurement("Waist", Double.NaN, "cm"))))
        assertEquals(listOf(EntryProblem.EMPTY_MEASUREMENT_NAME), diary.check(Entry("e", own, 1, Measurement(" ", 80.0, "cm"))))
    }

    @Test
    fun theDoctorSeesThemInTheFactsTheReportAndTheExport() {
        val diary = Diary(listOf(
            Entry("w1", own, day0, Measurement.weight(72.0)),
            Entry("w2", own, day0 + 86_400_000L, Measurement.weight(71.5)),
            Entry("s1", own, day0, Measurement("Waist", 84.0, "cm")),
        ))
        val day = kotlin.time.Instant.fromEpochMilliseconds(day0).toString().take(10)
        val next = kotlin.time.Instant.fromEpochMilliseconds(day0 + 86_400_000L).toString().take(10)
        val record: PatientRecord = diary.selection(setOf(EntryCategory.MEASUREMENTS), day, next)
        assertEquals(listOf("measurements"), record.request.widgets.map { it.id })
        assertEquals(3, record.entries.size)

        val digest = record.factDigest()
        assertEquals(listOf("Weight" to listOf("72", "71.5"), "Waist" to listOf("84")), digest.measurements.map { it.name to it.values.map { v -> v.value } })
        assertTrue("Weight (kg): $day 72 [w1]; $next 71.5 [w2]" in digest.toText())
        assertTrue("Weight (kg): $day 72 [w1]" in digest.toLocalizedText("de"), "names and refs stay verbatim")

        val observations = record.toFhirBundle()["entry"]!!.jsonArray.map { it.jsonObject["resource"]!!.jsonObject }
            .filter { it["resourceType"]?.jsonPrimitive?.content == "Observation" }
        val weight = observations.first { it["id"]?.jsonPrimitive?.content == "w1" }
        assertTrue("29463-7" in weight["code"].toString())
        assertEquals("kg", weight["valueQuantity"]!!.jsonObject["code"]!!.jsonPrimitive.content)

        val csv = record.toCsv()
        assertTrue(csv.lines().first().endsWith("measurement,value,unit,loinc"))
        assertTrue(csv.lines().any { it.contains("measurement") && it.contains("Waist") && it.contains("84.0") && it.contains("cm") })
    }
}
