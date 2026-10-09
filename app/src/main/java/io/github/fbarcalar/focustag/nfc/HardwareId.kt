package io.github.fbarcalar.focustag.nfc

/** Rules for pairing a card by its hardware UID (D-62). */
internal object HardwareId {
    /** ISO 14443-A marks a random (per-tap) 4-byte UID with a first byte of 0x08. */
    private const val RANDOM_UID_PREFIX = "08"
    private const val SINGLE_SIZE_UID_HEX_LENGTH = 8

    /**
     * Write failures meaning the card answered but can't hold our URI. `REJECTED` is left out: a
     * writable sticker refuses a write mostly through a weak tap, and that should be retried, not
     * downgraded to the weaker ID-only check.
     */
    private val cannotHoldOurUri = setOf(WriteFailure.NOT_NDEF, WriteFailure.READ_ONLY, WriteFailure.TOO_SMALL)

    /**
     * Whether a card whose write failed with [failure] may be paired by UID. A tag already holding
     * another app's link is refused: Android would open that link instead of reaching FocusTag.
     */
    fun mayPairById(failure: WriteFailure, tag: ScannedTag): Boolean =
        failure in cannotHoldOurUri && tag.ndefUris.all { FocusTagUri.parseTagId(it) != null }

    /**
     * True when [uidHex] is empty or announces itself as random, so it can't identify the card.
     * The `08` rule is ISO 14443-A's; a fixed 4-byte NFC-B PUPI starting with `08` (about 1 in 256)
     * is refused too, which only costs that card the ID-only option.
     */
    fun isKnownUnstable(uidHex: String): Boolean =
        uidHex.isEmpty() || (uidHex.length == SINGLE_SIZE_UID_HEX_LENGTH && uidHex.startsWith(RANDOM_UID_PREFIX))
}
