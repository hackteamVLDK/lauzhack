package org.easypaper.ui.patient.v2

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.easypaper.model.*
import org.easypaper.storage.PatientRepository
import org.easypaper.ui.components.category
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.theme.Category

const val PERSONAL_REQUEST_ID = "personal"
val defaultPinned = setOf(Category.BloodPressure, Category.Water, Category.Food, Category.Medicines)

/** Temporary adapter until entries can exist independently of requests (#47). */
fun personalRequest(today: LocalDate, locale: String, now: Long, titles: Map<Category, String>): CareRequest {
    fun title(c: Category) = titles.getValue(c)
    return CareRequest(id = PERSONAL_REQUEST_ID, createdAt = now, doctorName = "", complaint = "",
        startDate = today.toString(), days = 365, locale = locale, widgets = listOf(
            BloodPressureWidget("personal-pressure", title(Category.BloodPressure), required = false),
            WaterWidget("personal-water", title(Category.Water), required = false),
            FoodWidget("personal-food", title(Category.Food), required = false),
            MedicationsWidget("personal-medicines", title(Category.Medicines), required = false),
            QuestionnaireWidget("personal-symptoms", title(Category.Questionnaire), required = false,
                questions = listOf(Question("symptoms", title(Category.Questionnaire), QuestionKind.TEXT))),
            DiaryWidget("personal-diary", title(Category.Diary), required = false),
            DoctorQuestionsWidget("personal-questions", title(Category.DoctorQuestion), required = false),
        ))
}

suspend fun PatientRepository.ensurePersonal(request: CareRequest) {
    require(request.id == PERSONAL_REQUEST_ID)
    // start() checks the store under its mutex, including during the first refresh.
    start(request)
}

data class Period(val from: LocalDate, val to: LocalDate) {
    init { require(from <= to) }
    operator fun contains(date: LocalDate) = date >= from && date <= to
}

fun recentPeriod(today: LocalDate, days: Int): Period {
    require(days > 0)
    return Period(today.plus(DatePeriod(days = 1 - days)), today)
}

fun PatientRecord.requestPeriod(): Period? = runCatching {
    val start = LocalDate.parse(request.startDate)
    Period(start, start.plus(DatePeriod(days = request.days - 1)))
}.getOrNull()

fun List<PatientRecord>.activeDoctor(today: LocalDate): PatientRecord? =
    filter { it.request.id != PERSONAL_REQUEST_ID && it.request.doctorName.isNotBlank() &&
        it.requestPeriod()?.contains(today) == true }.maxByOrNull { it.request.createdAt }

data class OwnedEntry(val record: PatientRecord, val entry: Entry)

fun categoryEntries(records: List<PatientRecord>, category: Category): List<OwnedEntry> = records.flatMap { r ->
    val ids = r.request.widgets.filter { it.category == category }.map { it.id }.toSet()
    r.entries.filter { it.widgetId in ids }.map { OwnedEntry(r, it) }
}.sortedByDescending { it.entry.at }

val Entry.photoReference: String?
    get() = when (val p = payload) { is Food -> p.photoRef; is DiaryNote -> p.photoRef; else -> null }

data class ShareSelection(val record: PatientRecord, val photoRefs: Set<String>) {
    val entryCount get() = record.entries.size
    val photoCount get() = photoRefs.size
}

/** Filter before encryption; disabling photos also removes their references from the report. */
fun selectToShare(record: PatientRecord, categories: Set<Category>, period: Period,
                  includePhotos: Boolean, timeZone: TimeZone): ShareSelection {
    val widgets = record.request.widgets.filter { it.category in categories }
    val ids = widgets.map { it.id }.toSet()
    val entries = record.entries.filter { it.widgetId in ids && it.localDate(timeZone) in period }.map { entry ->
        if (includePhotos) entry else when (val p = entry.payload) {
            is Food -> entry.copy(payload = p.copy(photoRef = null))
            is DiaryNote -> entry.copy(payload = p.copy(photoRef = null))
            else -> entry
        }
    }
    val request = record.request.copy(widgets = widgets, startDate = period.from.toString(),
        days = (period.to.toEpochDays() - period.from.toEpochDays()).toInt() + 1)
    return ShareSelection(PatientRecord(request, entries), entries.mapNotNull { it.photoReference }.toSet())
}

data class PressureRange(val systolicMin: Int, val systolicMax: Int, val diastolicMin: Int, val diastolicMax: Int)
fun pressureRange(entries: List<Entry>): PressureRange? {
    val values = entries.mapNotNull { it.payload as? BloodPressure }
    return if (values.isEmpty()) null else PressureRange(values.minOf { it.systolic }, values.maxOf { it.systolic },
        values.minOf { it.diastolic }, values.maxOf { it.diastolic })
}

data class Highlights(val pressure: PressureRange?, val averageWaterMl: Int?, val waterDays: Int)

/** Seven local calendar days, including today. Water mean includes days with no logged water. */
fun highlights(records: List<PatientRecord>, today: LocalDate, timeZone: TimeZone): Highlights {
    val period = recentPeriod(today, 7)
    val entries = records.flatMap { it.entries }.filter { it.localDate(timeZone) in period }
    val water = entries.filter { it.payload is Water }
    return Highlights(pressureRange(entries), if (water.isEmpty()) null else water.sumOf { (it.payload as Water).ml } / 7,
        water.map { it.localDate(timeZone) }.distinct().size)
}
