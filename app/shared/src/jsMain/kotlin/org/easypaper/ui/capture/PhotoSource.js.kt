package org.easypaper.ui.capture

import androidx.compose.runtime.Composable

// shortcut: the JS build (old browsers) has no photos; the Wasm build picks one with <input type=file>.
@Composable
actual fun rememberPhotoSource(onError: () -> Unit, onPhoto: (ByteArray) -> Unit): PhotoSource = PhotoSource(null, null)
