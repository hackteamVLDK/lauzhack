package org.easypaper.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import org.easypaper.ai.AiStatus
import org.easypaper.ai.AiUsage
import org.easypaper.ai.SummaryResult
import org.easypaper.demo.Demo
import org.easypaper.ui.doctor.VisitState
import org.easypaper.ui.format.currentUtcOffsetMinutes
import org.easypaper.model.DiaryNote
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.PatientRecord
import org.easypaper.ai.DocumentFact
import org.easypaper.report.ReceivedReport
import org.easypaper.report.Report
import org.easypaper.storage.PatientRepository
import org.easypaper.visit.demoVisitReport

/** Which app this device shows. Android is always the patient, the web always the doctor. */
enum class Role { Patient, Doctor }

/** Developer settings from the hidden menu; also set by Desktop system properties for screenshots. */
@Stable
class ShellSettings(dark: Boolean? = null, largeText: Boolean = false, serverUrl: String? = null, aiToken: String? = null) {
    /** null = follow the system. */
    var dark: Boolean? by mutableStateOf(dark)
    /** Text at 200 % to check layouts at the largest system font size. */
    var largeText: Boolean by mutableStateOf(largeText)
    /** Optional Easy Paper server for live AI status; null keeps the captured demo only. */
    var serverUrl: String? by mutableStateOf(serverUrl)
    /** Clinic access token for AI requests; kept in memory and never displayed. */
    var aiToken: String? by mutableStateOf(aiToken)
}

/**
 * What the apps work with: the patient's records through [repository] (task 27; in memory until the
 * device storage is wired in) and the reports the doctor opened (in memory until the transfer, task 30).
 */
@Stable
class ShellState(
    role: Role? = null,
    val repository: PatientRepository = inMemoryPatientRepository(),
    serverUrl: String? = null,
    aiToken: () -> String? = { null },
) {
    /** An invite link that opened the app (Android: easypaper://invite#…); the patient app offers to open it. */
    var pendingInvite: String? by mutableStateOf(null)

    /** The Easy Paper server for the AI summary (doctor app); null when there is none. */
    val server: ServerConnection? = serverUrl?.let { ServerConnection(it, aiToken) }

    /** Server AI mode, models and spending (#8), for the cost screen (#32); null until asked or unreachable. */
    var aiStatus: AiStatus? by mutableStateOf(null)
        private set

    /** Every AI call made from this app, for the cost screen (#32). */
    val aiUsage = mutableStateListOf<AiUsage>()

    /** The doctor's work on each opened report (decisions, saved form), kept while they look at other screens. */
    val visits = mutableMapOf<String, VisitState>()

    /** AI summaries by report id. */
    val aiResults = mutableStateMapOf<String, SummaryResult>()

    /** Report ids with a summary being written, and ids whose last try failed. */
    val aiBusy = mutableStateMapOf<String, Boolean>()
    val aiFailed = mutableStateMapOf<String, Boolean>()

    suspend fun refreshAiStatus() {
        aiStatus = server?.let { runCatching { it.ai.status() }.getOrNull() }
    }

    /** Asks the server for the summary of [report]; on failure the facts-based summary stays. */
    suspend fun summarize(report: Report) {
        val ai = server?.ai ?: return
        aiBusy[report.id] = true
        aiFailed.remove(report.id)
        try {
            val result = ai.summarize(report.record, reportPhotos[report.id].orEmpty(), utcOffsetMinutes = currentUtcOffsetMinutes()) // food photos without text get described (#36)
            aiResults[report.id] = result
            aiUsage += result.usage
            refreshAiStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aiFailed[report.id] = true // server down, AI off, timeout: said on screen, nothing else changes
        } finally {
            aiBusy.remove(report.id)
        }
    }

    /** null on Desktop until the person chooses. */
    var role: Role? by mutableStateOf(role)

    var reports: List<Report> by mutableStateOf(emptyList())
        private set
    var selectedReportId: String? by mutableStateOf(null)

    val selectedReport: Report? get() = reports.firstOrNull { it.id == selectedReportId }

    /** Facts read from paper documents at the visit, by report id (task 41). */
    val documents = mutableStateMapOf<String, List<DocumentFact>>()

    /** Photos of the reports the doctor received, by report id and photo ref (task 30). */
    val reportPhotos = mutableStateMapOf<String, Map<String, ByteArray>>()

    /** A report the doctor received by the patient's QR (task 30), with its photos; it becomes the selected one. */
    fun receive(received: ReceivedReport) {
        reports = reports.filterNot { it.id == received.report.id } + received.report
        reportPhotos[received.report.id] = received.photos
        selectedReportId = received.report.id
    }
    val hasData: Boolean get() = repository.records.value.isNotEmpty() || reports.isNotEmpty()

    /** The shared demo (#13): the five-day headache record for the patient and its report (with summary) for the doctor. */
    suspend fun fillWithDemoData() {
        repository.start(Demo.request)
        val stored = repository.records.value.firstOrNull { it.request.id == Demo.request.id }?.entries?.map { it.id }.orEmpty().toSet()
        for (entry in Demo.record.entries) if (entry.id !in stored) repository.add(Demo.request.id, entry.withoutPhotoRef())
        // The doctor's copy carries the demo summary with links to every entry (task 10).
        val report = demoVisitReport()
        reports = listOf(report)
        selectedReportId = report.id
    }

    suspend fun clear() {
        for (record in repository.records.value) repository.delete(record.request.id)
        reports = emptyList()
        selectedReportId = null
    }
}

/**
 * The demo's photo refs ("demo-photo-1") point to no stored photo and are not "photo/<id>" refs of the
 * PhotoStore, so the patient's copy of the demo keeps the text and drops the ref.
 */
private fun Entry.withoutPhotoRef(): Entry = when (val p = payload) {
    is Food -> copy(payload = p.copy(photoRef = null))
    is DiaryNote -> copy(payload = p.copy(photoRef = null))
    else -> this
}

/** The request the patient works on now: the most recently created one on the device. */
fun List<PatientRecord>.current(): PatientRecord? = maxByOrNull { it.request.createdAt }
