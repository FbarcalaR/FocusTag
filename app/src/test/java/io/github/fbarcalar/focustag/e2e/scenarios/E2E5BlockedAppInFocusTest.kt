package io.github.fbarcalar.focustag.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** E2E-5 (T2 + T5): in FOCUS a blocked app gets the blocking screen and an allowed app does not. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E5BlockedAppInFocusTest : FocusTagE2E() {
    @Before
    fun startFocusWithABlockList() {
        runBlocking { graph.blockListRepository().add(BLOCKED) }
        grant(SystemGrant.NOTIFICATION_POLICY)
        startApp()
        scanTagDirect(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)
    }

    @Test
    fun `opening a blocked app in focus shows the blocking screen`() {
        openApp(BLOCKED)

        assertBlockingShown(BLOCKED)
    }

    @Test
    fun `opening an allowed app in focus shows nothing`() {
        openApp(ALLOWED)

        assertNothingBlocked()
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
        const val ALLOWED = "com.example.allowed"
    }
}
