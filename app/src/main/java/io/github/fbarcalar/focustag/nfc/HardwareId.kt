package io.github.fbarcalar.focustag.nfc

/** Rules for pairing a card by its hardware UID (D-62). */
internal object HardwareId {
    /** ISO 14443-A marks a random (per-tap) 4-byte UID with a first byte of 0x08. */
    private const val RANDOM_UID_PREFIX = "08"
    private const val SINGLE_SIZE_UID_HEX_LENGTH = 8

    /** Write failures after which the card may still be paired by UID: it answered but can't hold our URI. */
    val idOnlyEligible = setOf(WriteFailure.NOT_NDEF, WriteFailure.READ_ONLY, WriteFailure.REJECTED, WriteFailure.TOO_SMALL)

    /** True when [uidHex] is empty or announces itself as random, so it can't identify the card. */
    fun isKnownUnstable(uidHex: String): Boolean =
        uidHex.isEmpty() || (uidHex.length == SINGLE_SIZE_UID_HEX_LENGTH && uidHex.startsWith(RANDOM_UID_PREFIX))
}
