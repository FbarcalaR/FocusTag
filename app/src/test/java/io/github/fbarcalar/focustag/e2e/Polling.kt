package io.github.fbarcalar.focustag.e2e

import android.os.Looper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows.shadowOf

private const val POLL_MILLIS = 10L

/** One attempt blocks the main thread; app code waiting for Main (e.g. a DataStore edit) runs between attempts. */
private val ATTEMPT_TIMEOUT = 250.milliseconds

/** Runs everything queued on the (paused) Robolectric main looper. */
internal fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

/**
 * Retries [assertion], idling the main looper in between, until it passes or [timeout] ends.
 * Each attempt is bounded (at most [ATTEMPT_TIMEOUT]), so a suspended assertion cannot hang the test, and a
 * read queued behind a main-thread write gets another chance after the next idle.
 * E2E uses real I/O dispatchers for DataStore, so there is no virtual time to advance.
 */
internal fun retryUntilPasses(timeout: Duration, assertion: suspend () -> Unit) {
    val start = TimeSource.Monotonic.markNow()
    var lastFailure: Throwable? = null
    while (start.elapsedNow() < timeout) {
        idleMainLooper()
        val remaining = timeout - start.elapsedNow()
        val failure = runCatching { runBlocking { withTimeout(minOf(remaining, ATTEMPT_TIMEOUT)) { assertion() } } }.exceptionOrNull() ?: return
        if (failure !is TimeoutCancellationException || lastFailure == null) lastFailure = failure
        Thread.sleep(POLL_MILLIS)
    }
    throw lastFailure ?: AssertionError("Assertion was never attempted within $timeout")
}
