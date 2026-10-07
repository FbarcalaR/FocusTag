package io.github.fbarcalar.focustag.blocker

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dagger.hilt.android.AndroidEntryPoint
import io.github.fbarcalar.focustag.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

/** Thin adapter from window events to [ForegroundAppGuard] (D-20). */
@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var guardFactory: ForegroundAppGuard.Factory

    @Inject
    @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    private lateinit var serviceScope: CoroutineScope
    private lateinit var guard: ForegroundAppGuard

    override fun onCreate() {
        super.onCreate()
        // A child of the app scope (and its background dispatcher), so ending the process also ends
        // this collector without needing the main thread, which a blocked test thread may hold.
        serviceScope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))
        guard = guardFactory.create(::showBlockingScreen) { rootInActiveWindow?.packageName?.toString() }
        guard.start(serviceScope)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = guard.onForeground(foregroundPackages(event))

    override fun onInterrupt() = Unit

    private fun foregroundPackages(event: AccessibilityEvent): List<String> = when (event.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> listOfNotNull(event.packageName?.toString())
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> visibleAppPackages()
        else -> emptyList()
    }

    private fun visibleAppPackages(): List<String> = windows
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        .mapNotNull { it.root?.packageName?.toString() }

    private fun showBlockingScreen(packageName: String) {
        try {
            startActivity(BlockingActivity.intent(this, packageName))
        } catch (_: ActivityNotFoundException) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (_: SecurityException) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }
}
