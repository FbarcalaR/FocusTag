package io.github.fbarcalar.focustag.ui.setup.tags

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Reader mode while the hosting lifecycle (the nav back-stack entry) is RESUMED and [enabled];
 * switched off on pause and on leaving composition, so it is never left on (D-14, PLAN P3).
 */
@Composable
fun ReaderModeEffect(enabled: Boolean, onEnable: (Activity) -> Unit, onDisable: (Activity) -> Unit) {
    val activity = LocalActivity.current ?: return
    val enable by rememberUpdatedState(onEnable)
    val disable by rememberUpdatedState(onDisable)
    LifecycleResumeEffect(enabled, activity) {
        if (enabled) enable(activity)
        onPauseOrDispose { if (enabled) disable(activity) }
    }
}
