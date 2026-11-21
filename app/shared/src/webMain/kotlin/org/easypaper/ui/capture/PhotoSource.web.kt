package org.easypaper.ui.capture

import androidx.compose.runtime.Composable

// shortcut: no photos in the browser yet (the web app is the doctor's), add an <input type=file> when patients use it.
@Composable
actual fun rememberPhotoSource(onPhoto: (ByteArray) -> Unit): PhotoSource = PhotoSource(null, null)
