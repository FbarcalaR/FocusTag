package io.github.fbarcalar.focustag.blocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidAlwaysAllowedResolverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = AndroidAlwaysAllowedResolver(context)

    @Test
    fun `our own app is always allowed`() {
        assertThat(resolver.resolve()).contains(context.packageName)
    }

    @Test
    fun `system ui settings and the phone process are always allowed`() {
        assertThat(resolver.resolve()).containsAtLeast("com.android.systemui", "com.android.settings", "com.android.phone")
    }

    @Test
    fun `the default launcher is always allowed`() {
        registerDefaultActivity("com.example.launcher", homeFilter())

        assertThat(resolver.resolve()).contains("com.example.launcher")
    }

    @Test
    fun `the settings app is always allowed`() {
        registerDefaultActivity("com.example.settings", IntentFilter(Settings.ACTION_SETTINGS))

        assertThat(resolver.resolve()).contains("com.example.settings")
    }

    @Test
    fun `the default and system dialers are always allowed`() {
        val telecom = shadowOf(context.getSystemService(TelecomManager::class.java))
        telecom.setDefaultDialerPackage("com.example.dialer")
        telecom.setSystemDialerPackage("com.example.systemdialer")

        assertThat(resolver.resolve()).containsAtLeast("com.example.dialer", "com.example.systemdialer")
    }

    @Test
    fun `enabled keyboards are always allowed`() {
        val keyboard = InputMethodInfo("com.example.keyboard", "com.example.keyboard.Ime", "Keyboard", null)
        shadowOf(context.getSystemService(InputMethodManager::class.java)).setEnabledInputMethodInfoList(listOf(keyboard))

        assertThat(resolver.resolve()).contains("com.example.keyboard")
    }

    @Test
    fun `an ordinary app is not always allowed`() {
        assertThat(resolver.resolve()).doesNotContain("com.example.blocked")
    }

    private fun registerDefaultActivity(packageName: String, filter: IntentFilter) {
        val component = ComponentName(packageName, "$packageName.Main")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(component, filter.apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    private fun homeFilter() = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
}
