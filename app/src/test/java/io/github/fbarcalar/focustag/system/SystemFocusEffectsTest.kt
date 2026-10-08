package io.github.fbarcalar.focustag.system

import android.Manifest
import android.app.Application
import android.service.notification.Condition
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.system.grayscale.DaltonizerSettings
import io.github.fbarcalar.focustag.system.grayscale.DaltonizerSnapshotStore
import io.github.fbarcalar.focustag.system.grayscale.DaltonizerValues
import io.github.fbarcalar.focustag.system.grayscale.DataStoreGrayscaleFallbackSettings
import io.github.fbarcalar.focustag.system.grayscale.SecureSettingsGrayscale
import io.github.fbarcalar.focustag.system.grayscale.SystemStoreRule
import io.github.fbarcalar.focustag.system.zen.ZenRuleController
import io.github.fbarcalar.focustag.system.zen.ZenTestSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SystemFocusEffectsTest {
    @get:Rule
    val storeRule = SystemStoreRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val zen = ZenTestSupport(app)
    private val daltonizer = DaltonizerSettings(app)
    private val previous = DaltonizerValues(enabled = "0", mode = "12")
    private val fallback by lazy { DataStoreGrayscaleFallbackSettings(storeRule.store) }
    private val effects by lazy {
        SystemFocusEffects(
            ZenRuleController(app, zen.spec, Dispatchers.IO),
            SecureSettingsGrayscale(app, daltonizer, DaltonizerSnapshotStore(storeRule.store)),
            fallback,
        )
    }

    private fun grantSecureSettings() = shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)

    private fun ruleState(): Int = zen.stateOf(zen.ourRules().keys.single())

    @Test
    fun `enabling with DND access is healthy and switches the rule on`() = runTest {
        zen.setPolicyAccess(true)

        val status = effects.enable()

        assertThat(status).isEqualTo(EffectsStatus())
        assertThat(ruleState()).isEqualTo(Condition.STATE_TRUE)
    }

    @Test
    fun `enabling without DND access fails only the zen rule`() = runTest {
        val status = effects.enable()

        assertThat(status.failed).containsExactly(Effect.ZEN_RULE)
    }

    @Test
    fun `the fallback without its grant fails only the fallback`() = runTest {
        zen.setPolicyAccess(true)
        fallback.setEnabled(true)

        val status = effects.enable()

        assertThat(status.failed).containsExactly(Effect.GRAYSCALE_FALLBACK)
    }

    @Test
    fun `the granted fallback applies grayscale and disabling restores it`() = runTest {
        zen.setPolicyAccess(true)
        grantSecureSettings()
        daltonizer.write(previous)
        fallback.setEnabled(true)

        effects.enable()
        val during = daltonizer.read()
        effects.disable()

        assertThat(during).isEqualTo(DaltonizerValues.GRAYSCALE)
        assertThat(daltonizer.read()).isEqualTo(previous)
    }

    @Test
    fun `a fallback switched off mid-session is restored on the next enable`() = runTest {
        zen.setPolicyAccess(true)
        grantSecureSettings()
        daltonizer.write(previous)
        fallback.setEnabled(true)
        effects.enable()

        fallback.setEnabled(false)
        effects.enable()

        assertThat(daltonizer.read()).isEqualTo(previous)
    }

    @Test
    fun `disabling switches the rule off and is healthy`() = runTest {
        zen.setPolicyAccess(true)
        effects.enable()

        val status = effects.disable()

        assertThat(status).isEqualTo(EffectsStatus())
        assertThat(ruleState()).isEqualTo(Condition.STATE_FALSE)
    }

    @Test
    fun `concurrent enables leave exactly one rule`() = runTest {
        zen.setPolicyAccess(true)

        withContext(Dispatchers.Default) { List(20) { async { effects.enable() } }.awaitAll() }

        assertThat(zen.ourRules()).hasSize(1)
    }

    @Test
    fun `a repeated enable writes no zen state`() = runTest {
        zen.setPolicyAccess(true)
        effects.enable()
        val before = zen.storedCondition(zen.ourRules().keys.single())

        effects.enable()

        assertThat(zen.storedCondition(zen.ourRules().keys.single())).isSameInstanceAs(before)
    }
}
