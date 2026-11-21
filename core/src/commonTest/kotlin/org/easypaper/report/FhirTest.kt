package org.easypaper.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FhirTest {
    private val record = PatientRecord(
        CareRequest(
            id = "req-1", createdAt = 0, doctorName = "Dr", complaint = "Headache", startDate = "2026-11-17", days = 2, locale = "en",
            widgets = listOf(
                QuestionnaireWidget(
                    "q", "Daily questions",
                    questions = listOf(
                        Question("q1", "Pain today?", QuestionKind.YES_NO),
                        Question("q2", "Intensity", QuestionKind.SCALE),
                        Question("q3", "Where?", QuestionKind.CHOICE, listOf("forehead", "temples")),
                        Question("q4", "Anything else?", QuestionKind.TEXT),
                    ),
                ),
                BloodPressureWidget("bp", "Blood pressure"),
                MedicationsWidget("m", "Medications"),
            ),
        ),
        listOf(
            Entry("e1", "bp", 1, BloodPressure(130, 85)),
            Entry("e2", "q", 2, Answers(mapOf("q1" to "yes", "q2" to "7", "q3" to "temples", "q4" to "after lunch"))),
            Entry("e3", "q", 3, Answers(mapOf("q1" to "no", "q2" to "n/a"))),
            Entry("e4", "m", 4, Medication("X", source = MedicationSource.MANUAL, confirmed = true)),
            Entry("e5", "old", 5, Water(100)), // widget not in the request
        ),
    )
    private val resources = record.toFhirBundle().getValue("entry").jsonArray.map { it.jsonObject.getValue("resource").jsonObject }
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun ref(r: JsonObject) = r.str("resourceType") + "/" + r.str("id")

    @Test
    fun compositionListsEveryEntryOnce() {
        val composition = resources.first()
        assertEquals("Composition", composition.str("resourceType"))
        assertEquals("preliminary", composition.str("status"))
        val sections = composition.getValue("section").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Daily questions", "Blood pressure", "Medications", "Other"), sections.map { it.str("title") })
        val refs = sections.flatMap { s -> s.getValue("entry").jsonArray.map { it.jsonObject.str("reference") } }
        val entryResources = resources.filter { it.str("id") in record.entries.map { e -> e.id } }
        assertEquals(entryResources.map(::ref).sorted(), refs.sorted()) // each exactly once
        assertEquals(listOf("QuestionnaireResponse/e2", "QuestionnaireResponse/e3"), sections[0].getValue("entry").jsonArray.map { it.jsonObject.str("reference") })
    }

    @Test
    fun questionnaireDescribesTheDoctorsQuestions() {
        val q = resources.single { it.str("resourceType") == "Questionnaire" }
        assertEquals("q", q.str("id"))
        val items = q.getValue("item").jsonArray.map { it.jsonObject }
        assertEquals(listOf("boolean", "integer", "choice", "string"), items.map { it.str("type") })
        assertEquals(listOf("forehead", "temples"), items[2].getValue("answerOption").jsonArray.map { it.jsonObject.getValue("valueCoding").jsonObject.str("code") })
        val responses = resources.filter { it.str("resourceType") == "QuestionnaireResponse" }
        assertTrue(responses.all { it.str("questionnaire") == ref(q) })
    }

    @Test
    fun answersAreTypedByQuestionKind() {
        fun answers(id: String) = resources.single { it.str("id") == id }.getValue("item").jsonArray.associate {
            val item = it.jsonObject
            item.str("linkId") to item.getValue("answer").jsonArray.single().jsonObject
        }
        val a = answers("e2")
        assertEquals(true, a.getValue("q1").getValue("valueBoolean").jsonPrimitive.boolean)
        assertEquals(7, a.getValue("q2").getValue("valueInteger").jsonPrimitive.int)
        assertEquals("temples", a.getValue("q3").getValue("valueCoding").jsonObject.str("code"))
        assertEquals("after lunch", a.getValue("q4").str("valueString"))
        val b = answers("e3")
        assertEquals(false, b.getValue("q1").getValue("valueBoolean").jsonPrimitive.boolean)
        assertEquals("n/a", b.getValue("q2").str("valueString")) // does not fit the kind: kept as text
        assertEquals("Pain today?", resources.single { it.str("id") == "e2" }.getValue("item").jsonArray[0].jsonObject.str("text"))
    }
}
