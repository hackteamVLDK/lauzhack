package org.easypaper.ui.capture

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.easypaper.report.shrinkPhoto
import java.io.File

/** The system camera app (full resolution, no camera permission needed) and the photo picker (no storage permission). */
@Composable
actual fun rememberPhotoSource(onPhoto: (ByteArray) -> Unit): PhotoSource {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onPhoto)
    // the camera writes here through the FileProvider declared in this module's manifest (res/xml/photo_paths.xml)
    val file = remember { File(context.cacheDir, "photos/capture.jpg") }
    fun finish(read: () -> ByteArray?) {
        scope.launch {
            val photo = withContext(Dispatchers.IO) {
                try {
                    read()?.let(::shrinkPhoto)
                } catch (_: IllegalArgumentException) {
                    null // not an image the phone can decode
                } finally {
                    file.delete() // the full-size original with GPS never stays on the phone
                }
            }
            photo?.let(deliver)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) finish { file.readBytes() } else file.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) finish { context.contentResolver.openInputStream(picked)?.use { it.readBytes() } }
    }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    return remember(camera, gallery, hasCamera) {
        PhotoSource(
            takePhoto = if (hasCamera) {
                {
                    file.parentFile?.mkdirs()
                    camera.launch(FileProvider.getUriForFile(context, context.packageName + ".photos", file))
                }
            } else null,
            pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}
