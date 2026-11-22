package org.easypaper.ui.handover

import androidx.compose.runtime.Composable

/** Reads a report file locally. Null means the picker was cancelled or the file could not be read. */
@Composable
expect fun rememberFilePicker(onPicked: (ByteArray?) -> Unit): () -> Unit
