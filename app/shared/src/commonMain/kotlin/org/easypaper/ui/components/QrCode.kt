package org.easypaper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.easypaper.ui.theme.EasyPaperTheme

/**
 * A QR code people scan with a phone camera: dark modules on white with a quiet zone, in both themes.
 * White here is not decoration but what scanners need, so it is the one fixed color outside the theme.
 * The code is shown on a screen, never printed or creased, so low error correction keeps the modules large
 * enough to scan a long invite from a monitor.
 */
@Composable
fun QrCodeImage(content: String, description: String, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    Box(modifier.widthIn(max = spacing.xxl * 10).fillMaxWidth().clip(EasyPaperTheme.shapes.group).background(SCANNER_WHITE).padding(spacing.m)) {
        Image(
            rememberQrCodePainter(content) { errorCorrectionLevel = QrErrorCorrectionLevel.Low },
            contentDescription = description,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
    }
}

private val SCANNER_WHITE = Color.White
