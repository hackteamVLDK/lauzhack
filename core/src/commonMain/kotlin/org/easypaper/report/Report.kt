package org.easypaper.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.DiaryNote
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.Measurement
import org.easypaper.model.Medication
import org.easypaper.model.SideEffectCheck
import org.easypaper.scan.SIDE_EFFECTS_BY_KEY
import org.easypaper.model.PatientRecord
import org.easypaper.model.Question
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
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

/**
 * FHIR-like Bundle (type collection): first a Composition (table of contents, one section per doctor's widget,
 * status preliminary: patient-reported, not confirmed by a doctor), then one Questionnaire per questionnaire widget,
 * then one resource per entry. Entry resource ids are the [Entry.id], so a summary can link to originals.
 */
fun PatientRecord.toFhirBundle(): JsonObject {
    val questionsByWidget = request.widgets.filterIsInstance<QuestionnaireWidget>()
        .associate { w -> w.id to w.questions.associateBy { it.id } }
    val resources = entries.map { it to it.toFhir(questionsByWidget[it.widgetId].orEmpty()) }
    return buildJsonObject {
        put("resourceType", "Bundle")
        put("id", request.id)
        put("type", "collection")
        putJsonArray("entry") {
            addJsonObject { put("resource", composition(resources)) }
            for (w in request.widgets.filterIsInstance<QuestionnaireWidget>()) addJsonObject { put("resource", w.toFhirQuestionnaire()) }
            for ((_, resource) in resources) addJsonObject { put("resource", resource) }
        }
    }
}

private fun PatientRecord.composition(resources: List<Pair<Entry, JsonObject>>): JsonObject = buildJsonObject {
    fun ref(resource: JsonObject) = resource.getValue("resourceType").jsonPrimitive.content + "/" + resource.getValue("id").jsonPrimitive.content
    put("resourceType", "Composition")
    put("id", request.id)
    put("status", "preliminary")
    putJsonObject("type") { put("text", "Patient-reported record") }
    put("date", request.startDate)
    put("title", "Patient-reported record: ${request.complaint}")
    putJsonArray("author") { addJsonObject { put("display", "Patient") } }
    val known = request.widgets.map { it.id }.toSet()
    val sections = request.widgets.map { w -> w.title to resources.filter { it.first.widgetId == w.id } } +
        listOf("Other" to resources.filter { it.first.widgetId !in known })
    putJsonArray("section") {
        for ((title, items) in sections) if (items.isNotEmpty()) addJsonObject {
            put("title", title)
            putJsonArray("entry") { for ((_, r) in items) addJsonObject { put("reference", ref(r)) } }
        }
    }
}

private fun QuestionnaireWidget.toFhirQuestionnaire(): JsonObject = buildJsonObject {
    put("resourceType", "Questionnaire")
    put("id", id)
    put("status", "active")
    put("title", title)
    putJsonArray("item") {
        for (q in questions) addJsonObject {
            put("linkId", q.id)
            put("text", q.text)
            put("type", when (q.kind) { QuestionKind.YES_NO -> "boolean"; QuestionKind.SCALE -> "integer"; QuestionKind.CHOICE -> "choice"; QuestionKind.TEXT -> "string" })
            if (q.kind == QuestionKind.CHOICE) putJsonArray("answerOption") {
                for (o in q.options) addJsonObject { putJsonObject("valueCoding") { put("code", o); put("display", o) } }
            }
        }
    }
}

/** Typed by the question kind; a value that does not fit stays a string, so nothing is lost. */
private fun JsonObjectBuilder.answerValue(q: Question?, value: String) {
    when {
        q?.kind == QuestionKind.YES_NO && (value == "yes" || value == "no") -> put("valueBoolean", value == "yes")
        q?.kind == QuestionKind.SCALE && value.toIntOrNull() != null -> put("valueInteger", value.toInt())
        q?.kind == QuestionKind.CHOICE -> putJsonObject("valueCoding") { put("code", value); put("display", value) }
        else -> put("valueString", value)
    }
}

@OptIn(ExperimentalTime::class)
private fun Entry.toFhir(questions: Map<String, Question>): JsonObject = buildJsonObject {
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
        // what the person noticed while taking a medicine: one yes/no component per side effect asked about
        is SideEffectCheck -> observation(time, null, "Patient-reported side effects: " + p.medication) {
            putJsonArray("component") {
                for ((key, answer) in p.answers) addJsonObject {
                    code(null, SIDE_EFFECTS_BY_KEY[key]?.en ?: key)
                    put("valueBoolean", answer == "yes")
                }
            }
        }
        // the person's own measurement; a known kind (weight) carries its LOINC code and a UCUM unit
        is Measurement -> observation(time, p.loinc, p.name) {
            putJsonObject("valueQuantity") {
                put("value", p.value); put("unit", p.unit)
                if (p.loinc == Measurement.WEIGHT_LOINC) { put("system", UCUM); put("code", "kg") }
            }
            putJsonArray("category") { addJsonObject { put("text", "patient-reported") } }
        }
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
            // what the patient says they took (task 71); otherwise the dose text they typed
            (p.intake?.let { "${it.amount} ${it.unit}" } ?: p.dose)?.let { putJsonArray("dosage") { addJsonObject { put("text", it) } } }
            putJsonArray("note") {
                addJsonObject { put("text", "source: " + p.source.name.lowercase()) }
                p.product?.let { card ->
                    addJsonObject {
                        put("text", "registry card: ${card.name}; " + card.ingredients.joinToString(", ") {
                            listOfNotNull(it.name, it.amount, it.unit).joinToString(" ")
                        } + (card.perUnit?.let { " ($it)" } ?: "") + "; ${card.source.url} (${card.source.updated})")
                    }
                }
                p.patientWords?.let { addJsonObject { put("text", "patient's words: $it") } }
            }
        }
        is Answers -> {
            put("resourceType", "QuestionnaireResponse")
            put("status", "completed")
            put("questionnaire", "Questionnaire/$widgetId")
            put("authored", time)
            putJsonArray("item") {
                for ((question, answer) in p.values) addJsonObject {
                    put("linkId", question)
                    questions[question]?.let { put("text", it.text) }
                    putJsonArray("answer") { addJsonObject { answerValue(questions[question], answer) } }
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
