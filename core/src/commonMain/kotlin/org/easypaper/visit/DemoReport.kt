package org.easypaper.visit

import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.Entry
import org.easypaper.model.EntryPayload
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.PatientRecord
import org.easypaper.model.Question
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import org.easypaper.model.WaterWidget
import org.easypaper.report.Report
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Time zone of the demo patient (Lausanne, CET). */
const val DEMO_UTC_OFFSET_MINUTES = 60

/** The demo doctor's request: five days of morning headaches. */
fun demoCareRequest(): CareRequest = CareRequest(
    id = "demo-headache-2026-11",
    createdAt = demoTime(day = 0, hour = 16, minute = 40) - 86_400_000L,
    doctorName = "Dr. Anna Keller",
    complaint = "Headache in the morning for two weeks",
    startDate = "2026-11-22",
    days = 5,
    locale = "en",
    widgets = listOf(
        QuestionnaireWidget(
            id = "morning", title = "Morning headache",
            questions = listOf(
                Question("woke", "Did you wake up with a headache?", QuestionKind.YES_NO),
                Question("strength", "How strong was it?", QuestionKind.SCALE, listOf("none", "worst")),
                Question("where", "Where did it hurt?", QuestionKind.CHOICE, listOf("forehead", "temples", "back of the head")),
            ),
        ),
        BloodPressureWidget(id = "bp", title = "Blood pressure", timesPerDay = 2),
        WaterWidget(id = "water", title = "Water"),
        FoodWidget(id = "food", title = "Meals", required = false),
        MedicationsWidget(id = "meds", title = "Medicines you take"),
        DiaryWidget(id = "diary", title = "How you feel"),
        DoctorQuestionsWidget(id = "ask", title = "Questions for the doctor"),
    ),
)

/** The demo record with a summary as the AI pipeline (#7) should write it: one fact per line, links to entries. */
fun demoReport(): Report {
    val entries = mutableListOf<Entry>()
    fun add(widget: String, day: Int, hour: Int, minute: Int, payload: EntryPayload): String {
        val id = "e${entries.size + 1}"
        entries += Entry(id, widget, demoTime(day, hour, minute), payload)
        return id
    }

    // Day by day, as the patient filled it in. Morning higher than evening on purpose: facts, no judgement.
    val morningBp = listOf(142 to 91, 138 to 88, 145 to 92, 128 to 82, 140 to 90)
    val eveningBp = listOf(124 to 80, 122 to 78, 126 to 81, 121 to 79, 125 to 80)
    val pulse = listOf(78, 74, 80, 70, 76)
    val strength = listOf("6", "5", "7", "0", "6")
    val woke = listOf("yes", "yes", "yes", "no", "yes")
    val water = listOf(listOf(250, 500, 250), listOf(500, 250, 500), listOf(250, 250, 500), listOf(500, 500, 500), listOf(250, 500))
    val bpIds = mutableListOf<String>()
    val morningIds = mutableListOf<String>()
    val waterIds = mutableListOf<String>()
    for (day in 0 until 5) {
        morningIds += add("morning", day, 7, 5, Answers(buildMap {
            put("woke", woke[day]); put("strength", strength[day]); if (woke[day] == "yes") put("where", "temples")
        }))
        bpIds += add("bp", day, 7, 12, BloodPressure(morningBp[day].first, morningBp[day].second, pulse[day]))
        water[day].forEachIndexed { i, ml -> waterIds += add("water", day, 9 + i * 4, 30, Water(ml)) }
        bpIds += add("bp", day, 20, 40, BloodPressure(eveningBp[day].first, eveningBp[day].second, pulse[day] - 6))
    }
    val ibuprofen = listOf(0, 2, 4).map { day ->
        add("meds", day, 7, 30, Medication("DOLO SPEDIFEN forte Filmtabl 400 mg 10 Stk", "7680567200025", "400 mg", MedicationSource.SCAN, confirmed = true))
    }
    val magnesium = add("meds", 0, 21, 0, Medication("Magnesium", dose = "300 mg in the evening", source = MedicationSource.MANUAL, confirmed = true))
    val paracetamol = add("meds", 2, 13, 15, Medication("ACETALGIN Filmtabl 1 g 16 Stk", "7680623550019", "1 g", MedicationSource.SCAN, confirmed = false))
    val coffee = add("food", 1, 7, 45, Food("Two cups of coffee, toast with jam", photoRef = "photo/breakfast-2"))
    val dinner = add("food", 2, 21, 30, Food("Pizza and red wine, late dinner"))
    val sleep = add("diary", 1, 22, 10, DiaryNote("Woke up at 5 again. Pressing pain in both temples, eases after coffee."))
    val screen = add("diary", 3, 19, 0, DiaryNote("Day off, no laptop. No headache this morning."))
    val q1 = add("ask", 2, 22, 0, DoctorQuestion("Can the headaches come from working at the screen all day?"))
    val q2 = add("ask", 4, 8, 0, DoctorQuestion("Is it fine to take ibuprofen three times a week?"))

    val summary = listOf(
        "Woke up with a headache on 4 of 5 days, strength 5–7 of 10, in the temples ${refs(morningIds.filterIndexed { i, _ -> i != 3 })}.",
        "No headache on day 4, a day off without the laptop ${refs(listOf(morningIds[3], screen))}.",
        "Morning blood pressure 128–145/82–92 mmHg, evening 121–126/78–81 mmHg ${refs(bpIds)}.",
        "Ibuprofen 400 mg taken on 3 of 5 mornings ${refs(ibuprofen)}; paracetamol 1 g scanned once, not confirmed by the patient ${refs(listOf(paracetamol))}.",
        "Magnesium 300 mg in the evening ${refs(listOf(magnesium))}.",
        "Water about 1.1 L per day ${refs(waterIds)}.",
        "Coffee in the morning, late dinner with wine on day 3 ${refs(listOf(coffee, dinner))}; wakes up around 5 a.m. ${refs(listOf(sleep))}.",
        "Asks about screen work and about taking ibuprofen three times a week ${refs(listOf(q1, q2))}.",
    ).joinToString("\n")

    return Report(
        id = "demo-report-1",
        createdAt = demoTime(day = 5, hour = 8, minute = 50),
        record = PatientRecord(demoCareRequest(), entries),
        summary = summary,
    )
}

private fun refs(ids: List<String>) = ids.joinToString(", ", "[", "]")

/** Local CET time on demo day [day] (0 = 2026-11-22) as epoch millis UTC. */
@OptIn(ExperimentalTime::class)
private fun demoTime(day: Int, hour: Int, minute: Int): Long =
    Instant.parse("2026-11-22T00:00:00Z").toEpochMilliseconds() +
        ((day * 24L + hour) * 60 + minute - DEMO_UTC_OFFSET_MINUTES) * 60_000L
