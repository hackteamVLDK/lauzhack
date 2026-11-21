package org.easypaper.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.easypaper.demo.Demo
import org.easypaper.model.DiaryNote
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.PatientRecord
import org.easypaper.report.Report
import org.easypaper.storage.PatientRepository
import org.easypaper.visit.demoVisitReport

/** Which app this device shows. Android is always the patient, the web always the doctor. */
enum class Role { Patient, Doctor }

/** Developer settings from the hidden menu; also set by Desktop system properties for screenshots. */
@Stable
class ShellSettings(dark: Boolean? = null, largeText: Boolean = false, serverUrl: String? = null) {
    /** null = follow the system. */
    var dark: Boolean? by mutableStateOf(dark)
    /** Text at 200 % to check layouts at the largest system font size. */
    var largeText: Boolean by mutableStateOf(largeText)
    /** Optional Easy Paper server for live AI status; null keeps the captured demo only. */
    var serverUrl: String? by mutableStateOf(serverUrl)
}

/**
 * What the apps work with: the patient's records through [repository] (task 27; in memory until the
 * device storage is wired in) and the reports the doctor opened (in memory until the transfer, task 30).
 */
@Stable
class ShellState(role: Role? = null, val repository: PatientRepository = inMemoryPatientRepository()) {
    /** null on Desktop until the person chooses. */
    var role: Role? by mutableStateOf(role)

    var reports: List<Report> by mutableStateOf(emptyList())
        private set
    var selectedReportId: String? by mutableStateOf(null)

    val selectedReport: Report? get() = reports.firstOrNull { it.id == selectedReportId }
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
