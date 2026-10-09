package io.github.fbarcalar.focustag.system.permissions

import io.github.fbarcalar.focustag.di.ApplicationScope
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.system.PermissionChecker
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live checklist: re-evaluated on [refresh], on NFC changes and on OS permission signals. */
@Singleton
class AndroidPermissionChecker @Inject constructor(
    private val nfcGateway: NfcGateway,
    signals: PermissionChangeSignals,
    private val reader: PermissionStatusReader,
    actions: PermissionActions,
    @ApplicationScope scope: CoroutineScope,
) : PermissionChecker {
    // Built once: Intents compare by identity, so fresh ones would make every refresh a change.
    private val actionsById = PermissionId.entries.associateWith(actions::actionFor)

    // Optimistic until the gateway's first emission, so no false banner flashes.
    @Volatile
    private var nfc = NfcAvailability.ENABLED
    private val current = MutableStateFlow(evaluate())

    override val items: StateFlow<List<PermissionItem>> = current.asStateFlow()

    init {
        scope.launch { nfcGateway.availability.collect { nfc = it; refresh() } }
        scope.launch { signals.changes.collect { refresh() } }
    }

    override fun refresh() {
        current.update { evaluate() }
    }

    private fun evaluate(): List<PermissionItem> = PermissionId.entries.map { id ->
        PermissionItem(id, statusOf(id), required = id in REQUIRED, action = actionsById.getValue(id))
    }

    private fun statusOf(id: PermissionId): PermissionStatus = when (id) {
        PermissionId.NFC_ENABLED -> nfcStatus(nfc)
        PermissionId.NFC_TAG_INTENTS -> tagIntentStatus(nfc, nfcGateway.tagIntentsAllowed())
        PermissionId.ACCESSIBILITY_SERVICE -> reader.accessibilityService()
        PermissionId.NOTIFICATION_POLICY_ACCESS -> reader.notificationPolicyAccess()
        PermissionId.POST_NOTIFICATIONS -> reader.postNotifications()
        PermissionId.BATTERY_OPTIMIZATION_EXEMPTION -> reader.batteryOptimizationExemption()
        PermissionId.GRAYSCALE_CAPABILITY -> reader.grayscaleCapability()
        PermissionId.WRITE_SECURE_SETTINGS -> reader.writeSecureSettings()
    }

    private companion object {
        // Battery, grayscale capability and the adb grant are recommended, not required (PLAN S3).
        val REQUIRED = setOf(
            PermissionId.NFC_ENABLED,
            PermissionId.NFC_TAG_INTENTS,
            PermissionId.ACCESSIBILITY_SERVICE,
            PermissionId.NOTIFICATION_POLICY_ACCESS,
            PermissionId.POST_NOTIFICATIONS,
        )
    }
}
