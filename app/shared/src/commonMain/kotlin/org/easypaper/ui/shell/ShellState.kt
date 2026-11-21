package org.easypaper.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.easypaper.demo.Demo
import org.easypaper.model.PatientRecord
import org.easypaper.report.Report

/** Which app this device shows. Android is always the patient, the web always the doctor. */
enum class Role { Patient, Doctor }

/** Developer settings from the hidden menu; also set by Desktop system properties for screenshots. */
@Stable
class ShellSettings(dark: Boolean? = null, largeText: Boolean = false) {
    /** null = follow the system. */
    var dark: Boolean? by mutableStateOf(dark)
    /** Text at 200 % to check layouts at the largest system font size. */
    var largeText: Boolean by mutableStateOf(largeText)
}

/**
 * What the apps show until the device storage (#27) and the transfer (#30) exist: the patient's
 * record and the reports the doctor opened, in memory.
 */
@Stable
class ShellState(role: Role? = null) {
    /** null on Desktop until the person chooses. */
    var role: Role? by mutableStateOf(role)

    var patientRecord: PatientRecord? by mutableStateOf(null)
        private set
    var reports: List<Report> by mutableStateOf(emptyList())
        private set
    var selectedReportId: String? by mutableStateOf(null)

    val selectedReport: Report? get() = reports.firstOrNull { it.id == selectedReportId }
    val hasData: Boolean get() = patientRecord != null || reports.isNotEmpty()

    /** The shared demo (#13): a five-day headache record for the patient and its report for the doctor. */
    fun fillWithDemoData() {
        patientRecord = Demo.record
        reports = listOf(Demo.report)
        selectedReportId = Demo.report.id
    }

    fun clear() {
        patientRecord = null
        reports = emptyList()
        selectedReportId = null
    }
}
