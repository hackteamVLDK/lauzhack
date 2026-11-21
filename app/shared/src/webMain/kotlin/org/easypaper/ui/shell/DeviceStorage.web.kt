package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.easypaper.storage.PatientRepository

// shortcut: the browser is the doctor's app and keeps nothing; add IndexedDB (#27 step 3) if patients use the web.
@Composable
actual fun rememberDeviceRepository(): Result<PatientRepository>? = remember { Result.success(inMemoryPatientRepository()) }
