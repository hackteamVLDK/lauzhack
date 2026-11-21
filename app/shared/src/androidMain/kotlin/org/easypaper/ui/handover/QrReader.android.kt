package org.easypaper.ui.handover

import androidx.compose.runtime.Composable

/** The phone is the patient's: it shows the QR, it does not read one. */
@Composable
actual fun rememberQrReader(onCode: (String) -> Unit, onFailure: () -> Unit): (() -> Unit)? = null
