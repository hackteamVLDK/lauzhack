package org.easypaper.ui.handover

import androidx.compose.runtime.Composable

/**
 * Reads the patient's QR code the platform's way (task 30): the browser's camera on the web, an image file on
 * Desktop. Returns the action that starts reading, or null where the doctor app has no reader (then the code is
 * pasted or typed by a scanner). [onCode] gets the text; [onFailure] means no camera, no permission or no QR.
 */
@Composable
expect fun rememberQrReader(onCode: (String) -> Unit, onFailure: () -> Unit): (() -> Unit)?
