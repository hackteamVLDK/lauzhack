package org.easypaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.easypaper.ui.shell.EasyPaperApp
import org.easypaper.ui.shell.Role

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            EasyPaperApp(role = Role.Patient)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    EasyPaperApp(role = Role.Patient, demo = true)
}