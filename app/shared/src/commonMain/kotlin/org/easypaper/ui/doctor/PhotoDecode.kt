package org.easypaper.ui.doctor

/** Skia (Desktop, web) turns a JPEG upright by its EXIF orientation while decoding; Android's BitmapFactory does not. */
internal expect val decoderTurnsPhotosUpright: Boolean
