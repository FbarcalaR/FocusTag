package io.github.fbarcalar.focustag.e2e

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HarnessSelfTest : FocusTagE2E() {
    private val key = stringPreferencesKey("probe")

    @Test
    fun `scanning tag A directly starts focus`() {
        scanTagDirect(TagRole.ACTIVATE)

        assertMode(FocusMode.FOCUS)
    }

    @Test
    fun `seeding the same file twice works because the first store is closed`() {
        seedPreferences(PROBE_FILE) { store -> store.edit { it[key] = "first" } }

        var read: String? = null
        seedPreferences(PROBE_FILE) { store -> read = store.data.first()[key] }

        assertThat(read).isEqualTo("first")
    }

    @Test
    fun `seeding after the graph was used fails`() {
        scanTagDirect(TagRole.ACTIVATE)

        assertThrows(IllegalStateException::class.java) { seedPreferences(PROBE_FILE) {} }
    }

    @Test
    fun `harness actions run against the placeholder graph without crashing`() {
        pairTags()
        startApp()
        scanTag(TagRole.ACTIVATE)
        openApp("com.example.blocked")
        reboot()

        assertMode(FocusMode.FREE)
        assertZenRuleActive(false)
        assertEffectsDegraded(false)
        assertNothingBlocked()
    }

    @Test
    fun `revoking a grant reaches the fakes`() {
        revoke(SystemGrant.NFC)

        assertThat(graph.fakeNfcGateway().availability.value.name).isEqualTo("DISABLED")
    }

    private companion object {
        const val PROBE_FILE = "harness_probe"
    }
}
