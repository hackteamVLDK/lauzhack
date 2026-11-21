package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import org.easypaper.storage.PatientRepository

/**
 * The patient's records on this device (task 27): encrypted on disk on Android (key in Android Keystore) and
 * Desktop (~/.easypaper), in memory in the browser. null while opening; a failure is shown, never replaced by
 * an empty in-memory store that would silently lose what the patient enters.
 */
@Composable
expect fun rememberDeviceRepository(): Result<PatientRepository>?
