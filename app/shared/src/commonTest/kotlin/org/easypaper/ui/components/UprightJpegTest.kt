package org.easypaper.ui.components

import org.easypaper.report.jpegRotationDegrees
import kotlin.test.Test
import kotlin.test.assertEquals

class UprightJpegTest {
    /** JPEG with a big-endian EXIF IFD0 containing only the Orientation tag. */
    private fun jpeg(orientation: Int) = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE1.toByte(), 0, 34,
        0x45, 0x78, 0x69, 0x66, 0, 0,
        0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8,
        0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
        0, 0, 0, 0, 0xFF.toByte(), 0xD9.toByte(),
    )

    @Test
    fun orientationSixNeedsNinetyDegreesOnAndroid() {
        assertEquals(90, jpegRotationDegrees(jpeg(6)))
        assertEquals(90, storedJpegRotationDegrees(jpeg(6), decoderTurnsUpright = false))
    }

    @Test
    fun allCameraRotationsAreAppliedOnlyWhenTheDecoderDoesNotApplyExif() {
        for ((orientation, rotation) in mapOf(1 to 0, 3 to 180, 6 to 90, 8 to 270)) {
            assertEquals(rotation, storedJpegRotationDegrees(jpeg(orientation), decoderTurnsUpright = false))
            assertEquals(0, storedJpegRotationDegrees(jpeg(orientation), decoderTurnsUpright = true))
        }
        assertEquals(0, storedJpegRotationDegrees(byteArrayOf(), decoderTurnsUpright = false))
    }
}
