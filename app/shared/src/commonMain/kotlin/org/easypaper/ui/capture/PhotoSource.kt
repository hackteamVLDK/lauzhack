package org.easypaper.ui.capture

import androidx.compose.runtime.Composable

/**
 * Where a photo can come from on this platform (task 28): [takePhoto] opens the camera, [pickPhoto] the gallery or
 * a file chooser; null when the platform has neither. The photo reaches onPhoto already shrunk to ≤ 1024 px and
 * ≤ 200 KB without metadata (shrinkPhoto); the full-size original is not kept. Recognition: org.easypaper.scan.readPhoto.
 * [onError]: the chosen file or picture could not be opened as a photo (say so; otherwise the tap seems to do nothing).
 */
class PhotoSource(val takePhoto: (() -> Unit)?, val pickPhoto: (() -> Unit)?)

@Composable
expect fun rememberPhotoSource(onError: () -> Unit = {}, onPhoto: (ByteArray) -> Unit): PhotoSource
