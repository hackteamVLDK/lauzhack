package org.easypaper.ui.patient.medications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import org.easypaper.catalog.Candidate
import org.easypaper.catalog.Catalog
import org.easypaper.catalog.CatalogQuery
import org.easypaper.catalog.Resolution
import org.easypaper.catalog.ResolutionKind
import org.easypaper.catalog.strengthLabel
import org.easypaper.diary.EntryProblem
import org.easypaper.model.Entry
import org.easypaper.model.Intake
import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource
import org.easypaper.scan.PhotoReading
import org.easypaper.storage.PatientRepository
import org.easypaper.ui.patient.newEntryId

/** One step of recognising a medicine package (task 71). */
sealed interface RecognitionStep {
    /** Camera, an image file or typing the label; every loaded registry is searched. */
    data object Start : RecognitionStep
    data object Reading : RecognitionStep
    data object Searching : RecognitionStep
    /** What the registries answered for the photo or the typed label text. */
    data class Result(val resolution: Resolution, val query: CatalogQuery) : RecognitionStep
    /** The card to confirm; [entryId] is fixed here, so confirming twice never records two intakes. */
    data class Card(val candidate: Candidate, val gtin: String?, val entryId: String = newEntryId()) : RecognitionStep
    /** After "Confirm": how much was actually taken and when, asked separately from the package. */
    data class Intake(val medication: Medication, val entryId: String) : RecognitionStep
    /** The patient's own words, without a registry card ("cold powder, can't remember the name"). */
    data class Words(val readName: String?, val entryId: String = newEntryId()) : RecognitionStep
    /** Typing what is printed on the box (no camera, no text recognition on this device, or a better reading). */
    data object TypeLabel : RecognitionStep
    /** The registries could not be asked (offline, server down): typing in the patient's words still works. */
    data class Unavailable(val readName: String?) : RecognitionStep
}

/**
 * The recognition flow without the platform parts: the photo comes in through [onPhoto] (camera or a chosen
 * image), the device reads codes and text with [reader], the server's [catalog] answers. Plain state, so the whole
 * flow can be driven in a test without a camera.
 */
class Recognition(
    private val catalog: Catalog?,
    private val reader: suspend (ByteArray) -> PhotoReading,
) {
    var step: RecognitionStep? by mutableStateOf(null)
        private set
    private var packagePhoto: ByteArray? = null
    var onlineUnavailable: Boolean by mutableStateOf(false)
        private set
    val canReadOnline: Boolean get() = packagePhoto != null && catalog != null &&
        (step as? RecognitionStep.Result)?.resolution?.kind in setOf(ResolutionKind.TOO_LITTLE, ResolutionKind.NOT_FOUND)

    fun start() { packagePhoto = null; onlineUnavailable = false; step = RecognitionStep.Start }
    fun close() { packagePhoto = null; onlineUnavailable = false; step = null }
    /** An intake of a medicine already on the list (its confirmed card is reused, nothing is looked up again). */
    fun intakeOf(medication: Medication) { step = RecognitionStep.Intake(medication.copy(intake = null), newEntryId()) }
    fun typeLabel() { step = RecognitionStep.TypeLabel }
    fun words(readName: String? = (step as? RecognitionStep.Result)?.resolution?.labelName) { step = RecognitionStep.Words(readName) }
    fun choose(candidate: Candidate, gtin: String?) { step = RecognitionStep.Card(candidate, gtin) }

    suspend fun onPhoto(photo: ByteArray) {
        packagePhoto = photo.copyOf()
        onlineUnavailable = false
        step = RecognitionStep.Reading
        val reading = try { reader(photo) } catch (e: CancellationException) { throw e } catch (e: Exception) { PhotoReading(emptyList(), "") }
        search(CatalogQuery(reading.codes, reading.text, country = null))
    }

    suspend fun onLabelText(text: String) {
        packagePhoto = null
        onlineUnavailable = false
        search(CatalogQuery(emptyList(), text, country = null))
    }

    /** Called only by the separate online-reading button, after its photo disclosure is shown. */
    suspend fun readOnline() {
        if (!canReadOnline) return
        val previous = step as? RecognitionStep.Result ?: return
        val photo = packagePhoto ?: return
        val registries = catalog ?: return
        onlineUnavailable = false
        step = RecognitionStep.Reading
        step = try {
            val result = registries.readLabel(photo)
            if (packagePhoto !== photo || step != RecognitionStep.Reading) return
            if (result.kind == ResolutionKind.EXACT) RecognitionStep.Card(result.candidates.single(), result.gtin)
            else RecognitionStep.Result(result, previous.query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (packagePhoto !== photo || step != RecognitionStep.Reading) return
            onlineUnavailable = true
            previous
        }
    }

    private suspend fun search(query: CatalogQuery) {
        if (query.codes.isEmpty() && query.text.isBlank()) {
            step = RecognitionStep.Result(Resolution(ResolutionKind.TOO_LITTLE, question = org.easypaper.catalog.ResolutionQuestion.PHOTO_PACKAGE), query)
            return
        }
        val registries = catalog ?: run { step = RecognitionStep.Unavailable(likelyName(query.text)); return }
        step = RecognitionStep.Searching
        step = try {
            val resolution = registries.resolve(query)
            if (resolution.kind == ResolutionKind.EXACT) RecognitionStep.Card(resolution.candidates.single(), resolution.gtin)
            else RecognitionStep.Result(resolution, query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecognitionStep.Unavailable(likelyName(query.text))
        }
    }

    /** "Confirm" on the card: the medicine is known, the intake is asked next. */
    fun confirm(card: RecognitionStep.Card) {
        step = RecognitionStep.Intake(confirmedMedication(card.candidate), card.entryId)
    }

    /** The patient's words become a medication without any registry card, then the intake is asked. */
    fun confirmWords(words: RecognitionStep.Words, text: String, labelIngredients: String) {
        step = RecognitionStep.Intake(wordsMedication(text, labelIngredients), words.entryId)
    }

    private fun likelyName(text: String) = text.lines().map { it.trim() }.firstOrNull { l -> l.count { it.isLetter() } >= 3 }?.take(80)
}

/**
 * The confirmed card as a snapshot in the entry: name, version, composition, units and source as the registry gave
 * them today. The pack found by its code is kept as information only; its size never becomes the amount taken.
 */
fun confirmedMedication(candidate: Candidate): Medication {
    val card = candidate.product.card
    val pack = candidate.pack
    return Medication(
        name = card.name,
        // only the GTIN of the pack that was found: an unknown barcode next to a card found by its text is not this product's code
        code = pack?.gtin,
        dose = candidate.product.strengthLabel().ifEmpty { null },
        source = MedicationSource.SCAN,
        confirmed = true,
        product = card.copy(pack = pack?.description ?: card.pack, gtin = pack?.gtin),
    )
}

/** A medicine, tea or supplement in the patient's words: no composition is added, label ingredients stay as printed. */
fun wordsMedication(words: String, labelIngredients: String): Medication = Medication(
    name = words.trim(),
    source = MedicationSource.MANUAL,
    confirmed = true,
    patientWords = words.trim(),
    labelIngredients = labelIngredients.trim().ifEmpty { null },
)

/**
 * Records [medication] with what the patient says they took ([intake], or null = only on their list). The entry's
 * time is the intake time. Saving the same [entryId] again (a second tap, a retry) does nothing.
 */
suspend fun PatientRepository.recordMedicine(
    requestId: String, widgetId: String, entryId: String, medication: Medication, intake: Intake?, now: Long,
): List<EntryProblem> {
    if (records.value.firstOrNull { it.request.id == requestId }?.entries?.any { it.id == entryId } == true) return emptyList()
    return try {
        add(requestId, Entry(entryId, widgetId, intake?.takenAt ?: now, medication.copy(intake = intake)))
    } catch (e: IllegalArgumentException) {
        // a parallel tap saved it a moment earlier: that is the same intake, not a second one
        if (e.message?.contains("already exists") == true) emptyList() else throw e
    }
}

/** "1", "0,5", "2.5" -> the amount as entered (decimal comma accepted); null when it is not a positive number. */
fun intakeAmount(text: String): String? {
    val t = text.trim().replace(',', '.')
    val value = t.toDoubleOrNull() ?: return null
    return if (value > 0 && value <= MAX_UNITS) t else null
}

/** "08:30", "8:30", "8.30", "0830" -> minutes after midnight; null when it is not a time of day. */
fun timeOfDayMinutes(text: String): Int? {
    val m = Regex("""^\s*(\d{1,2})\s*[:.h]?\s*(\d{2})\s*$""").find(text) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}

/**
 * The unit the patient counts, as one of the app's unit keys (tablet, capsule, sachet, ml, g, dose, suppository,
 * lozenge) when the registry word is known, otherwise the registry's own word.
 */
fun countUnitKey(doseUnit: String?): String? = when (doseUnit?.lowercase()?.trim()) {
    null, "" -> null
    "tablet", "comprimé", "comprimé pelliculé", "comprimé effervescent", "comprimé sécable", "comprimé orodispersible" -> "tablet"
    "capsule", "gélule" -> "capsule"
    "sachet", "sachet-dose" -> "sachet"
    "ml" -> "ml"
    "g" -> "g"
    "dose", "dose(s)" -> "dose"
    "suppository", "suppositoire" -> "suppository"
    "lozenge", "pastille" -> "lozenge"
    else -> if (doseUnit.startsWith("comprimé")) "tablet" else doseUnit
}

private const val MAX_UNITS = 1000.0
