package io.github.fbarcalar.focustag.e2e.scenarios

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.HarnessTags
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.TapSource
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** E2E-15 (D-65): with Status open, FocusTag reads tags itself, whatever Android's background dispatch does. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E15InAppScanTest : FocusTagE2E() {
    private val transportCard = TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21")
    private val sticker = FakeTagHandle(HarnessTags.scanOf(HarnessTags.A.uidHex, HarnessTags.uriFor(HarnessTags.tagIdOf(TagRole.ACTIVATE))))

    @Test
    fun `tags tapped while status is open toggle focus and are logged as read in the app`() {
        pairStickerAndCard()
        openStatus()

        present(sticker)
        assertMode(FocusMode.FOCUS)
        awaitToast(R.string.nfc_scan_focus_on)

        present(FakeTagHandle(ScannedTag(transportCard.uidHex, emptyList())))
        assertMode(FocusMode.FREE)
        eventually { assertThat(graph.tapLog().lastTap.first()?.source).isEqualTo(TapSource.IN_APP) }
    }

    @Test
    fun `an unknown tag read in the app says so and changes nothing`() {
        pairStickerAndCard()
        openStatus()

        present(FakeTagHandle(ScannedTag("5A000000", emptyList())))

        awaitToast(R.string.nfc_scan_not_recognised)
        assertThat(runBlocking { graph.focusStateReader().state.first().mode }).isEqualTo(FocusMode.FREE)
    }

    private fun openStatus() {
        openMainUi()
        composeRule.waitUntilExactlyOneExists(hasText(app.getString(R.string.status_mode_free)), TIMEOUT_MILLIS)
        eventually { assertThat(graph.fakeNfcGateway().readerModeEnabled).isTrue() }
    }

    /** The toast is shown from composition, so Compose frames must run while waiting. */
    private fun awaitToast(message: Int) =
        composeRule.waitUntil(TIMEOUT_MILLIS) { ShadowToast.getTextOfLatestToast() == app.getString(message) }

    private fun present(tag: FakeTagHandle) {
        graph.fakeNfcGateway().present(tag)
        idle()
    }

    private fun pairStickerAndCard() = runBlocking {
        graph.pairingRepository().save(HarnessTags.A)
        graph.pairingRepository().save(transportCard)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
