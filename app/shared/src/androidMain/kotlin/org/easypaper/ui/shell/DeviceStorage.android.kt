package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import org.easypaper.storage.PatientRepository
import org.easypaper.storage.androidPatientRepository
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun rememberDeviceRepository(): Result<PatientRepository>? {
    val context = LocalContext.current.applicationContext
    return produceState<Result<PatientRepository>?>(null, context) {
        value = try {
            Result.success(androidPatientRepository(context))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }.value
}
