package io.github.fbarcalar.focustag.e2e

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
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
import io.github.fbarcalar.focustag.blocker.BlockingActivity
import io.github.fbarcalar.focustag.blocker.EXTRA_BLOCKED_PACKAGE
import io.github.fbarcalar.focustag.blocker.FocusAccessibilityService
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcTriggerActivity
import io.github.fbarcalar.focustag.nfc.ScannedTag
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
import org.robolectric.Shadows.shadowOf

/**
 * Base class for E2E scenarios (PLAN §2.2, R6). Subclasses add `@HiltAndroidTest` and
 * `@RunWith(AndroidJUnit4::class)`. Only hardware/OS edges are faked; the graph is the real one.
 */
abstract class FocusTagE2E {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    protected val app: Application = ApplicationProvider.getApplicationContext()
    private val zenRules = ZenRules(app)
    private val scenarios = mutableListOf<ActivityScenario<*>>()
    private val startedActivities = mutableListOf<Intent>()
    private val accessibilityService by lazy { Robolectric.buildService(FocusAccessibilityService::class.java).create().get() }
    private var graphTouched = false

    /** The real graph; resolving it ends the "before process start" window used by [seedPreferences]. */
    protected val graph: HarnessEntryPoint
        get() {
            graphTouched = true
            return EntryPointAccessors.fromApplication(app, HarnessEntryPoint::class.java)
        }

    @Before
    fun injectHilt() = hiltRule.inject()

    @After
    fun closeScenarios() = scenarios.forEach { it.close() }

    // ---- Arrange ---------------------------------------------------------------------------

    /**
     * Writes [file] as a previous process would have left it (cold-start seam). Must run before
     * the graph is touched; the DataStore is closed afterwards so the app can open the file.
     */
    protected fun seedPreferences(file: String, block: suspend (DataStore<Preferences>) -> Unit) {
        check(!graphTouched) { "seedPreferences must run before the graph is first used" }
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
        graphTouched = true
        scenarios += ActivityScenario.launch(MainActivity::class.java)
        idle()
    }

    protected fun scanTag(role: TagRole) {
        val tag = HarnessTags.of(role)
        deliverScan(HarnessTags.scanOf(tag.uidHex, HarnessTags.uriFor(tag.tagId)))
    }

    protected fun scanTagWithWrongUid(role: TagRole) {
        deliverScan(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.uriFor(HarnessTags.of(role).tagId)))
    }

    protected fun scanUnknownTag() {
        deliverScan(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.uriFor(HarnessTags.UNKNOWN_TAG_ID)))
    }

    protected fun scanForeignUri() = deliverScan(HarnessTags.scanOf(HarnessTags.UNKNOWN_UID, HarnessTags.FOREIGN_URI))

    /** Calls the engine directly, bypassing NFC (for slices that run before T3 lands). */
    protected fun scanTagDirect(role: TagRole) = runBlocking { graph.focusController().onTagScanned(role) }

    protected fun openApp(packageName: String) {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            this.packageName = packageName
            className = "$packageName.MainActivity"
        }
        accessibilityService.onAccessibilityEvent(event)
        idle()
    }

    protected fun turnZenRuleOffExternally() {
        zenRules.switchAllOff()
        zenRules.broadcastStatusChanged()
        idle()
    }

    /** Volatile OS state is lost, then BOOT_COMPLETED is delivered. Does not run [startApp]. */
    protected fun reboot() {
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
        assertThat(blockingIntents().map { it.getStringExtra(EXTRA_BLOCKED_PACKAGE) }).contains(packageName)
    }

    protected fun assertNothingBlocked() {
        idle()
        assertThat(blockingIntents()).isEmpty()
    }

    // ---- Plumbing --------------------------------------------------------------------------

    protected fun idle() = idleMainLooper()

    protected fun eventually(timeout: Duration = 5.seconds, assertion: suspend () -> Unit) =
        retryUntilPasses(timeout, assertion)

    private fun deliverScan(scan: ScannedTag) {
        graph.fakeNfcGateway().enqueueRead(scan)
        val intent = Intent(HarnessTags.ACTION_NDEF_DISCOVERED, Uri.parse(scan.ndefUris.first()))
            .setClass(app, NfcTriggerActivity::class.java)
        Robolectric.buildActivity(NfcTriggerActivity::class.java, intent).setup()
        idle()
    }

    private fun setGrant(grant: SystemGrant, granted: Boolean) {
        grant.apply(granted, app, graph.fakeNfcGateway())
        graph.permissionChecker().refresh()
        idle()
    }

    private fun blockingIntents(): List<Intent> {
        val shadowApp = shadowOf(app)
        generateSequence { shadowApp.nextStartedActivity }.forEach { startedActivities += it }
        return startedActivities.filter { it.component?.className == BlockingActivity::class.java.name }
    }
}
