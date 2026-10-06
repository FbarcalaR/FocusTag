package io.github.fbarcalar.focustag.e2e

import android.os.Looper
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf

private const val POLL_MILLIS = 10L

/** Runs everything queued on the (paused) Robolectric main looper. */
internal fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

/**
 * Retries [assertion], idling the main looper in between, until it passes or [timeout] ends.
 * E2E uses real I/O dispatchers for DataStore, so there is no virtual time to advance.
 */
internal fun retryUntilPasses(timeout: Duration, assertion: suspend () -> Unit) {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (true) {
        idleMainLooper()
        val failure = runCatching { runBlocking { assertion() } }.exceptionOrNull() ?: return
        if (deadline.hasPassedNow()) throw failure
        Thread.sleep(POLL_MILLIS)
    }
}
