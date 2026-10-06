package io.github.fbarcalar.focustag.e2e

import android.os.Looper
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows.shadowOf

private const val POLL_MILLIS = 10L

/** Runs everything queued on the (paused) Robolectric main looper. */
internal fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

/**
 * Retries [assertion], idling the main looper in between, until it passes or [timeout] ends.
 * Each attempt is bounded by the remaining time, so a suspended assertion cannot hang the test.
 * E2E uses real I/O dispatchers for DataStore, so there is no virtual time to advance.
 */
internal fun retryUntilPasses(timeout: Duration, assertion: suspend () -> Unit) {
    val start = TimeSource.Monotonic.markNow()
    while (true) {
        idleMainLooper()
        val remaining = timeout - start.elapsedNow()
        val failure = runCatching { runBlocking { withTimeout(remaining) { assertion() } } }.exceptionOrNull() ?: return
        if (start.elapsedNow() >= timeout) throw failure
        Thread.sleep(POLL_MILLIS)
    }
}
