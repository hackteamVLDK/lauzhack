package org.easypaper.ui.handover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.doc_file_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.coroutines.resume

@Composable
actual fun rememberFilePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val deliver by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.doc_file_title)
    return remember(scope, title) {
        {
            scope.launch {
                val file = chooseReportFile(title)
                val bytes = withContext(Dispatchers.IO) { runCatching { file?.readBytes() }.getOrNull() }
                deliver(bytes)
            }
            Unit
        }
    }
}

private suspend fun chooseReportFile(title: String): File? = suspendCancellableCoroutine { continuation ->
    EventQueue.invokeLater {
        if (continuation.isActive) {
            try {
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
                continuation.invokeOnCancellation { EventQueue.invokeLater { dialog.dispose() } }
                try {
                    dialog.setFilenameFilter { _, name -> name.endsWith(".epr", ignoreCase = true) }
                    dialog.isVisible = true
                    if (continuation.isActive) continuation.resume(dialog.files.firstOrNull())
                } finally {
                    dialog.dispose()
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}
