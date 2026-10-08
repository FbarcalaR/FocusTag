package io.github.fbarcalar.focustag.system.zen

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.Condition
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Result of switching the zen rule. */
sealed interface ZenOutcome {
    /** The rule is in the requested state. */
    data object Applied : ZenOutcome

    /** Notification policy access is missing (D-31). */
    data object AccessDenied : ZenOutcome

    /** The OS kept the rule off: the user disabled the mode or overrode it (R-T4-1/2). */
    data object NotApplied : ZenOutcome

    /** Below API 35 (D-30). */
    data object Unsupported : ZenOutcome
}

/** Owns our single AutomaticZenRule: adopt or create it, dedupe it, and switch it (D-30, D-34, D-35). */
class ZenRuleController @Inject constructor(
    @ApplicationContext context: Context,
    private val spec: ZenRuleSpec,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    suspend fun activate(): ZenOutcome = withContext(io) {
        if (zenRulesSupported()) whenAccessible { activateRule() } else ZenOutcome.Unsupported
    }

    suspend fun deactivate(): ZenOutcome = withContext(io) {
        if (zenRulesSupported()) whenAccessible { deactivateRules() } else ZenOutcome.Unsupported
    }

    // Only SecurityException: access can be revoked between the check and the call.
    private inline fun whenAccessible(block: () -> ZenOutcome): ZenOutcome = try {
        if (notifications.isNotificationPolicyAccessGranted) block() else ZenOutcome.AccessDenied
    } catch (_: SecurityException) {
        ZenOutcome.AccessDenied
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun activateRule(): ZenOutcome {
        val rule = adoptOrCreate()
        return when {
            !rule.enabled -> ZenOutcome.NotApplied
            stateOf(rule.id) == Condition.STATE_TRUE -> ZenOutcome.Applied
            else -> reassert(rule.id)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun deactivateRules(): ZenOutcome {
        ourRules().keys.filter { stateOf(it) == Condition.STATE_TRUE }.forEach { setState(it, active = false) }
        return ZenOutcome.Applied
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun adoptOrCreate(): AdoptedRule {
        val ours = ourRules()
        val choice = chooseRule(ours.map { (id, rule) -> OwnedRule(id, rule.creationTime) })
        choice.duplicates.forEach { notifications.removeAutomaticZenRule(it) }
        val keep = choice.keep ?: return AdoptedRule(notifications.addAutomaticZenRule(spec.newRule()), enabled = true)
        return AdoptedRule(keep, enabled = ours.getValue(keep).isEnabled)
    }

    // FALSE before TRUE clears a user override (D-34); skipped when already FALSE.
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun reassert(id: String): ZenOutcome {
        if (stateOf(id) != Condition.STATE_FALSE) setState(id, active = false)
        setState(id, active = true)
        return if (stateOf(id) == Condition.STATE_TRUE) ZenOutcome.Applied else ZenOutcome.NotApplied
    }

    private fun ourRules(): Map<String, AutomaticZenRule> = notifications.automaticZenRules.filterValues(spec::isOurs)

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun stateOf(id: String): Int = notifications.getAutomaticZenRuleState(id)

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun setState(id: String, active: Boolean) = notifications.setAutomaticZenRuleState(id, spec.condition(active))

    private data class AdoptedRule(val id: String, val enabled: Boolean)
}
