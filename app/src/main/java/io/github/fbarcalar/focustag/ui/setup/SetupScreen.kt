package io.github.fbarcalar.focustag.ui.setup

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.ui.setup.blocklist.BlockListEvent
import io.github.fbarcalar.focustag.ui.setup.blocklist.BlockListUiState
import io.github.fbarcalar.focustag.ui.setup.blocklist.blockListSection
import io.github.fbarcalar.focustag.ui.setup.permissions.PermissionEvent
import io.github.fbarcalar.focustag.ui.setup.permissions.PermissionsUiState
import io.github.fbarcalar.focustag.ui.setup.permissions.permissionSection
import io.github.fbarcalar.focustag.ui.setup.tags.TagDialogs
import io.github.fbarcalar.focustag.ui.setup.tags.TagEvent
import io.github.fbarcalar.focustag.ui.setup.tags.TagSection
import io.github.fbarcalar.focustag.ui.setup.tags.TagsUiState
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** The three sections' states and event sinks. */
data class SetupSections(
    val tags: TagsUiState,
    val permissions: PermissionsUiState,
    val blockList: BlockListUiState,
    val onTagEvent: (TagEvent) -> Unit = {},
    val onPermissionEvent: (PermissionEvent) -> Unit = {},
    val onBlockListEvent: (BlockListEvent) -> Unit = {},
    val loadIcon: suspend (String) -> Bitmap? = { null },
)

/** Stateless Setup screen: Tags, Permissions, then the (long) Block list in one list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(sections: SetupSections, onBack: (() -> Unit)?) {
    var resetRole by rememberSaveable { mutableStateOf<TagRole?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_setup)) },
                navigationIcon = { onBack?.let { BackButton(it) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { sections(sections, onRequestReset = { resetRole = it }) }
    }
    TagDialogs(sections.tags.pairing, resetRole, sections.onTagEvent, onResetDismiss = { resetRole = null })
}

private fun LazyListScope.sections(sections: SetupSections, onRequestReset: (TagRole) -> Unit) {
    if (sections.tags.focusLocked) item(key = "focus-locked") { FocusLockedCard() }
    item(key = "tags") { TagSection(sections.tags, sections.onTagEvent, onRequestReset) }
    item(key = "divider-permissions") { HorizontalDivider() }
    permissionSection(sections.permissions, sections.onPermissionEvent)
    item(key = "divider-block-list") { HorizontalDivider() }
    blockListSection(sections.blockList, sections.onBlockListEvent, sections.loadIcon)
}

@Composable
private fun FocusLockedCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Text(stringResource(R.string.setup_focus_locked), modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

@Preview
@Composable
private fun SetupScreenFreePreview() {
    FocusTagTheme { SetupScreen(SetupSections(TagsUiState(loaded = true), PermissionsUiState(), BlockListUiState()), onBack = {}) }
}

@Preview
@Composable
private fun SetupScreenFocusPreview() {
    FocusTagTheme {
        SetupScreen(SetupSections(TagsUiState(loaded = true, focusLocked = true), PermissionsUiState(), BlockListUiState()), onBack = null)
    }
}
