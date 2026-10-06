package io.github.fbarcalar.focustag.testing

import io.github.fbarcalar.focustag.system.PermissionChecker
import io.github.fbarcalar.focustag.system.PermissionItem
import kotlinx.coroutines.flow.MutableStateFlow

/** [PermissionChecker] whose items the test sets directly. */
class FakePermissionChecker(items: List<PermissionItem> = emptyList()) : PermissionChecker {
    override val items = MutableStateFlow(items)

    var refreshCount = 0
        private set

    override fun refresh() {
        refreshCount++
    }
}
