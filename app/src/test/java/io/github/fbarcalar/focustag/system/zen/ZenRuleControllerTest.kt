package io.github.fbarcalar.focustag.system.zen

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZenRuleControllerTest {
    private val zen = ZenTestSupport(ApplicationProvider.getApplicationContext())
    private val controller = ZenRuleController(ApplicationProvider.getApplicationContext(), zen.spec, Dispatchers.Unconfined)

    @Before
    fun grantAccess() = zen.setPolicyAccess(true)

    @Test
    fun `first activation creates one rule with the focus shape`() = runTest {
        controller.activate()

        val rule = zen.ourRules().values.single()
        assertThat(rule.type).isEqualTo(AutomaticZenRule.TYPE_OTHER)
        assertThat(rule.interruptionFilter).isEqualTo(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertThat(rule.conditionId).isEqualTo(ZEN_CONDITION_ID)
        assertThat(rule.configurationActivity?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(rule.deviceEffects?.shouldDisplayGrayscale()).isTrue()
    }

    @Test
    fun `the policy lets alarms through and blocks calls and messages`() = runTest {
        controller.activate()

        val policy = zen.ourRules().values.single().zenPolicy
        assertThat(policy?.priorityCategoryAlarms).isEqualTo(ZenPolicy.STATE_ALLOW)
        assertThat(policy?.priorityCategoryCalls).isEqualTo(ZenPolicy.STATE_DISALLOW)
        assertThat(policy?.priorityCategoryMessages).isEqualTo(ZenPolicy.STATE_DISALLOW)
    }

    @Test
    fun `activation switches the rule on`() = runTest {
        val outcome = controller.activate()

        assertThat(outcome).isEqualTo(ZenOutcome.Applied)
        assertThat(zen.stateOf(zen.ourRules().keys.single())).isEqualTo(Condition.STATE_TRUE)
    }

    @Test
    fun `activating twice keeps a single rule`() = runTest {
        controller.activate()
        controller.activate()

        assertThat(zen.ourRules()).hasSize(1)
    }

    @Test
    fun `activating an active rule writes no state`() = runTest {
        controller.activate()
        val id = zen.ourRules().keys.single()
        val before = zen.storedCondition(id)

        controller.activate()

        assertThat(zen.storedCondition(id)).isSameInstanceAs(before)
    }

    @Test
    fun `deactivating an inactive rule writes no state`() = runTest {
        val id = zen.addOurRule()
        zen.setState(id, Condition.STATE_FALSE)
        val before = zen.storedCondition(id)

        controller.deactivate()

        assertThat(zen.storedCondition(id)).isSameInstanceAs(before)
    }

    @Test
    fun `a rule the user disabled is reported as not applied and left alone`() = runTest {
        val id = zen.addOurRule(enabled = false)

        val outcome = controller.activate()

        assertThat(outcome).isEqualTo(ZenOutcome.NotApplied)
        assertThat(zen.storedCondition(id)).isNull()
    }

    @Test
    fun `an existing rule of ours is adopted`() = runTest {
        val existing = zen.addOurRule()

        controller.activate()

        assertThat(zen.ourRules().keys).containsExactly(existing)
        assertThat(zen.stateOf(existing)).isEqualTo(Condition.STATE_TRUE)
    }

    @Test
    fun `duplicate rules of ours are reduced to one`() = runTest {
        repeat(3) { zen.addOurRule() }

        controller.activate()

        assertThat(zen.ourRules()).hasSize(1)
    }

    @Test
    fun `rules of other apps and other conditions are untouched`() = runTest {
        val otherApp = zen.addForeignRule(ZEN_CONDITION_ID, "com.example.other")
        val otherCondition = zen.addForeignRule(Uri.parse("focustag://zen/other"), "io.github.fbarcalar.focustag")

        controller.activate()

        assertThat(zen.notifications.automaticZenRules.keys).containsAtLeast(otherApp, otherCondition)
        assertThat(zen.storedCondition(otherApp)).isNull()
        assertThat(zen.storedCondition(otherCondition)).isNull()
    }

    @Test
    fun `a rule switched off externally is re-activated`() = runTest {
        controller.activate()
        val id = zen.ourRules().keys.single()
        zen.setState(id, Condition.STATE_FALSE)

        val outcome = controller.activate()

        assertThat(outcome).isEqualTo(ZenOutcome.Applied)
        assertThat(zen.stateOf(id)).isEqualTo(Condition.STATE_TRUE)
    }

    @Test
    fun `a rule deleted externally is re-created`() = runTest {
        controller.activate()
        zen.notifications.removeAutomaticZenRule(zen.ourRules().keys.single())

        controller.activate()

        val id = zen.ourRules().keys.single()
        assertThat(zen.stateOf(id)).isEqualTo(Condition.STATE_TRUE)
    }

    @Test
    fun `deactivation switches the rule off`() = runTest {
        controller.activate()

        val outcome = controller.deactivate()

        assertThat(outcome).isEqualTo(ZenOutcome.Applied)
        assertThat(zen.stateOf(zen.ourRules().keys.single())).isEqualTo(Condition.STATE_FALSE)
    }

    @Test
    fun `deactivation without a rule creates nothing`() = runTest {
        val outcome = controller.deactivate()

        assertThat(outcome).isEqualTo(ZenOutcome.Applied)
        assertThat(zen.ourRules()).isEmpty()
    }

    @Test
    fun `activation without policy access is denied and creates nothing`() = runTest {
        zen.setPolicyAccess(false)

        val outcome = controller.activate()

        zen.setPolicyAccess(true)
        assertThat(outcome).isEqualTo(ZenOutcome.AccessDenied)
        assertThat(zen.ourRules()).isEmpty()
    }

    @Test
    fun `deactivation without policy access is denied`() = runTest {
        zen.setPolicyAccess(false)

        assertThat(controller.deactivate()).isEqualTo(ZenOutcome.AccessDenied)
    }
}
