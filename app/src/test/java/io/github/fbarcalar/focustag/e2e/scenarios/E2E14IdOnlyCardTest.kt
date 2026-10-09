package io.github.fbarcalar.focustag.e2e.scenarios

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.HarnessTags
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.IdOnlyScanSwitch
import io.github.fbarcalar.focustag.nfc.TagPairing
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** E2E-14 (D-62): a card that can't be written, paired by its ID, ends focus; nothing else does. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E14IdOnlyCardTest : FocusTagE2E() {
    private val transportCard = TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21")

    @Test
    fun `a card paired by id ends focus and turns card scans on`() {
        pairStickerAndCard()
        startApp()
        scanTag(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)

        scanCard(transportCard.uidHex)

        assertMode(FocusMode.FREE)
        eventually { assertThat(cardScanAliasState()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED) }
    }

    @Test
    fun `an unknown card or a sticker's bare uid never ends focus`() {
        pairStickerAndCard()
        scanTag(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)
        val toastsBefore = ShadowToast.shownToastCount()

        scanCard("5A000000")
        scanCard(HarnessTags.A.uidHex)

        assertModeStays(FocusMode.FOCUS)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(toastsBefore)
    }

    /** Ignored scans leave no trace to wait for, so the mode is watched for a while instead (as in E2E-3). */
    private fun assertModeStays(mode: FocusMode) {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < WATCH) {
            idle()
            assertThat(runBlocking { graph.focusStateReader().state.first().mode }).isEqualTo(mode)
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun pairStickerAndCard() = runBlocking {
        graph.pairingRepository().save(HarnessTags.A)
        graph.pairingRepository().save(transportCard)
    }

    private fun cardScanAliasState() = app.packageManager.getComponentEnabledSetting(IdOnlyScanSwitch.component(app))

    private companion object {
        val WATCH = 500.milliseconds
        const val POLL_MILLIS = 20L
    }
}
