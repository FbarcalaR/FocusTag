package io.github.fbarcalar.focustag.ui.setup.blocklist

import io.github.fbarcalar.focustag.blocker.InstalledApp

/** Everything the Block list section renders. */
data class BlockListUiState(
    val query: String = "",
    val apps: AppsState = AppsState.Loading,
    val focusLocked: Boolean = false,
)

sealed interface AppsState {
    data object Loading : AppsState
    data class Loaded(val rows: List<AppRow>) : AppsState
}

/** One app; [canToggle] is false only for a blocked app during FOCUS (D-45). */
data class AppRow(val packageName: String, val label: String, val blocked: Boolean, val canToggle: Boolean)

/** Apps matching [query] (label or package, ignoring case), in source order. */
fun appRows(apps: List<InstalledApp>, blocked: Set<String>, query: String, locked: Boolean): List<AppRow> {
    val needle = query.trim()
    return apps
        .filter { it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true) }
        .map { app ->
            val isBlocked = app.packageName in blocked
            AppRow(app.packageName, app.label, isBlocked, canToggle = !(locked && isBlocked))
        }
}
