@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package org.easypaper.ui.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Opens the browser's file chooser for an image (on a phone it offers the camera too) and shrinks it in the page:
 * drawn on a canvas (the browser turns it upright by its EXIF) with the longest side ≤ 1024, 768 or 512 px and
 * JPEG quality 0.8, 0.65 or 0.5 until it is ≤ 200 KB, the same steps as shrinkPhoto. The canvas writes no metadata
 * (no EXIF, no GPS; only the JFIF density header). Nothing is uploaded here; onPhoto gets the JPEG as base64.
 */
private fun pickShrunkPhoto(onPhoto: (String) -> Unit, onError: () -> Unit): Unit = js("""{
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) return;
        const url = URL.createObjectURL(file);
        const img = new Image();
        img.onerror = () => { URL.revokeObjectURL(url); onError(); };
        img.onload = () => {
            URL.revokeObjectURL(url);
            const steps = [];
            for (const side of [1024, 768, 512]) for (const q of [0.8, 0.65, 0.5]) steps.push([side, q]);
            try { for (const [side, q] of steps) {
                if (!img.naturalWidth) break;
                const scale = Math.min(1, side / Math.max(img.naturalWidth, img.naturalHeight));
                const canvas = document.createElement('canvas');
                canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
                canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
                const ctx = canvas.getContext('2d');
                if (!ctx) break;
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                const data = canvas.toDataURL('image/jpeg', q);
                const base64 = data.substring(data.indexOf(',') + 1);
                const bytes = base64.length * 3 / 4 - (base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0);
                if (data.startsWith('data:image/jpeg') && bytes <= 200000) { onPhoto(base64); return; }
            } } catch (e) { /* a broken or huge picture: fall through to onError */ }
            onError();
        };
        img.src = url;
    };
    input.click();
}""")

@Composable
actual fun rememberPhotoSource(onError: () -> Unit, onPhoto: (ByteArray) -> Unit): PhotoSource {
    val deliver by rememberUpdatedState(onPhoto)
    val fail by rememberUpdatedState(onError)
    return remember {
        PhotoSource(takePhoto = null, pickPhoto = {
            pickShrunkPhoto({ base64 -> deliver(Base64.decode(base64)) }, { fail() })
        })
    }
}
