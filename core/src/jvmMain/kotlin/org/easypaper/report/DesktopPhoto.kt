package org.easypaper.report

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Desktop: a photo file (JPEG, PNG, BMP, GIF) -> JPEG ≤ 1024 px, ≤ 200 KB, no metadata. See [shrinkPhoto]. */
fun shrinkPhoto(photo: ByteArray): ByteArray {
    val image = ImageIO.read(ByteArrayInputStream(photo)) ?: throw IllegalArgumentException("Not an image")
    return shrinkPhoto(photo) { maxSide, quality ->
        val scale = minOf(1.0, maxSide.toDouble() / maxOf(image.width, image.height))
        val w = maxOf(1, (image.width * scale).toInt())
        val h = maxOf(1, (image.height * scale).toInt())
        val rgb = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB) // transparent PNG areas become white
        rgb.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(image, 0, 0, w, h, Color.WHITE, null)
            dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality / 100f
            }
            writer.write(null, IIOImage(rgb, null, null), params)
            writer.dispose()
        }
        out.toByteArray()
    }
}
