package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import org.easypaper.storage.PatientRepository
import org.easypaper.storage.desktopPatientRepository
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun rememberDeviceRepository(): Result<PatientRepository>? =
    produceState<Result<PatientRepository>?>(null) {
        value = try {
            Result.success(desktopPatientRepository())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }.value
