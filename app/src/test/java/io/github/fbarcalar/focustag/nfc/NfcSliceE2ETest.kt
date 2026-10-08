package io.github.fbarcalar.focustag.nfc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.HarnessTags
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowToast

/** Slice E2E (PLAN §2.2): NDEF intents through the real `NfcTriggerActivity` into the focus layer. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NfcSliceE2ETest : FocusTagE2E() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NfcSliceEntryPoint {
        fun tagWriter(): TagWriter
    }

    @Test
    fun `scanning tag A while free starts focus with one toast`() {
        pairTags()

        trigger(scanOf(HarnessTags.A))

        assertMode(FocusMode.FOCUS)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Focus on")
    }

    @Test
    fun `scanning tag A twice reports already in focus`() {
        pairTags()
        trigger(scanOf(HarnessTags.A))

        trigger(scanOf(HarnessTags.A))

        assertMode(FocusMode.FOCUS)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(2)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Already in focus")
    }

    @Test
    fun `scanning tag B in focus returns to free time`() {
        pairTags()
        trigger(scanOf(HarnessTags.A))

        trigger(scanOf(HarnessTags.B))

        assertMode(FocusMode.FREE)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Free time")
    }

    @Test
    fun `unknown, copied and foreign tags are ignored silently`() {
        pairTags()

        trigger(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.uriFor(HarnessTags.UNKNOWN_TAG_ID)))
        trigger(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.uriFor(HarnessTags.A.tagId)))
        trigger(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.FOREIGN_URI))

        assertMode(FocusMode.FREE)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `a scan before any pairing is ignored`() {
        trigger(scanOf(HarnessTags.A))

        assertMode(FocusMode.FREE)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `an intent without a tag just finishes`() {
        pairTags()

        trigger(scan = null)

        assertMode(FocusMode.FREE)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `a re-created trigger does not process the intent again`() {
        pairTags()
        val gateway = graph.fakeNfcGateway()
        gateway.enqueueRead(scanOf(HarnessTags.A))

        val activity = Robolectric.buildActivity(NfcTriggerActivity::class.java, ndefIntent(HarnessTags.A.tagId))
            .create(Bundle()).get()

        assertThat(activity.isFinishing).isTrue()
        assertThat(gateway.readTag(Intent())).isEqualTo(scanOf(HarnessTags.A))
        assertMode(FocusMode.FREE)
    }

    @Test
    fun `re-pairing tag A invalidates the old tag and the new one starts focus`() {
        pairTags()
        val writer = EntryPointAccessors.fromApplication(app, NfcSliceEntryPoint::class.java).tagWriter()
        val handle = FakeTagHandle(ScannedTag(HarnessTags.A.uidHex, emptyList()))

        val repaired = runBlocking { writer.pair(handle, TagRole.ACTIVATE) } as PairingResult.Paired
        trigger(scanOf(HarnessTags.A))
        assertMode(FocusMode.FREE)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)

        trigger(scanOf(repaired.pairing))
        assertMode(FocusMode.FOCUS)
    }

    private fun scanOf(pairing: TagPairing) = HarnessTags.scanOf(pairing.uidHex, HarnessTags.uriFor(pairing.tagId))

    private fun ndefIntent(tagId: String) = Intent(HarnessTags.ACTION_NDEF_DISCOVERED, Uri.parse(HarnessTags.uriFor(tagId)))
        .setClass(app, NfcTriggerActivity::class.java)

    /** Delivers [scan] like `deliverScan`, then waits until the trigger finished its single job. */
    private fun trigger(scan: ScannedTag?) {
        graph.fakeNfcGateway().enqueueRead(scan)
        val activity = Robolectric.buildActivity(NfcTriggerActivity::class.java, ndefIntent(HarnessTags.A.tagId))
            .setup().get()
        eventually { assertThat(activity.isFinishing).isTrue() }
    }
}
