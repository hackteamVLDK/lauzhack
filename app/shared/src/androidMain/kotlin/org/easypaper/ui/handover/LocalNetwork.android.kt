package org.easypaper.ui.handover

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberLocalNetworkAccess(): (onResult: (granted: Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
    }
    return remember(context, launcher) {
        { onResult ->
            if (Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK ||
                context.checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) onResult(true)
            else {
                pending = onResult
                launcher.launch(ACCESS_LOCAL_NETWORK)
            }
        }
    }
}

// Android 17 (API 37): apps targeting it reach 10.x / 172.16.x / 192.168.x only with this runtime permission.
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val LOCAL_NETWORK_SDK = 37
