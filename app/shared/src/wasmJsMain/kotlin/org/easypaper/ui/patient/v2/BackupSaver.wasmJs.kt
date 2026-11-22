@file:OptIn(ExperimentalWasmJsInterop::class)

package org.easypaper.ui.patient.v2

import androidx.compose.runtime.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@Composable
actual fun rememberFileSaver(fileName: String, onResult: (Boolean) -> Unit): (ByteArray) -> Unit = { bytes ->
    val success = runCatching { downloadBackup(Base64.encode(bytes), fileName) }.isSuccess
    onResult(success)
}

private fun downloadBackup(data: String, fileName: String) {
    js("""{ const bytes = Uint8Array.from(atob(data), c => c.charCodeAt(0));
        const url = URL.createObjectURL(new Blob([bytes], {type: 'application/octet-stream'}));
        const a = document.createElement('a'); a.href = url; a.download = fileName;
        document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 60000); }""")
}
