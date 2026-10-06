package io.github.fbarcalar.focustag.system

import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Empty checklist until T4 lands `AndroidPermissionChecker`. Deleted by T4. */
class PlaceholderPermissionChecker @Inject constructor() : PermissionChecker {
    override val items: StateFlow<List<PermissionItem>> =
        MutableStateFlow(emptyList<PermissionItem>()).asStateFlow()

    override fun refresh() = Unit
}
