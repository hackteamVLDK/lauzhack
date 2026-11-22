package org.easypaper.ui.patient.v2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFileSaver(fileName: String, onResult: (Boolean) -> Unit): (ByteArray) -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pending
        pending = null
        if (uri == null || bytes == null) result(false) else scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) } }.isSuccess
            }
            result(success)
        }
    }
    return { bytes -> pending = bytes; launcher.launch(fileName) }
}
