package io.github.fbarcalar.focustag.focus

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric only because a failing reconcile is logged with `android.util.Log`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FocusStartHookTest {
    private val reader = FakeFocusEngine()
    private val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private var reconciles = 0
    private var gate: CompletableDeferred<Unit>? = null
    private var failNext = false
    private val hook = FocusStartHook(reader, { signals }) {
        reconciles++
        gate?.await()
        if (failNext) {
            failNext = false
            error("effects broke")
        }
    }

    @Test
    fun `starting reconciles once while free`() = runTest {
        start()

        assertThat(reconciles).isEqualTo(1)
    }

    @Test
    fun `starting reconciles once while focused`() = runTest {
        reader.state.value = FOCUS

        start()

        assertThat(reconciles).isEqualTo(1)
    }

    @Test
    fun `a zen change while focused reconciles`() = runTest {
        reader.state.value = FOCUS
        start()

        signals.tryEmit(Unit)
        runCurrent()

        assertThat(reconciles).isEqualTo(2)
    }

    @Test
    fun `while free nobody listens to zen changes`() = runTest {
        start()

        signals.tryEmit(Unit)
        runCurrent()

        assertThat(signals.subscriptionCount.value).isEqualTo(0)
        assertThat(reconciles).isEqualTo(1)
    }

    @Test
    fun `leaving focus stops listening`() = runTest {
        reader.state.value = FOCUS
        start()
        assertThat(signals.subscriptionCount.value).isEqualTo(1)

        reader.state.value = FocusState.Free
        runCurrent()

        assertThat(signals.subscriptionCount.value).isEqualTo(0)
    }

    @Test
    fun `a burst during a reconcile queues at most one more`() = runTest {
        reader.state.value = FOCUS
        start()
        gate = CompletableDeferred()
        signals.tryEmit(Unit)
        runCurrent()

        repeat(5) { signals.tryEmit(Unit) }
        runCurrent()
        gate?.complete(Unit)
        runCurrent()

        assertThat(reconciles).isEqualTo(3)
    }

    @Test
    fun `a failing start reconcile does not stop re-assertion`() = runTest {
        reader.state.value = FOCUS
        failNext = true
        start()

        signals.tryEmit(Unit)
        runCurrent()

        assertThat(reconciles).isEqualTo(2)
    }

    @Test
    fun `a failing reconcile on a zen change keeps listening`() = runTest {
        reader.state.value = FOCUS
        start()
        failNext = true
        signals.tryEmit(Unit)
        runCurrent()

        signals.tryEmit(Unit)
        runCurrent()

        assertThat(reconciles).isEqualTo(3)
    }

    private fun TestScope.start() {
        backgroundScope.launch { hook.onAppStart() }
        runCurrent()
    }

    private companion object {
        val FOCUS = FocusState.Focus(Instant.parse("2026-10-06T08:00:00Z"))
    }
}
