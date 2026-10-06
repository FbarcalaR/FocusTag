package io.github.fbarcalar.focustag.nfc

import io.github.fbarcalar.focustag.focus.TagRole

/** Verdict on a scanned tag (D-12). Only [Valid] scans may change the focus mode. */
sealed interface TagScanResult {
    /** A paired tag with the right UID, acting as [role]. */
    data class Valid(val role: TagRole) : TagScanResult

    /** A FocusTag URI whose id belongs to no pairing (also: an unpaired role, a stale id). */
    data object Unknown : TagScanResult

    /** A paired id on a tag with a different hardware UID (a copied URI). */
    data object UidMismatch : TagScanResult

    /** No FocusTag URI on the tag. */
    data object Malformed : TagScanResult
}

/** Pure check of a scan against the stored pairings (D-12). */
object TagValidator {
    fun validate(tag: ScannedTag, pairings: Map<TagRole, TagPairing>): TagScanResult {
        val tagId = tag.ndefUris.firstNotNullOfOrNull(FocusTagUri::parseTagId) ?: return TagScanResult.Malformed
        val pairing = pairings.values.firstOrNull { it.tagId == tagId } ?: return TagScanResult.Unknown
        return when (pairing.uidHex) {
            tag.uidHex -> TagScanResult.Valid(pairing.role)
            else -> TagScanResult.UidMismatch
        }
    }
}
