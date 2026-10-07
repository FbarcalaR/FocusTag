package io.github.fbarcalar.focustag.blocker

import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Collapses the several triggers one app open produces (window events, state re-emissions) into
 * one launch of the blocking screen. Not thread-safe; [ForegroundAppGuard] serialises its calls.
 */
class RelaunchGuard @Inject constructor(private val clock: Clock) {
    private var lastPackage: String? = null
    private var lastLaunch: Instant = Instant.MIN

    /** True if the screen may be shown for [packageName] now; a true answer records the launch. */
    fun shouldLaunch(packageName: String): Boolean {
        val now = clock.instant()
        val isRepeat = packageName == lastPackage && Duration.between(lastLaunch, now) < WINDOW
        if (isRepeat) return false
        lastPackage = packageName
        lastLaunch = now
        return true
    }

    private companion object {
        val WINDOW: Duration = Duration.ofMillis(500)
    }
}
