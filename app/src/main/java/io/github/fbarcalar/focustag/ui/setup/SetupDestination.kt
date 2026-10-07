package io.github.fbarcalar.focustag.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.ui.setup.blocklist.BlockListEvent
import io.github.fbarcalar.focustag.ui.setup.blocklist.BlockListViewModel
import io.github.fbarcalar.focustag.ui.setup.permissions.PermissionEvent
import io.github.fbarcalar.focustag.ui.setup.permissions.PermissionsViewModel
import io.github.fbarcalar.focustag.ui.setup.permissions.rememberPermissionActionLauncher
import io.github.fbarcalar.focustag.ui.setup.tags.PairingState
import io.github.fbarcalar.focustag.ui.setup.tags.ReaderModeEffect
import io.github.fbarcalar.focustag.ui.setup.tags.TagEvent
import io.github.fbarcalar.focustag.ui.setup.tags.TagPairingViewModel

/**
 * Setup screen entry point (frozen signature). [onBack] is null when Setup is the start
 * destination; [onPairingComplete] is called once a pairing completes the set of both tags.
 */
@Composable
fun SetupDestination(onBack: (() -> Unit)?, onPairingComplete: () -> Unit) {
    val tagsViewModel: TagPairingViewModel = hiltViewModel()
    val permissionsViewModel: PermissionsViewModel = hiltViewModel()
    val blockListViewModel: BlockListViewModel = hiltViewModel()
    val tags by tagsViewModel.uiState.collectAsStateWithLifecycle()
    val permissions by permissionsViewModel.uiState.collectAsStateWithLifecycle()
    val blockList by blockListViewModel.uiState.collectAsStateWithLifecycle()
    val launch = rememberPermissionActionLauncher(onResult = permissionsViewModel::refresh)

    ReaderModeEffect(tags.nfc == NfcAvailability.ENABLED, tagsViewModel::enableReaderMode, tagsViewModel::disableReaderMode)
    LifecycleResumeEffect(Unit) {
        permissionsViewModel.refresh()
        onPauseOrDispose {}
    }
    PairingCompleteEffect(tags.pairing, onPairingComplete)

    val sections = SetupSections(
        tags = tags,
        permissions = permissions,
        blockList = blockList,
        onTagEvent = { event -> tagsViewModel.handle(event, onOpenNfcSettings = { permissions.items.nfcAction()?.let(launch) }) },
        onPermissionEvent = { event -> permissionsViewModel.handle(event, launch) },
        onBlockListEvent = blockListViewModel::handle,
        loadIcon = blockListViewModel::icon,
    )
    SetupScreen(sections, onBack)
}

@Composable
private fun PairingCompleteEffect(pairing: PairingState, onPairingComplete: () -> Unit) {
    val complete by rememberUpdatedState(onPairingComplete)
    LaunchedEffect(pairing) {
        if (pairing is PairingState.Done && pairing.completesSetup) complete()
    }
}

private fun TagPairingViewModel.handle(event: TagEvent, onOpenNfcSettings: () -> Unit) = when (event) {
    is TagEvent.StartPairing -> startPairing(event.role)
    is TagEvent.Reset -> reset(event.role)
    TagEvent.Dismiss -> dismiss()
    TagEvent.OpenNfcSettings -> onOpenNfcSettings()
}

private fun PermissionsViewModel.handle(event: PermissionEvent, launch: (PermissionAction) -> Unit) = when (event) {
    is PermissionEvent.Launch -> launch(event.action)
    is PermissionEvent.SetFallback -> setFallbackEnabled(event.enabled)
}

private fun BlockListViewModel.handle(event: BlockListEvent) = when (event) {
    is BlockListEvent.QueryChanged -> onQueryChange(event.query)
    is BlockListEvent.SetBlocked -> setBlocked(event.packageName, event.blocked)
}

/** The NFC settings intent comes from the checklist, so it has a single source (T4 S3). */
private fun List<PermissionItem>.nfcAction(): PermissionAction? = firstOrNull { it.id == PermissionId.NFC_ENABLED }?.action
