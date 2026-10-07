package io.github.fbarcalar.focustag.blocker

import android.app.Application
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.idleMainLooper
import io.github.fbarcalar.focustag.e2e.retryUntilPasses
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.cancelApplicationScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FocusAccessibilityServiceTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var focusController: FocusController

    @Inject
    lateinit var focusState: FocusStateReader

    @Inject
    lateinit var blockList: BlockListRepository

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<FocusAccessibilityService>
    private val service get() = controller.get()

    @Before
    fun setUp() {
        hiltRule.inject()
        controller = Robolectric.buildService(FocusAccessibilityService::class.java).create()
        runBlocking { blockList.add(BLOCKED) }
    }

    @After
    fun tearDown() = cancelApplicationScope(app)

    @Test
    fun `a blocked app in focus starts the blocking screen in a new task`() {
        startFocus()

        service.onAccessibilityEvent(windowStateChanged(BLOCKED))

        val started = awaitStartedActivity()
        assertThat(started.component?.className).isEqualTo(BlockingActivity::class.java.name)
        assertThat(started.getStringExtra(EXTRA_BLOCKED_PACKAGE)).isEqualTo(BLOCKED)
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `a blocked app window in split screen is blocked`() {
        startFocus()
        shadowOf(service).setWindows(listOf(appWindow(OTHER), appWindow(BLOCKED)))

        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOWS_CHANGED))

        assertThat(awaitStartedActivity().getStringExtra(EXTRA_BLOCKED_PACKAGE)).isEqualTo(BLOCKED)
    }

    @Test
    fun `unrelated event types are ignored`() {
        startFocus()

        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply { packageName = BLOCKED })
        idleMainLooper()

        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test
    fun `a destroyed service blocks nothing when focus starts`() {
        service.onAccessibilityEvent(windowStateChanged(BLOCKED))
        controller.destroy()

        startFocus()
        idleMainLooper()

        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    private fun startFocus() {
        runBlocking { focusController.onTagScanned(TagRole.ACTIVATE) }
        retryUntilPasses(5.seconds) { assertThat(focusState.state.first().mode).isEqualTo(FocusMode.FOCUS) }
    }

    private fun awaitStartedActivity(): Intent {
        var started: Intent? = null
        retryUntilPasses(5.seconds) {
            started = started ?: shadowOf(app).nextStartedActivity
            assertThat(started).isNotNull()
        }
        return checkNotNull(started)
    }

    private fun windowStateChanged(packageName: String) =
        AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { this.packageName = packageName }

    private fun appWindow(packageName: String): AccessibilityWindowInfo {
        val window = AccessibilityWindowInfo.obtain()
        shadowOf(window).setType(AccessibilityWindowInfo.TYPE_APPLICATION)
        shadowOf(window).setRoot(AccessibilityNodeInfo.obtain().apply { this.packageName = packageName })
        return window
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
        const val OTHER = "com.example.other"
    }
}
