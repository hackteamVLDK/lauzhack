package org.easypaper.ui.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.easypaper.report.shrinkPhoto
import java.awt.FileDialog
import java.awt.Frame

private val IMAGE_FILES = setOf("jpg", "jpeg", "png", "bmp", "gif")

/** Desktop: no camera; a photo file from the system file dialog. */
@Composable
actual fun rememberPhotoSource(onPhoto: (ByteArray) -> Unit): PhotoSource {
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onPhoto)
    return remember {
        PhotoSource(takePhoto = null, pickPhoto = {
            val dialog = FileDialog(null as Frame?, "Photo", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_FILES }
                isVisible = true // modal: returns after the choice
            }
            dialog.files.firstOrNull()?.let { file ->
                scope.launch {
                    val photo = withContext(Dispatchers.IO) {
                        try { shrinkPhoto(file.readBytes()) } catch (_: IllegalArgumentException) { null }
                    }
                    photo?.let(deliver)
                }
            }
        })
    }
}
