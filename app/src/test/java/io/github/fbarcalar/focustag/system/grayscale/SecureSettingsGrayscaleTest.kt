package io.github.fbarcalar.focustag.system.grayscale

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SecureSettingsGrayscaleTest {
    @get:Rule
    val storeRule = SystemStoreRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val settings = DaltonizerSettings(app)
    private val previous = DaltonizerValues(enabled = "0", mode = "12")

    private fun grayscale() = SecureSettingsGrayscale(app, settings, DaltonizerSnapshotStore(storeRule.store))

    private fun setGrant(granted: Boolean) = shadowOf(app).run {
        if (granted) grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS) else denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    @Test
    fun `enabling without the grant is refused and leaves settings untouched`() = runTest {
        settings.write(previous)

        val outcome = grayscale().enable()

        assertThat(outcome).isEqualTo(FallbackOutcome.NotGranted)
        assertThat(settings.read()).isEqualTo(previous)
    }

    @Test
    fun `enabling writes grayscale`() = runTest {
        setGrant(true)
        settings.write(previous)

        val outcome = grayscale().enable()

        assertThat(outcome).isEqualTo(FallbackOutcome.Applied)
        assertThat(settings.read()).isEqualTo(DaltonizerValues.GRAYSCALE)
    }

    @Test
    fun `disabling restores the exact previous values`() = runTest {
        setGrant(true)
        settings.write(previous)
        val grayscale = grayscale()
        grayscale.enable()

        grayscale.disable()

        assertThat(settings.read()).isEqualTo(previous)
    }

    @Test
    fun `previously unset keys are restored as unset`() = runTest {
        setGrant(true)
        settings.write(DaltonizerValues(enabled = null, mode = null))
        val grayscale = grayscale()
        grayscale.enable()

        grayscale.disable()

        assertThat(settings.read()).isEqualTo(DaltonizerValues(enabled = null, mode = null))
    }

    @Test
    fun `enabling twice keeps the original snapshot`() = runTest {
        setGrant(true)
        settings.write(previous)
        val grayscale = grayscale()
        grayscale.enable()
        grayscale.enable()

        grayscale.disable()

        assertThat(settings.read()).isEqualTo(previous)
    }

    @Test
    fun `disabling without a snapshot changes nothing`() = runTest {
        setGrant(true)
        settings.write(previous)

        val outcome = grayscale().disable()

        assertThat(outcome).isEqualTo(FallbackOutcome.Applied)
        assertThat(settings.read()).isEqualTo(previous)
    }

    @Test
    fun `a new process restores the snapshot of the previous one`() = runTest {
        setGrant(true)
        settings.write(previous)
        grayscale().enable()
        storeRule.reopen()

        grayscale().disable()

        assertThat(settings.read()).isEqualTo(previous)
    }

    @Test
    fun `disabling after the grant is lost keeps the snapshot for later`() = runTest {
        setGrant(true)
        settings.write(previous)
        grayscale().enable()
        setGrant(false)

        val outcome = grayscale().disable()
        setGrant(true)
        grayscale().disable()

        assertThat(outcome).isEqualTo(FallbackOutcome.NotGranted)
        assertThat(settings.read()).isEqualTo(previous)
    }

    @Test
    fun `colour correction keys are the hidden secure settings`() {
        settings.write(DaltonizerValues.GRAYSCALE)

        val resolver = app.contentResolver
        assertThat(Settings.Secure.getString(resolver, "accessibility_display_daltonizer_enabled")).isEqualTo("1")
        assertThat(Settings.Secure.getString(resolver, "accessibility_display_daltonizer")).isEqualTo("0")
    }
}
