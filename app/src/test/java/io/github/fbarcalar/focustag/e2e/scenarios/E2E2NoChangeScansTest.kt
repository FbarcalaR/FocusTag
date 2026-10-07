package io.github.fbarcalar.focustag.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.TagRole
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** E2E-2 (T2 + T3): a second desk scan, or the living-room tag while FREE, changes nothing. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E2NoChangeScansTest : FocusTagE2E() {
    @Test
    fun `living room tag while free keeps free time and today's total`() {
        pairTags()

        scanTag(TagRole.DEACTIVATE)

        awaitToast(count = 1, text = "Already in free time")
        assertMode(FocusMode.FREE)
        eventually { assertThat(graph.focusStateReader().stats.first().todayTotal).isEqualTo(Duration.ZERO) }
    }

    @Test
    fun `a second desk scan keeps the session that is already running`() {
        pairTags()
        scanTag(TagRole.ACTIVATE)
        awaitToast(count = 1, text = "Focus on")
        val session = currentState()
        advanceClock(5.minutes)

        scanTag(TagRole.ACTIVATE)

        awaitToast(count = 2, text = "Already in focus")
        assertThat(currentState()).isEqualTo(session)
        assertThat(session.mode).isEqualTo(FocusMode.FOCUS)
    }

    private fun currentState(): FocusState {
        var state: FocusState = FocusState.Free
        eventually { state = graph.focusStateReader().state.first() }
        return state
    }

    /** The toast is shown after the scan was fully processed, so it doubles as a barrier. */
    private fun awaitToast(count: Int, text: String) = eventually {
        assertThat(ShadowToast.shownToastCount()).isEqualTo(count)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(text)
    }
}
