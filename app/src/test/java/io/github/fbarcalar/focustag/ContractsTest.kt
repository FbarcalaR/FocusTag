package io.github.fbarcalar.focustag

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.isComplete
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.system.missingRequired
import org.junit.Test

class ContractsTest {
    private val pairingA = TagPairing.written(TagRole.ACTIVATE, tagId = "a", uidHex = "01")
    private val pairingB = TagPairing.written(TagRole.DEACTIVATE, tagId = "b", uidHex = "02")

    @Test
    fun `pairings are complete only when both roles are paired`() {
        assertThat(mapOf(TagRole.ACTIVATE to pairingA).isComplete()).isFalse()
        assertThat(mapOf(TagRole.ACTIVATE to pairingA, TagRole.DEACTIVATE to pairingB).isComplete()).isTrue()
    }

    @Test
    fun `effects are degraded when any effect failed`() {
        assertThat(EffectsStatus().isDegraded).isFalse()
        assertThat(EffectsStatus(setOf(Effect.ZEN_RULE)).isDegraded).isTrue()
    }

    @Test
    fun `missing required lists only required items that are missing`() {
        val missingRequired = item(PermissionId.NFC_ENABLED, PermissionStatus.MISSING, required = true)
        val items = listOf(
            missingRequired,
            item(PermissionId.WRITE_SECURE_SETTINGS, PermissionStatus.MISSING, required = false),
            item(PermissionId.POST_NOTIFICATIONS, PermissionStatus.GRANTED, required = true),
        )

        assertThat(items.missingRequired).containsExactly(missingRequired)
    }

    private fun item(id: PermissionId, status: PermissionStatus, required: Boolean) =
        PermissionItem(id, status, required, PermissionAction.AdbGrant("adb"))
}
