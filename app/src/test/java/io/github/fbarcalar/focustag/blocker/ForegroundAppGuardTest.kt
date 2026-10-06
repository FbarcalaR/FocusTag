package io.github.fbarcalar.focustag.blocker

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.FakeClock
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundAppGuardTest {
    private val focus = FakeFocusEngine()
    private val blockList = FakeBlockList()
    private val clock = FakeClock()
    private val launched = mutableListOf<String>()
    private var activeWindowPackage: String? = null
    private var resolverCalls = 0
    private val resolver = AlwaysAllowedResolver { resolverCalls++; setOf(OWN, LAUNCHER) }
    private val guard = ForegroundAppGuard(
        focus, blockList, resolver, RelaunchGuard(clock), { launched += it }, { activeWindowPackage },
    )

    private fun test(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) {
        guard.start(backgroundScope)
        body()
    }

    private suspend fun startFocus() = focus.onTagScanned(TagRole.ACTIVATE)

    @Test
    fun `a blocked app opened in focus shows the blocking screen`() = test {
        blockList.emit(BLOCKED)
        startFocus()

        guard.onForeground(listOf(BLOCKED))

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `a blocked app opened in free shows nothing`() = test {
        blockList.emit(BLOCKED)

        guard.onForeground(listOf(BLOCKED))

        assertThat(launched).isEmpty()
    }

    @Test
    fun `an unlisted app opened in focus shows nothing and skips the resolver`() = test {
        blockList.emit(BLOCKED)
        startFocus()

        guard.onForeground(listOf(OTHER))

        assertThat(launched).isEmpty()
        assertThat(resolverCalls).isEqualTo(0)
    }

    @Test
    fun `an always allowed app is never blocked even when listed`() = test {
        blockList.emit(LAUNCHER, OWN)
        startFocus()

        guard.onForeground(listOf(LAUNCHER))
        guard.onForeground(listOf(OWN))

        assertThat(launched).isEmpty()
    }

    @Test
    fun `the remembered blocked app is blocked when focus starts`() = test {
        blockList.emit(BLOCKED)
        guard.onForeground(listOf(BLOCKED))

        startFocus()

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `the active window is blocked when focus starts and nothing is remembered`() = test {
        blockList.emit(BLOCKED)
        activeWindowPackage = BLOCKED

        startFocus()

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `a blocked app under our own translucent window is blocked when focus starts`() = test {
        blockList.emit(BLOCKED)
        guard.onForeground(listOf(OWN, BLOCKED))

        startFocus()

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `an event before the block list loads is blocked once it loads`() = test {
        startFocus()
        guard.onForeground(listOf(BLOCKED))

        blockList.emit(BLOCKED)

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `repeated events for the same app launch the screen once`() = test {
        blockList.emit(BLOCKED)
        startFocus()

        repeat(3) { guard.onForeground(listOf(BLOCKED)) }

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `reopening the app after the dedupe window blocks it again`() = test {
        blockList.emit(BLOCKED)
        startFocus()
        guard.onForeground(listOf(BLOCKED))
        clock.advanceBy(1.seconds)

        guard.onForeground(listOf(BLOCKED))

        assertThat(launched).containsExactly(BLOCKED, BLOCKED)
    }

    @Test
    fun `the blocking screen's own window event launches nothing more`() = test {
        blockList.emit(BLOCKED)
        startFocus()
        guard.onForeground(listOf(BLOCKED))
        clock.advanceBy(1.seconds)

        guard.onForeground(listOf(OWN))

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `ending focus launches nothing`() = test {
        blockList.emit(BLOCKED)
        guard.onForeground(listOf(OTHER))
        startFocus()

        focus.onTagScanned(TagRole.DEACTIVATE)

        assertThat(launched).isEmpty()
    }

    @Test
    fun `a split screen list blocks its blocked app`() = test {
        blockList.emit(BLOCKED)
        startFocus()

        guard.onForeground(listOf(OTHER, BLOCKED))

        assertThat(launched).containsExactly(BLOCKED)
    }

    @Test
    fun `an empty window list keeps the remembered app`() = test {
        blockList.emit(BLOCKED)
        guard.onForeground(listOf(BLOCKED))
        guard.onForeground(emptyList())

        startFocus()

        assertThat(launched).containsExactly(BLOCKED)
    }

    private class FakeBlockList : BlockListRepository {
        override val blockedPackages = MutableSharedFlow<Set<String>>(replay = 1)

        fun emit(vararg packages: String) = check(blockedPackages.tryEmit(packages.toSet()))

        override suspend fun add(packageName: String) = Unit

        override suspend fun remove(packageName: String) = RemoveResult.Removed
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
        const val OTHER = "com.example.other"
        const val OWN = "io.github.fbarcalar.focustag"
        const val LAUNCHER = "com.example.launcher"
    }
}
