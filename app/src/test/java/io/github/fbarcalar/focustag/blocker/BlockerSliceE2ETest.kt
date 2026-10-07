package io.github.fbarcalar.focustag.blocker

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.StartedActivities
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** The blocker driven from its accessibility-service entry point through the real graph (PLAN §2.2). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockerSliceE2ETest : FocusTagE2E() {
    private fun block(packageName: String) = runBlocking { graph.blockListRepository().add(packageName) }

    private fun startFocus() {
        scanTagDirect(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)
    }

    @Test
    fun `opening a blocked app in focus shows the blocking screen`() {
        block(BLOCKED)
        startFocus()

        openApp(BLOCKED)

        assertBlockingShown(BLOCKED)
    }

    @Test
    fun `opening an unlisted app in focus blocks nothing`() {
        block(BLOCKED)
        startFocus()

        openApp(OTHER)

        assertNothingBlocked()
    }

    @Test
    fun `opening a blocked app in free blocks nothing`() {
        block(BLOCKED)

        openApp(BLOCKED)

        assertNothingBlocked()
    }

    @Test
    fun `a blocked app already open when focus starts is blocked`() {
        block(BLOCKED)
        openApp(BLOCKED)

        startFocus()

        assertBlockingShown(BLOCKED)
    }

    @Test
    fun `our own app is never blocked even when listed`() {
        block(app.packageName)
        startFocus()

        openApp(app.packageName)

        assertNothingBlocked()
    }

    @Test
    fun `the blocking screen's own window does not trigger another block`() {
        block(BLOCKED)
        startFocus()
        openApp(BLOCKED)
        assertBlockingShown(BLOCKED)

        advanceClock(1.seconds)
        val laterStarts = StartedActivities(app)

        openApp(app.packageName)

        assertThat(laterStarts.blockedPackages()).isEmpty()
    }

    @Test
    fun `removing an app is refused in focus and allowed in free`() {
        block(BLOCKED)
        startFocus()
        val duringFocus = runBlocking { graph.blockListRepository().remove(BLOCKED) }
        scanTagDirect(TagRole.DEACTIVATE)
        assertMode(FocusMode.FREE)

        val duringFree = runBlocking { graph.blockListRepository().remove(BLOCKED) }

        assertThat(duringFocus).isEqualTo(RemoveResult.NotAllowedDuringFocus)
        assertThat(duringFree).isEqualTo(RemoveResult.Removed)
        assertThat(runBlocking { graph.blockListRepository().blockedPackages.first() }).isEmpty()
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
        const val OTHER = "com.example.other"
    }
}
