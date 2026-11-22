package org.easypaper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.easypaper.report.jpegRotationDegrees
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Compensate for EXIF only when the platform decoder leaves the stored pixels unturned. */
internal fun storedJpegRotationDegrees(bytes: ByteArray, decoderTurnsUpright: Boolean): Int =
    if (decoderTurnsUpright) 0 else jpegRotationDegrees(bytes)

/** Stored JPEGs retain EXIF orientation rather than rotated pixels. Invalid images show no content. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun UprightJpeg(
    bytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap = remember(bytes) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() } ?: return
    val turn = remember(bytes) { storedJpegRotationDegrees(bytes, decoderTurnsPhotosUpright) }
    val sideways = turn == 90 || turn == 270
    val ratio = if (sideways) bitmap.height.toFloat() / bitmap.width else bitmap.width.toFloat() / bitmap.height
    BoxWithConstraints(modifier.aspectRatio(ratio).clipToBounds(), contentAlignment = Alignment.Center) {
        Image(
            bitmap, contentDescription = contentDescription, contentScale = contentScale,
            modifier = Modifier.requiredSize(if (sideways) maxHeight else maxWidth, if (sideways) maxWidth else maxHeight)
                .graphicsLayer { rotationZ = turn.toFloat() },
        )
    }
}
