package io.github.fbarcalar.focustag.focus.reassert

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Signals that our zen rule may have been switched off or lost (D-34, D-35). */
fun interface ZenChangeSignals {
    /** Cold; listens only while collected. */
    fun changes(): Flow<Unit>
}

/** [ZenChangeSignals] from the system's zen broadcasts, minus the echoes of our own activation. */
class BroadcastZenChangeSignals @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ZenChangeSignals {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun changes(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (isReassertionSignal(intent, notificationManager.currentInterruptionFilter)) trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(context, receiver, ZEN_ACTIONS, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private companion object {
        val ZEN_ACTIONS = IntentFilter().apply {
            addAction(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
        }
    }
}

/**
 * False for broadcasts that can only mean "on" (our rule activated, DND still on), so re-asserting
 * never feeds on its own echoes; true for anything that may mean the rule is off or access changed.
 */
internal fun isReassertionSignal(intent: Intent, currentFilter: Int): Boolean = when (intent.action) {
    NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED -> !reportsActivated(intent)
    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED -> true
    else -> false
}

// The constant is inlined; before API 35 the system never sends it, so the comparison is just false.
@SuppressLint("InlinedApi")
private fun reportsActivated(intent: Intent): Boolean = intent.getIntExtra(
    NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_STATUS,
    NotificationManager.AUTOMATIC_RULE_STATUS_UNKNOWN,
) == NotificationManager.AUTOMATIC_RULE_STATUS_ACTIVATED
