package org.easypaper.report

import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.DiaryNote
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.Measurement
import org.easypaper.model.Medication
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

val CSV_COLUMNS = listOf(
    "entry_id", "time_utc", "widget_id", "type", "systolic", "diastolic", "pulse", "water_ml",
    "medication", "gtin", "dose", "med_source", "confirmed", "question_id", "answer", "text", "photo_ref",
    "measurement", "value", "unit", "loinc",
)

/**
 * All entries as CSV (RFC 4180, CRLF), oldest first; one row per entry, one row per answer for questionnaires.
 * Use separator ';' for Excel with Swiss/German settings. Cells that a spreadsheet would run as a formula get a
 * leading apostrophe.
 */
@OptIn(ExperimentalTime::class)
fun PatientRecord.toCsv(separator: Char = ','): String {
    val rows = mutableListOf(CSV_COLUMNS)
    for (e in entries.sortedBy { it.at }) {
        val base = mapOf("entry_id" to e.id, "time_utc" to Instant.fromEpochMilliseconds(e.at).toString(), "widget_id" to e.widgetId)
        for (cells in e.cells()) rows += CSV_COLUMNS.map { (base + cells)[it] ?: "" }
    }
    return rows.joinToString("") { row -> row.joinToString(separator.toString()) { cell(it, separator) } + "\r\n" }
}

private fun Entry.cells(): List<Map<String, String>> = when (val p = payload) {
    is BloodPressure -> listOf(
        mapOf("type" to "blood_pressure", "systolic" to "${p.systolic}", "diastolic" to "${p.diastolic}", "pulse" to (p.pulse?.toString() ?: "")),
    )
    is Water -> listOf(mapOf("type" to "water", "water_ml" to "${p.ml}"))
    is Food -> listOf(mapOf("type" to "food", "text" to (p.text ?: ""), "photo_ref" to (p.photoRef ?: "")))
    is DiaryNote -> listOf(mapOf("type" to "diary_note", "text" to p.text, "photo_ref" to (p.photoRef ?: "")))
    is DoctorQuestion -> listOf(mapOf("type" to "doctor_question", "text" to p.text))
    is Medication -> listOf(
        mapOf(
            "type" to "medication", "medication" to p.name, "gtin" to (p.code ?: ""), "dose" to (p.dose ?: ""),
            "med_source" to p.source.name.lowercase(), "confirmed" to "${p.confirmed}",
        ),
    )
    is Answers -> p.values.map { (q, a) -> mapOf("type" to "answers", "question_id" to q, "answer" to a) }
    is Measurement -> listOf(
        mapOf("type" to "measurement", "measurement" to p.name, "value" to "${p.value}", "unit" to p.unit, "loinc" to (p.loinc ?: "")),
    )
}

private fun cell(value: String, separator: Char): String {
    // OWASP CSV injection: a leading = + - @ TAB CR makes spreadsheets evaluate the cell
    val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
    return if (safe.any { it == separator || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}
