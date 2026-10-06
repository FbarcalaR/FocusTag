package io.github.fbarcalar.focustag.system.zen

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import android.service.notification.ZenPolicy
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.MainActivity
import io.github.fbarcalar.focustag.R
import javax.inject.Inject

/** Identifies our rule; not user-editable (PLAN S4). */
val ZEN_CONDITION_ID: Uri = Uri.parse("focustag://zen/focus")

/** The shape of our AutomaticZenRule: DND that lets only alarms through, plus grayscale. */
class ZenRuleSpec @Inject constructor(@ApplicationContext private val context: Context) {
    private val configurationActivity = ComponentName(context, MainActivity::class.java)

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun newRule(): AutomaticZenRule =
        AutomaticZenRule.Builder(context.getString(R.string.zen_rule_name), ZEN_CONDITION_ID)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setConfigurationActivity(configurationActivity)
            .setTriggerDescription(context.getString(R.string.zen_rule_trigger))
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setZenPolicy(alarmsOnlyPolicy())
            .setDeviceEffects(ZenDeviceEffects.Builder().setShouldDisplayGrayscale(true).build())
            .build()

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun condition(active: Boolean): Condition = Condition(
        ZEN_CONDITION_ID,
        context.getString(if (active) R.string.zen_condition_on else R.string.zen_condition_off),
        if (active) Condition.STATE_TRUE else Condition.STATE_FALSE,
        Condition.SOURCE_USER_ACTION,
    )

    fun isOurs(rule: AutomaticZenRule): Boolean =
        rule.conditionId == ZEN_CONDITION_ID && rule.configurationActivity?.packageName == context.packageName

    // Later calls win, so alarms are re-allowed after everything is disallowed (D-30 note).
    private fun alarmsOnlyPolicy(): ZenPolicy =
        ZenPolicy.Builder().disallowAllSounds().allowAlarms(true).hideAllVisualEffects().build()
}
