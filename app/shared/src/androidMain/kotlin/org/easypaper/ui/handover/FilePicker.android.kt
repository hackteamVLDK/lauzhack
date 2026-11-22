package org.easypaper.ui.handover

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFilePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val deliver by rememberUpdatedState(onPicked)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { uri?.let { context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() } } }.getOrNull()
            }
            deliver(bytes)
        }
    }
    return remember(launcher) { { launcher.launch("*/*") } }
}
