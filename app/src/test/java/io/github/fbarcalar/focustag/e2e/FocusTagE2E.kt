package io.github.fbarcalar.focustag.e2e

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import io.github.fbarcalar.focustag.MainActivity
import io.github.fbarcalar.focustag.blocker.FocusAccessibilityService
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.testing.cancelApplicationScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric

/**
 * Base class for E2E scenarios (PLAN §2.2, R6). Subclasses add `@HiltAndroidTest` and
 * `@RunWith(AndroidJUnit4::class)`. Only hardware/OS edges are faked; the graph is the real one.
 * Every Act/Assert call starts the simulated process, which closes the [seedPreferences] window.
 */
abstract class FocusTagE2E {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    protected val app: Application = ApplicationProvider.getApplicationContext()
    private val zenRules = ZenRules(app)
    private val startedActivities = StartedActivities(app)
    private val scenarios = mutableListOf<ActivityScenario<*>>()
    private val accessibilityService by lazy { Robolectric.buildService(FocusAccessibilityService::class.java).create().get() }
    private var processStarted = false

    /** The real graph; resolving it starts the simulated process. */
    protected val graph: HarnessEntryPoint
        get() {
            processStarted = true
            return EntryPointAccessors.fromApplication(app, HarnessEntryPoint::class.java)
        }

    @Before
    fun injectHilt() = hiltRule.inject()

    @After
    fun endProcess() {
        scenarios.forEach { it.close() }
        if (processStarted) cancelApplicationScope(app)
    }

    // ---- Arrange ---------------------------------------------------------------------------

    /**
     * Writes [file] as a previous process would have left it (cold-start seam). Must run before
     * the process starts; the DataStore is closed afterwards so the app can open the file.
     */
    protected fun seedPreferences(file: String, block: suspend (DataStore<Preferences>) -> Unit) {
        check(!processStarted) { "seedPreferences must run before any Act/Assert call" }
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO + Job())
            val store = PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(file) }
            try {
                block(store)
            } finally {
                scope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    protected fun pairTags() = runBlocking {
        graph.pairingRepository().save(HarnessTags.A)
        graph.pairingRepository().save(HarnessTags.B)
        idle()
    }

    protected fun grant(grant: SystemGrant) = setGrant(grant, granted = true)

    protected fun revoke(grant: SystemGrant) = setGrant(grant, granted = false)

    protected fun advanceClock(duration: Duration) = graph.fakeClock().advanceBy(duration)

    // ---- Act -------------------------------------------------------------------------------

    /** Runs the app-start hooks exactly as `FocusTagApp.onCreate` does. */
    protected fun startApp() {
        graph.appStartRunner().run()
        idle()
    }

    protected fun openMainUi() {
        processStarted = true
        scenarios += ActivityScenario.launch(MainActivity::class.java)
        idle()
    }

    protected fun scanTag(role: TagRole) {
        scan(HarnessTags.scanOf(HarnessTags.of(role).uidHex, HarnessTags.uriFor(HarnessTags.tagIdOf(role))))
    }

    protected fun scanTagWithWrongUid(role: TagRole) =
        scan(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.uriFor(HarnessTags.tagIdOf(role))))

    protected fun scanUnknownTag() =
        scan(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.uriFor(HarnessTags.UNKNOWN_TAG_ID)))

    protected fun scanForeignUri() = scan(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.FOREIGN_URI))

    /** A card with no NDEF data, as the `TECH_DISCOVERED` alias delivers it (D-62). */
    protected fun scanCard(uidHex: String) = scan(ScannedTag(uidHex, emptyList()))

    /** Calls the engine directly, bypassing NFC (for slices that run before T3 lands). */
    protected fun scanTagDirect(role: TagRole) = runBlocking { graph.focusController().onTagScanned(role) }

    protected fun openApp(packageName: String) {
        processStarted = true
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            this.packageName = packageName
            className = "$packageName.MainActivity"
        }
        accessibilityService.onAccessibilityEvent(event)
        idle()
    }

    protected fun turnZenRuleOffExternally() {
        processStarted = true
        zenRules.switchAllOff()
        zenRules.broadcastStatusChanged()
        idle()
    }

    /** Volatile OS state is lost, then BOOT_COMPLETED is delivered. Does not run [startApp]. */
    protected fun reboot() {
        processStarted = true
        zenRules.switchAllOff()
        app.getSystemService(NotificationManager::class.java).cancelAll()
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName))
        idle()
    }

    // ---- Assert ----------------------------------------------------------------------------

    protected fun assertMode(mode: FocusMode) = eventually {
        assertThat(graph.focusStateReader().state.first().mode).isEqualTo(mode)
    }

    protected fun assertZenRuleActive(active: Boolean) = eventually { assertThat(zenRules.anyActive()).isEqualTo(active) }

    protected fun assertEffectsDegraded(degraded: Boolean) = eventually {
        assertThat(graph.focusStateReader().effectsStatus.value.isDegraded).isEqualTo(degraded)
    }

    protected fun assertBlockingShown(packageName: String) = eventually {
        assertThat(startedActivities.blockedPackages()).contains(packageName)
    }

    protected fun assertNothingBlocked() {
        processStarted = true
        idle()
        assertThat(startedActivities.blockedPackages()).isEmpty()
    }

    // ---- Plumbing (see Polling.kt, TagScans.kt) ----------------------------------------------

    protected fun idle() = idleMainLooper()

    /** Retries [assertion] until it passes or [timeout] ends; starts the simulated process. */
    protected fun eventually(timeout: Duration = 5.seconds, assertion: suspend () -> Unit) {
        processStarted = true
        retryUntilPasses(timeout, assertion)
    }

    private fun scan(scan: ScannedTag) = deliverScan(app, graph.fakeNfcGateway(), scan)

    private fun setGrant(grant: SystemGrant, granted: Boolean) {
        grant.apply(granted, app, graph.fakeNfcGateway())
        graph.permissionChecker().refresh()
        idle()
    }
}
