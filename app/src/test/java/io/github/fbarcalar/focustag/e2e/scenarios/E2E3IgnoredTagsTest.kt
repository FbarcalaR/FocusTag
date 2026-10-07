package io.github.fbarcalar.focustag.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** E2E-3 (T2 + T3): unknown tags, copied URIs on another UID and foreign URIs are ignored (D-12). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E3IgnoredTagsTest : FocusTagE2E() {
    @Test
    fun `invalid tags never start focus`() {
        pairTags()

        scanUnknownTag()
        scanTagWithWrongUid(TagRole.ACTIVATE)
        scanForeignUri()

        assertModeStays(FocusMode.FREE)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `invalid tags never end focus`() {
        pairTags()
        scanTag(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)

        scanUnknownTag()
        scanTagWithWrongUid(TagRole.DEACTIVATE)
        scanForeignUri()

        assertModeStays(FocusMode.FOCUS)
    }

    /** Ignored scans leave no trace to wait for, so the mode is watched for a while instead. */
    private fun assertModeStays(mode: FocusMode) {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < WATCH) {
            idle()
            assertThat(runBlocking { graph.focusStateReader().state.first().mode }).isEqualTo(mode)
            Thread.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        val WATCH = 500.milliseconds
        const val POLL_MILLIS = 20L
    }
}
