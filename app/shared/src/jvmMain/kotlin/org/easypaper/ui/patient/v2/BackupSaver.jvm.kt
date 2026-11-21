package org.easypaper.ui.patient.v2

import androidx.compose.runtime.*
import easypaper.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
actual fun rememberBackupSaver(onResult: (Boolean) -> Unit): (ByteArray) -> Unit {
    val result by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val replace = stringResource(Res.string.p2_replace_backup)
    return remember(replace) {
        { bytes ->
            SwingUtilities.invokeLater {
                val chooser = JFileChooser().apply { selectedFile = File("EasyPaper.epb") }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    val approved = !file.exists() || javax.swing.JOptionPane.showConfirmDialog(
                        null, replace, file.name, javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    ) == javax.swing.JOptionPane.OK_OPTION
                    if (approved) scope.launch {
                        val success = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { file.writeBytes(bytes) }.isSuccess }
                        result(success)
                    } else result(false)
                } else result(false)
            }
        }
    }
}
