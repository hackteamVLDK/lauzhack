@file:OptIn(ExperimentalWasmJsInterop::class)

package org.easypaper.ui.handover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@JsModule("jsqr")
private external val jsqr: JsAny

/**
 * Opens the camera (the back one on tablets), shows a small preview over the page and reads frames with jsQR until
 * a QR code is found. Everything stays in the browser: no frame is uploaded. Returns the function that stops it.
 */
private fun startQrCamera(decoder: JsAny, onCode: (String) -> Unit, onFailure: (String) -> Unit): JsAny = js("""{
    const decode = typeof decoder === 'function' ? decoder : decoder.default;
    const video = document.createElement('video');
    video.setAttribute('playsinline', '');
    video.muted = true;
    video.style.cssText = 'position:fixed;right:24px;bottom:24px;width:320px;max-width:60vw;border-radius:12px;z-index:10;box-shadow:0 4px 24px rgba(0,0,0,.35)';
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    let stopped = false;
    let stream = null;
    const stop = () => { stopped = true; if (stream) stream.getTracks().forEach(t => t.stop()); video.remove(); };
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { onFailure('no camera API'); return stop; }
    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } }).then(s => {
        stream = s;
        if (stopped) { stop(); return; }
        video.srcObject = s;
        document.body.appendChild(video);
        video.play();
        const tick = () => {
            if (stopped) return;
            if (video.readyState >= 2 && video.videoWidth > 0) {
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
                ctx.drawImage(video, 0, 0);
                const frame = ctx.getImageData(0, 0, canvas.width, canvas.height);
                const found = decode(frame.data, frame.width, frame.height, { inversionAttempts: 'dontInvert' });
                if (found && found.data) { stop(); onCode(found.data); return; }
            }
            requestAnimationFrame(tick);
        };
        tick();
    }).catch(e => { stop(); onFailure(String((e && e.name) || e)); });
    return stop;
}""")

private fun callStop(stop: JsAny): Unit = js("stop()")

@Composable
actual fun rememberQrReader(onCode: (String) -> Unit, onFailure: () -> Unit): (() -> Unit)? {
    val code by rememberUpdatedState(onCode)
    val failure by rememberUpdatedState(onFailure)
    val running = remember { arrayOfNulls<JsAny>(1) }
    DisposableEffect(Unit) { onDispose { running[0]?.let(::callStop); running[0] = null } }
    return remember {
        {
            running[0]?.let(::callStop)
            running[0] = startQrCamera(jsqr, { text -> running[0] = null; code(text) }, { _ -> running[0] = null; failure() })
        }
    }
}
