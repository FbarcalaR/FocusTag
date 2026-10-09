package io.github.fbarcalar.focustag.e2e

import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.TagProof

/** The two physical tags used by every scenario, plus helpers to build scans. */
object HarnessTags {
    val A = TagPairing.written(TagRole.ACTIVATE, tagId = "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", uidHex = "04A1B2C3D4E5F6")
    val B = TagPairing.written(TagRole.DEACTIVATE, tagId = "0e9f8a7b-6c5d-4e3f-8a1b-2c3d4e5f6a7b", uidHex = "04F6E5D4C3B2A1")
    const val UNKNOWN_UID = "04000000000000"
    const val UNKNOWN_TAG_ID = "11111111-2222-4333-8444-555555555555"
    /** `NfcAdapter.ACTION_NDEF_DISCOVERED`, spelled out to keep android.nfc out of tests (R2.4). */
    const val ACTION_NDEF_DISCOVERED = "android.nfc.action.NDEF_DISCOVERED"
    const val ACTION_TECH_DISCOVERED = "android.nfc.action.TECH_DISCOVERED"
    const val FOREIGN_URI = "https://example.com/toggle"

    fun of(role: TagRole): TagPairing = when (role) {
        TagRole.ACTIVATE -> A
        TagRole.DEACTIVATE -> B
    }

    fun uriFor(tagId: String): String = "focustag://toggle/$tagId"

    /** The id written on the harness tag for [role]. */
    fun tagIdOf(role: TagRole): String = (of(role).proof as TagProof.WrittenId).tagId

    fun scanOf(uidHex: String, uri: String) = ScannedTag(uidHex, listOf(uri))
}
