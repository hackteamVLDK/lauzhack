package org.easypaper.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.DiaryNote
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.Medication
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Bump only together with a migration and a note to the doctor-screen owner (#10). */
const val REPORT_SCHEMA_VERSION = 1

/**
 * What the patient hands over at the visit. [summary] is the AI draft (#7) that cites originals by entry id,
 * e.g. "[e12]"; the doctor confirms every field. [fhir] is the same data as a FHIR-like Bundle.
 */
@Serializable
data class Report(
    val schemaVersion: Int = REPORT_SCHEMA_VERSION,
    val id: String,
    val createdAt: Long, // epoch millis UTC
    val record: PatientRecord,
    val summary: String? = null,
    val fhir: JsonObject = record.toFhirBundle(),
) {
    init {
        require(schemaVersion in 1..REPORT_SCHEMA_VERSION) { "Unsupported report schemaVersion $schemaVersion" }
        require(':' !in id) { "report id must not contain ':' (it goes into the QR string)" }
    }
}

private const val LOINC = "http://loinc.org"
private const val UCUM = "http://unitsofmeasure.org"

/** FHIR-like Bundle (type collection). Every resource id is the [Entry.id], so a summary can link to originals. */
fun PatientRecord.toFhirBundle(): JsonObject = buildJsonObject {
    put("resourceType", "Bundle")
    put("id", request.id)
    put("type", "collection")
    putJsonArray("entry") {
        for (entry in entries) addJsonObject { put("resource", entry.toFhir()) }
    }
}

@OptIn(ExperimentalTime::class)
private fun Entry.toFhir(): JsonObject = buildJsonObject {
    put("id", id)
    val time = Instant.fromEpochMilliseconds(at).toString()
    when (val p = payload) {
        is BloodPressure -> observation(time, "85354-9", "Blood pressure panel") {
            putJsonArray("component") {
                addJsonObject { code("8480-6", "Systolic blood pressure"); quantity(p.systolic, "mm[Hg]") }
                addJsonObject { code("8462-4", "Diastolic blood pressure"); quantity(p.diastolic, "mm[Hg]") }
                p.pulse?.let { addJsonObject { code("8867-4", "Heart rate"); quantity(it, "/min") } }
            }
        }
        is Water -> observation(time, null, "Fluid intake") { quantity(p.ml, "mL") }
        is Food -> observation(time, null, "Food intake") { p.text?.let { put("valueString", it) }; photo(p.photoRef) }
        is DiaryNote -> observation(time, null, "Patient diary note") { put("valueString", p.text); photo(p.photoRef) }
        is DoctorQuestion -> observation(time, null, "Question to the doctor") { put("valueString", p.text) }
        is Medication -> {
            put("resourceType", "MedicationStatement")
            put("status", if (p.confirmed) "recorded" else "draft") // unconfirmed scan stays a draft
            put("effectiveDateTime", time)
            putJsonObject("medication") {
                putJsonObject("concept") {
                    put("text", p.name)
                    p.code?.let { code -> putJsonArray("coding") { addJsonObject { put("system", "https://www.gs1.org/gtin"); put("code", code) } } }
                }
            }
            p.dose?.let { putJsonArray("dosage") { addJsonObject { put("text", it) } } }
            putJsonArray("note") { addJsonObject { put("text", "source: " + p.source.name.lowercase()) } }
        }
        is Answers -> {
            put("resourceType", "QuestionnaireResponse")
            put("status", "completed")
            put("questionnaire", widgetId)
            put("authored", time)
            putJsonArray("item") {
                for ((question, answer) in p.values) addJsonObject {
                    put("linkId", question)
                    putJsonArray("answer") { addJsonObject { put("valueString", answer) } }
                }
            }
        }
    }
}

private fun JsonObjectBuilder.observation(time: String, loinc: String?, text: String, value: JsonObjectBuilder.() -> Unit) {
    put("resourceType", "Observation")
    put("status", "final")
    put("effectiveDateTime", time)
    code(loinc, text)
    value()
}

private fun JsonObjectBuilder.code(loinc: String?, text: String) = putJsonObject("code") {
    put("text", text)
    loinc?.let { putJsonArray("coding") { addJsonObject { put("system", LOINC); put("code", it); put("display", text) } } }
}

private fun JsonObjectBuilder.quantity(value: Int, unit: String) = putJsonObject("valueQuantity") {
    put("value", value); put("unit", unit); put("system", UCUM); put("code", unit)
}

// Not FHIR: the photo bytes travel separately; the doctor screen resolves the reference.
private fun JsonObjectBuilder.photo(ref: String?) { ref?.let { put("photoRef", it) } }
