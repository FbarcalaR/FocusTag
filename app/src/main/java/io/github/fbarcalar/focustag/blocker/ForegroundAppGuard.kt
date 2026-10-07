package io.github.fbarcalar.focustag.blocker

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusStateReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Decides when to show the blocking screen. It remembers the last foreground packages and
 * re-checks them whenever the mode or the block list changes, so an app that is already open when
 * FOCUS starts is blocked too (D-23). Window events (main thread) and state changes (background)
 * are serialised by synchronising on the guard.
 */
class ForegroundAppGuard @AssistedInject constructor(
    private val focusState: FocusStateReader,
    private val blockList: BlockListRepository,
    private val resolver: AlwaysAllowedResolver,
    private val relaunchGuard: RelaunchGuard,
    @Assisted private val launcher: BlockScreenLauncher,
    @Assisted private val activeWindow: ActiveWindow,
) {
    /** Until the store has loaded, nothing is blocked (equivalent to FREE). */
    private var inputs = Inputs(FocusMode.FREE, emptySet())
    private var lastForeground: List<String> = emptyList()

    /** Follows the focus state and block list until [scope] is cancelled. */
    fun start(scope: CoroutineScope): Job =
        combine(focusState.state.map { it.mode }, blockList.blockedPackages, ::Inputs)
            .onEach(::onInputs)
            .launchIn(scope)

    /** The user now sees [packages]; an empty list carries no information and is ignored. */
    @Synchronized
    fun onForeground(packages: List<String>) {
        if (packages.isEmpty()) return
        lastForeground = packages
        blockFirstBlocked(packages)
    }

    @Synchronized
    private fun onInputs(newInputs: Inputs) {
        inputs = newInputs
        recheck()
    }

    private fun recheck() {
        if (inputs.mode != FocusMode.FOCUS) return
        blockFirstBlocked(lastForeground + listOfNotNull(activeWindow.packageName()))
    }

    private fun blockFirstBlocked(packages: List<String>) {
        val target = packages.firstOrNull { isBlocked(it) } ?: return
        if (relaunchGuard.shouldLaunch(target)) launcher.showBlockingScreen(target)
    }

    private fun isBlocked(packageName: String) =
        BlockDecider.shouldBlock(inputs.mode, packageName, inputs.blocked, resolver::resolve)

    private data class Inputs(val mode: FocusMode, val blocked: Set<String>)

    @AssistedFactory
    interface Factory {
        fun create(launcher: BlockScreenLauncher, activeWindow: ActiveWindow): ForegroundAppGuard
    }
}
