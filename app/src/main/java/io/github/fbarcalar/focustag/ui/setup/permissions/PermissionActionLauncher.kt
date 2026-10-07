package io.github.fbarcalar.focustag.ui.setup.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.fbarcalar.focustag.system.PermissionAction

/**
 * The only place that starts system screens or runtime requests (PLAN P5). [onResult] runs after
 * every runtime request so the checklist re-checks at once.
 */
@Composable
fun rememberPermissionActionLauncher(onResult: () -> Unit): (PermissionAction) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    // Saveable: the result can arrive in a re-created activity after the permission dialog.
    var pendingPermission by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSettings by rememberSaveable { mutableStateOf<Intent?>(null) }
    val runtime = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val permission = pendingPermission
        val settings = pendingSettings
        // The dialog no longer shows after repeated denials; the app's settings page is the way left.
        if (!granted && permission != null && settings != null && activity?.shouldShowRequestPermissionRationale(permission) == false) {
            context.startFirstResolvable(listOf(settings))
        }
        currentOnResult()
    }
    return remember(context, runtime) {
        { action ->
            when (action) {
                is PermissionAction.OpenSettings -> context.startFirstResolvable(action.intents)
                is PermissionAction.RequestRuntime -> {
                    pendingPermission = action.permission
                    pendingSettings = action.settingsIntent
                    runtime.launch(action.permission)
                }
                is PermissionAction.AdbGrant -> Unit
            }
        }
    }
}

/** Later intents are fallbacks for devices without the earlier screen (e.g. battery, T4 S3). */
private fun Context.startFirstResolvable(intents: List<Intent>) {
    intents.any { intent ->
        try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
