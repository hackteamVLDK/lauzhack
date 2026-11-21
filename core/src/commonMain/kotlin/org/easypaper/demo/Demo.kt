package org.easypaper.demo

import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.PatientRecord
import org.easypaper.model.Question
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import org.easypaper.model.WaterWidget
import org.easypaper.report.Report
import org.easypaper.model.EntryPayload
import org.easypaper.scan.MedicationDb
import org.easypaper.scan.parseScanCode
import org.easypaper.scan.toMedication

/**
 * Fixed demo data for screens (#10), the presentation (#11) and tests: a 5-day headache diary from 2026-11-17.
 * No randomness: the same values every time. Medications come from the local list (#6).
 */
object Demo {
    const val START = "2026-11-17"
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L
    private const val START_MS = 1_794_873_600_000L // 2026-11-17T00:00:00Z

    val request = CareRequest(
        id = "demo-headache-1",
        createdAt = START_MS - DAY,
        doctorName = "Dr. Demo",
        complaint = "Headache",
        startDate = START,
        days = 5,
        locale = "en",
        widgets = listOf(
            QuestionnaireWidget(
                "q", "Daily questions",
                questions = listOf(
                    Question("q1", "Headache today?", QuestionKind.YES_NO),
                    Question("q2", "Headache intensity", QuestionKind.SCALE, listOf("none", "worst")),
                    Question("q3", "Where?", QuestionKind.CHOICE, listOf("forehead", "temples", "back of head")),
                ),
            ),
            BloodPressureWidget("bp", "Blood pressure"),
            WaterWidget("w", "Water", targetMl = 1500),
            FoodWidget("f", "Food", required = false),
            MedicationsWidget("m", "Medications"),
            DiaryWidget("d", "Diary"),
            DoctorQuestionsWidget("dq", "Questions to the doctor"),
        ),
    )

    /** Per day: morning and evening (systolic, diastolic, pulse). */
    val bloodPressure = listOf(
        listOf(Triple(128, 84, 72), Triple(136, 88, 76)),
        listOf(Triple(131, 85, 70), Triple(142, 91, 80)),
        listOf(Triple(125, 82, 68), Triple(138, 89, 75)),
        listOf(Triple(129, 83, 71), Triple(140, 90, 78)),
        listOf(Triple(127, 81, 69), Triple(134, 87, 74)),
    )
    val water = listOf(listOf(250, 500, 250), listOf(300, 500, 400), listOf(250, 250), listOf(500, 750), listOf(1000))
    val answers = listOf(
        mapOf("q1" to "yes", "q2" to "6", "q3" to "temples"),
        mapOf("q1" to "yes", "q2" to "7", "q3" to "temples"),
        mapOf("q1" to "no", "q2" to "0"),
        mapOf("q1" to "yes", "q2" to "5", "q3" to "forehead"),
        mapOf("q1" to "yes", "q2" to "4", "q3" to "temples"),
    )

    val record: PatientRecord = run {
        val db = MedicationDb()
        val dolo = db.find(parseScanCode("7680567200025")!!)!!.toMedication("en").copy(confirmed = true)
        val panto = db.find(parseScanCode("7680587170018")!!)!!.toMedication("en") // scanned, not confirmed yet
        val entries = mutableListOf<Entry>()
        fun add(widget: String, day: Int, hour: Int, payload: EntryPayload) {
            val id = "e" + (entries.size + 1).toString().padStart(2, '0')
            entries += Entry(id, widget, START_MS + day * DAY + hour * HOUR, payload)
        }
        for (day in 0 until 5) {
            bloodPressure[day].forEachIndexed { i, (s, d, p) -> add("bp", day, if (i == 0) 8 else 20, BloodPressure(s, d, p)) }
            water[day].forEachIndexed { i, ml -> add("w", day, 10 + 3 * i, Water(ml)) }
            add("q", day, 21, Answers(answers[day]))
        }
        for (day in listOf(0, 1, 3)) add("m", day, 9, dolo)
        add("m", 4, 9, panto)
        add("f", 1, 13, Food("Coffee 3 cups, pasta", "demo-photo-1"))
        add("f", 3, 13, Food("Chocolate"))
        add("d", 1, 22, DiaryNote("Slept 5 hours"))
        add("d", 3, 22, DiaryNote("Long day at the screen"))
        add("dq", 4, 12, DoctorQuestion("Can I take ibuprofen together with coffee?"))
        PatientRecord(request, entries)
    }

    val report = Report(id = "demo-report-1", createdAt = START_MS + 5 * DAY, record = record)
}
