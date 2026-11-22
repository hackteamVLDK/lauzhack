package org.easypaper.ui.patient.medications

import org.easypaper.scan.sideEffectsFor
import org.easypaper.scan.SideEffect
import org.easypaper.scan.MedicationDb
import org.easypaper.model.SideEffectCheck
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.easypaper.model.Entry
import org.easypaper.diary.EntryProblem
import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource
import org.easypaper.model.PatientRecord
import org.easypaper.scan.PackageMatch
import org.easypaper.scan.toMedication
import org.easypaper.storage.PatientRepository
import org.easypaper.ui.patient.newEntryId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One medicine on the patient's list (task 31). Every confirmed entry is one intake the patient recorded, as the
 * report reads it; an unconfirmed entry is a scan the patient has not checked yet (a draft).
 */
data class MedicineItem(
    val name: String,
    val dose: String?,
    val code: String?,
    val source: MedicationSource,
    /** Entries still waiting for the patient's check; removed once confirmed. */
    val draftIds: List<String>,
    /** Times of today's intakes, oldest first. */
    val takenToday: List<Long>,
    val lastTaken: Long?,
) {
    val isDraft: Boolean get() = draftIds.isNotEmpty() && lastTaken == null
}

/** The patient's medicines in [widgetId]: one item per package code (or name), drafts first, then by name. */
@OptIn(ExperimentalTime::class)
fun medicinesFor(record: PatientRecord, widgetId: String, today: LocalDate, timeZone: TimeZone): List<MedicineItem> =
    record.entries.filter { it.widgetId == widgetId && it.payload is Medication }
        .sortedBy { it.at }
        .groupBy { (it.payload as Medication).let { m -> m.code ?: m.name.trim().lowercase() } }
        .values.map { entries ->
            val confirmed = entries.filter { (it.payload as Medication).confirmed }
            val shown = (confirmed.lastOrNull() ?: entries.last()).payload as Medication
            MedicineItem(
                name = shown.name,
                dose = shown.dose,
                code = shown.code,
                source = shown.source,
                draftIds = entries.filterNot { (it.payload as Medication).confirmed }.map { it.id },
                takenToday = confirmed.filter { Instant.fromEpochMilliseconds(it.at).toLocalDateTime(timeZone).date == today }.map { it.at },
                lastTaken = confirmed.lastOrNull()?.at,
            )
        }
        .sortedWith(compareByDescending<MedicineItem> { it.isDraft }.thenBy { it.name.lowercase() })

/** What a package photo offers to choose from; the patient picks one or types the name (unknown or unreadable box). */
fun scanChoices(match: PackageMatch, locale: String): List<Medication> = match.candidates.map { it.toMedication(locale) }

/** The patient's own words for dose and frequency win; the strength printed on the package is the default. */
fun Medication.confirmedWith(doseAndFrequency: String): Medication =
    copy(dose = doseAndFrequency.trim().ifEmpty { dose }, confirmed = true)

fun typedMedication(name: String, doseAndFrequency: String, code: String? = null): Medication =
    Medication(name.trim(), code, doseAndFrequency.trim().ifEmpty { null }, MedicationSource.MANUAL, confirmed = true)

/** Saves a medicine entry now; returns the input problems (empty = saved). */
suspend fun PatientRepository.addMedication(requestId: String, widgetId: String, medication: Medication, at: Long): List<EntryProblem> =
    add(requestId, Entry(newEntryId(), widgetId, at, medication))

/** The confirmed entry is saved before the drafts are removed, so a failure never loses what was scanned. */
suspend fun PatientRepository.confirm(requestId: String, widgetId: String, item: MedicineItem, doseAndFrequency: String, at: Long): List<EntryProblem> {
    val medication = Medication(item.name, item.code, item.dose, item.source, confirmed = false).confirmedWith(doseAndFrequency)
    val problems = addMedication(requestId, widgetId, medication, at)
    if (problems.isEmpty()) item.draftIds.forEach { removeEntry(requestId, it) }
    return problems
}

/** "Taken now": one more intake of a confirmed medicine. */
suspend fun PatientRepository.takeNow(requestId: String, widgetId: String, item: MedicineItem, at: Long): List<EntryProblem> =
    addMedication(requestId, widgetId, Medication(item.name, item.code, item.dose, item.source, confirmed = true), at)

/** A confirmed medicine with the side effects its leaflet lists as common, and whether they were answered today (#53). */
data class SideEffectQuestions(val item: MedicineItem, val effects: List<SideEffect>, val answeredToday: Boolean)

@OptIn(ExperimentalTime::class)
fun sideEffectQuestions(
    record: PatientRecord, widgetId: String, items: List<MedicineItem>, db: MedicationDb, today: LocalDate, timeZone: TimeZone,
): List<SideEffectQuestions> {
    val answered = record.entries.filter { it.widgetId == widgetId && it.payload is SideEffectCheck }
        .filter { Instant.fromEpochMilliseconds(it.at).toLocalDateTime(timeZone).date == today }
        .map { (it.payload as SideEffectCheck).let { c -> c.gtin ?: c.name() } }.toSet()
    // the package code identifies the medicine; its display name can change with the language of the list
    return items.filter { !it.isDraft }.mapNotNull { item ->
        db.sideEffectsFor(item.code).takeIf { it.isNotEmpty() }?.let { SideEffectQuestions(item, it, (item.code ?: item.name) in answered) }
    }
}

private fun SideEffectCheck.name() = medication

/** Saves what the person noticed today: side effect key -> "yes"/"no". */
suspend fun PatientRepository.saveSideEffects(requestId: String, widgetId: String, item: MedicineItem, answers: Map<String, Boolean>, at: Long) =
    add(requestId, Entry(newEntryId(), widgetId, at, SideEffectCheck(item.name, item.code, answers.mapValues { if (it.value) "yes" else "no" })))
