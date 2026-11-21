package org.easypaper.model

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaTest {
    private val request = CareRequest(
        id = "req-1",
        createdAt = 1_795_000_000_000,
        doctorName = "Dr. Test",
        complaint = "Headache",
        startDate = "2026-11-21",
        days = 5,
        locale = "ru",
        widgets = listOf(
            QuestionnaireWidget(
                id = "q", title = "Questions",
                questions = listOf(
                    Question("q1", "Pain today?", QuestionKind.YES_NO),
                    Question("q2", "How strong?", QuestionKind.SCALE, listOf("none", "worst")),
                    Question("q3", "Where?", QuestionKind.CHOICE, listOf("front", "back")),
                    Question("q4", "Anything else?", QuestionKind.TEXT),
                ),
            ),
            BloodPressureWidget("bp", "Pressure"),
            WaterWidget("w", "Water", targetMl = 2000),
            FoodWidget("f", "Food", photo = false),
            MedicationsWidget("m", "Medications"),
            DiaryWidget("d", "Diary"),
            DoctorQuestionsWidget("dq", "Questions to the doctor"),
        ),
    )

    private val record = PatientRecord(
        request,
        listOf(
            Entry("e1", "q", 1, Answers(mapOf("q1" to "yes", "q2" to "7", "q3" to "front", "q4" to "no"))),
            Entry("e2", "bp", 2, BloodPressure(130, 85, 72)),
            Entry("e3", "w", 3, Water(250)),
            Entry("e4", "f", 4, Food(text = "soup", photoRef = "photo-1")),
            Entry("e5", "m", 5, Medication("Ibuprofen", "04600000000000", "200 mg", MedicationSource.SCAN, true)),
            Entry("e6", "d", 6, DiaryNote("slept badly")),
            Entry("e7", "dq", 7, DoctorQuestion("Can I drink coffee?")),
        ),
    )

    @Test
    fun roundTripEveryWidgetAndPayload() {
        val json = EasyPaperJson.encodeToString(PatientRecord.serializer(), record)
        assertEquals(record, EasyPaperJson.decodeFromString(PatientRecord.serializer(), json))
        for (type in listOf(
            "questionnaire", "blood_pressure", "water", "food", "medications", "diary", "doctor_questions",
            "answers", "medication", "diary_note", "doctor_question",
        )) assertTrue("\"type\":\"$type\"" in json, "missing type $type in $json")
        assertTrue("\"schemaVersion\":1" in json)
        assertTrue("\"kind\":\"yesno\"" in json && "\"source\":\"scan\"" in json)
    }

    @Test
    fun unknownWidgetTypeFails() {
        val json = """{"id":"x","createdAt":0,"doctorName":"d","complaint":"c","startDate":"2026-11-21","days":1,
            "locale":"en","widgets":[{"type":"teleport","id":"t","title":"T"}]}"""
        assertFailsWith<SerializationException> { EasyPaperJson.decodeFromString(CareRequest.serializer(), json) }
    }

    @Test
    fun extraFieldsAreIgnored() {
        val json = """{"id":"x","createdAt":0,"doctorName":"d","complaint":"c","startDate":"2026-11-21","days":1,
            "locale":"en","future":42,"widgets":[{"type":"water","id":"w","title":"W","color":"blue"}]}"""
        val decoded = EasyPaperJson.decodeFromString(CareRequest.serializer(), json)
        assertEquals(listOf<WidgetSpec>(WaterWidget("w", "W")), decoded.widgets)
        assertEquals(SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun newerSchemaVersionFails() {
        val json = EasyPaperJson.encodeToString(CareRequest.serializer(), request)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":${SCHEMA_VERSION + 1}")
        assertFailsWith<IllegalArgumentException> { EasyPaperJson.decodeFromString(CareRequest.serializer(), json) }
    }

    @Test
    fun duplicateWidgetIdsFail() {
        assertFailsWith<IllegalArgumentException> {
            request.copy(widgets = listOf(DiaryWidget("a", "A"), WaterWidget("a", "B")))
        }
    }
}
