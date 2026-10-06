package io.github.fbarcalar.focustag.system.zen

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.util.ReflectionHelpers

/** OS-side view of the shadow's zen rules for system-layer tests. */
class ZenTestSupport(private val context: Context) {
    val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)
    val spec = ZenRuleSpec(context)

    fun setPolicyAccess(granted: Boolean) = shadowOf(notifications).setNotificationPolicyAccessGranted(granted)

    fun ourRules(): Map<String, AutomaticZenRule> = notifications.automaticZenRules.filterValues(spec::isOurs)

    fun stateOf(id: String): Int = notifications.getAutomaticZenRuleState(id)

    fun setState(id: String, state: Int) =
        notifications.setAutomaticZenRuleState(id, Condition(ZEN_CONDITION_ID, "test", state))

    fun addOurRule(enabled: Boolean = true): String =
        notifications.addAutomaticZenRule(AutomaticZenRule.Builder(spec.newRule()).setEnabled(enabled).build())

    fun addForeignRule(conditionId: Uri, packageName: String): String = notifications.addAutomaticZenRule(
        AutomaticZenRule.Builder("foreign", conditionId)
            .setConfigurationActivity(ComponentName(packageName, "$packageName.Config"))
            .build(),
    )

    /** The shadow keeps no call log; an unchanged instance proves no state was written. */
    fun storedCondition(id: String): Condition? =
        ReflectionHelpers.getStaticField<Map<String, Condition>>(ShadowNotificationManager::class.java, "automaticZenRuleStates")[id]
}
