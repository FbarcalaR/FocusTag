package io.github.fbarcalar.focustag.ui.setup.blocklist

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fbarcalar.focustag.blocker.BlockListRepository
import io.github.fbarcalar.focustag.blocker.InstalledApp
import io.github.fbarcalar.focustag.blocker.InstalledAppsSource
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusStateReader
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Searchable app list with block checkboxes; unblocking is locked in FOCUS (D-45). */
@HiltViewModel
class BlockListViewModel @Inject constructor(
    private val blockListRepository: BlockListRepository,
    private val installedAppsSource: InstalledAppsSource,
    focusStateReader: FocusStateReader,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private val locked = focusStateReader.state.map { it.mode == FocusMode.FOCUS }

    val uiState: StateFlow<BlockListUiState> =
        combine(apps, blockListRepository.blockedPackages, query, locked) { apps, blocked, query, locked ->
            val state = apps?.let { AppsState.Loaded(appRows(it, blocked, query, locked)) } ?: AppsState.Loading
            BlockListUiState(query, state, locked)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BlockListUiState())

    init {
        viewModelScope.launch { apps.value = installedAppsSource.launchableApps() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** A refused removal needs no handling: the row stays checked and renders locked. */
    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            if (blocked) blockListRepository.add(packageName) else blockListRepository.remove(packageName)
        }
    }

    suspend fun icon(packageName: String): Bitmap? = installedAppsSource.icon(packageName)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
