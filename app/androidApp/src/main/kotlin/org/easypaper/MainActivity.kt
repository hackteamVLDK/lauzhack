package org.easypaper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import org.easypaper.ui.shell.EasyPaperApp
import org.easypaper.ui.shell.Role

class MainActivity : ComponentActivity() {
    /**
     * The invite link (easypaper://invite#epi1:…, from the doctor's QR or a message) the app was opened with.
     * One activity (singleTask): a link opened while the app runs arrives in [onNewIntent] and replaces it.
     */
    private var invite by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        invite = intent?.data?.toString()

        setContent {
            EasyPaperApp(role = Role.Patient, invite = invite)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { invite = it.toString() }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    EasyPaperApp(role = Role.Patient, demo = true)
}
