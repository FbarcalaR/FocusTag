package io.github.fbarcalar.focustag.e2e

import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Checks the harness itself; asserts only facts that hold with placeholders and real layers alike. */
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
    fun `seeding after an act call fails`() {
        openApp("com.example.any")

        assertThrows(IllegalStateException::class.java) { seedPreferences(PROBE_FILE) {} }
    }

    @Test
    fun `pairing tags stores both harness pairings`() {
        pairTags()

        val pairings = runBlocking { graph.pairingRepository().pairings.first() }
        assertThat(pairings.values).containsExactly(HarnessTags.A, HarnessTags.B)
    }

    @Test
    fun `reboot delivers boot completed to our package`() {
        reboot()

        val boot = shadowOf(app).broadcastIntents.filter { it.action == Intent.ACTION_BOOT_COMPLETED }
        assertThat(boot.map { it.`package` }).containsExactly(app.packageName)
    }

    @Test
    fun `every act call runs without crashing and blocks nothing unrelated`() {
        startApp()
        scanUnknownTag()
        scanForeignUri()
        openApp("com.example.notblocked")
        turnZenRuleOffExternally()

        assertNothingBlocked()
    }

    @Test
    fun `revoking nfc reaches the fake gateway`() {
        revoke(SystemGrant.NFC)

        assertThat(graph.fakeNfcGateway().availability.value).isEqualTo(NfcAvailability.DISABLED)
    }

    private companion object {
        const val PROBE_FILE = "harness_probe"
    }
}
