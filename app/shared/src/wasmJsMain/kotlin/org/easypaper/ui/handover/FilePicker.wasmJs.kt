@file:OptIn(ExperimentalWasmJsInterop::class)

package org.easypaper.ui.handover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@Composable
actual fun rememberFilePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val deliver by rememberUpdatedState(onPicked)
    val picker = remember {
        createReportPicker { data -> deliver(data?.let { runCatching { Base64.decode(it) }.getOrNull() }) }
    }
    DisposableEffect(picker) { onDispose { disposeReportPicker(picker) } }
    return remember(picker) { { openReportPicker(picker) } }
}

private fun openReportPicker(picker: JsAny): Unit = js("picker.open()")
private fun disposeReportPicker(picker: JsAny): Unit = js("picker.dispose()")

private fun createReportPicker(onResult: (String?) -> Unit): JsAny = js("""{
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.epr,application/octet-stream';
    input.hidden = true;
    document.body.appendChild(input);
    let disposed = false;
    let reader = null;
    input.oncancel = () => { if (!disposed) onResult(null); };
    input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) { if (!disposed) onResult(null); return; }
        if (reader) reader.abort();
        const current = new FileReader();
        reader = current;
        current.onload = () => {
            if (!disposed && reader === current) {
                reader = null;
                onResult(String(current.result).split(',')[1]);
            }
        };
        current.onerror = () => {
            if (!disposed && reader === current) { reader = null; onResult(null); }
        };
        current.readAsDataURL(file);
    };
    return {
        open: () => { if (!disposed) { input.value = ''; input.click(); } },
        dispose: () => { disposed = true; if (reader) reader.abort(); input.remove(); }
    };
}""")
