package io.github.fbarcalar.focustag.ui.setup.blocklist

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme
import kotlinx.coroutines.flow.drop

/** User actions in the Block list section. */
sealed interface BlockListEvent {
    data class QueryChanged(val query: String) : BlockListEvent
    data class SetBlocked(val packageName: String, val blocked: Boolean) : BlockListEvent
}

/** Search field and app rows; a blocked app's row is disabled during FOCUS (D-45). */
fun LazyListScope.blockListSection(
    state: BlockListUiState,
    onEvent: (BlockListEvent) -> Unit,
    loadIcon: suspend (String) -> Bitmap?,
) {
    item(key = "block-list-header") { BlockListHeader(state.query, onQueryChange = { onEvent(BlockListEvent.QueryChanged(it)) }) }
    when (val apps = state.apps) {
        AppsState.Loading -> item(key = "block-list-loading") { Loading() }
        is AppsState.Loaded -> {
            if (apps.rows.isEmpty()) item(key = "block-list-empty") { EmptyText(state.query) }
            items(apps.rows, key = { it.packageName }) { row ->
                AppRowView(row, loadIcon, onToggle = { onEvent(BlockListEvent.SetBlocked(row.packageName, it)) })
            }
        }
    }
}

/** The field owns its text synchronously (no lag behind the ViewModel); only edits flow up. */
@Composable
private fun BlockListHeader(query: String, onQueryChange: (String) -> Unit) {
    val field = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { currentOnQueryChange(it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_block_list_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            state = field,
            label = { Text(stringResource(R.string.setup_block_list_search)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            trailingIcon = { if (field.text.isNotEmpty()) ClearButton { field.clearText() } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyText(query: String) {
    val message = if (query.isBlank()) R.string.setup_block_list_none else R.string.setup_block_list_empty
    Text(stringResource(message))
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.setup_block_list_clear))
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun AppRowView(row: AppRow, loadIcon: suspend (String) -> Bitmap?, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = row.blocked, enabled = row.canToggle, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(row.packageName, loadIcon)
        Column(Modifier.weight(1f)) {
            Text(row.label)
            if (!row.canToggle) Text(stringResource(R.string.setup_block_list_locked), style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(checked = row.blocked, onCheckedChange = null, enabled = row.canToggle)
    }
}

@Composable
private fun AppIcon(packageName: String, loadIcon: suspend (String) -> Bitmap?) {
    val icon by produceState<Bitmap?>(null, packageName) { value = loadIcon(packageName) }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp)) }
    }
}

private val previewRows = listOf(
    AppRow("com.example.maps", "Maps", blocked = false, canToggle = true),
    AppRow("com.example.video", "VideoTube", blocked = true, canToggle = false),
)

@Preview(showBackground = true)
@Composable
private fun BlockListFocusPreview() {
    FocusTagTheme {
        LazyColumn { blockListSection(BlockListUiState(apps = AppsState.Loaded(previewRows), focusLocked = true), {}, { null }) }
    }
}

@Preview(showBackground = true)
@Composable
private fun BlockListLoadingPreview() {
    FocusTagTheme { LazyColumn { blockListSection(BlockListUiState(), {}, { null }) } }
}

@Preview(showBackground = true)
@Composable
private fun BlockListNoMatchPreview() {
    FocusTagTheme { LazyColumn { blockListSection(BlockListUiState("zzz", AppsState.Loaded(emptyList())), {}, { null }) } }
}
