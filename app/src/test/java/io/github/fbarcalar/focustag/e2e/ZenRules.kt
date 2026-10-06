package io.github.fbarcalar.focustag.e2e

import android.app.Application
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Intent
import android.service.notification.Condition
import org.robolectric.Shadows.shadowOf

/**
 * Reads and flips this app's AutomaticZenRules the way the OS or the user would. The OS is not
 * subject to the app's notification-policy grant, so each call briefly lifts the shadow's check.
 */
internal class ZenRules(private val app: Application) {
    private val notificationManager = app.getSystemService(NotificationManager::class.java)

    fun anyActive(): Boolean = asSystem {
        ours().keys.any { id -> notificationManager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE }
    }

    fun switchAllOff() = asSystem {
        ours().forEach { (id, rule) ->
            notificationManager.setAutomaticZenRuleState(id, Condition(rule.conditionId, "off", Condition.STATE_FALSE))
        }
    }

    fun broadcastStatusChanged() {
        app.sendBroadcast(Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED).setPackage(app.packageName))
    }

    private fun ours(): Map<String, AutomaticZenRule> =
        notificationManager.automaticZenRules.filterValues { rule ->
            rule.owner?.packageName == app.packageName || rule.configurationActivity?.packageName == app.packageName
        }

    private fun <T> asSystem(block: () -> T): T {
        val shadow = shadowOf(notificationManager)
        val granted = notificationManager.isNotificationPolicyAccessGranted
        shadow.setNotificationPolicyAccessGranted(true)
        try {
            return block()
        } finally {
            shadow.setNotificationPolicyAccessGranted(granted)
        }
    }
}
