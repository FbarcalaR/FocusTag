package io.github.fbarcalar.focustag.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** E2E-6 (T2 + T3 + T5): a blocked app already in the foreground is blocked when the desk tag starts FOCUS. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E6BlockedAppOpenWhenFocusStartsTest : FocusTagE2E() {
    @Test
    fun `a blocked app open in free is blocked once the desk tag starts focus`() {
        pairTags()
        runBlocking { graph.blockListRepository().add(BLOCKED) }
        grant(SystemGrant.NOTIFICATION_POLICY)
        openApp(BLOCKED)
        assertNothingBlocked()

        scanTag(TagRole.ACTIVATE)

        assertMode(FocusMode.FOCUS)
        assertBlockingShown(BLOCKED)
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
    }
}
