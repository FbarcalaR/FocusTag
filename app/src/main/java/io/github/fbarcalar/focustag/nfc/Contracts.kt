package io.github.fbarcalar.focustag.nfc

import android.app.Activity
import android.content.Intent
import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.flow.Flow

/**
 * Hardware-free view of a scanned tag (D-15).
 *
 * @property uidHex hardware UID as upper-case hex without separators.
 * @property ndefUris every URI record found in the tag's NDEF message, in order.
 */
data class ScannedTag(val uidHex: String, val ndefUris: List<String>)

/** A tag currently in the field (delivered by reader mode); opaque outside the nfc layer. */
interface NfcTagHandle {
    /** What was read from the tag when it was discovered. */
    val scanned: ScannedTag
}

/** State of the device's NFC adapter. */
enum class NfcAvailability {
    /** The device has no NFC hardware. */
    UNAVAILABLE,

    /** NFC exists but is switched off. */
    DISABLED,

    /** NFC is on. */
    ENABLED,
}

/** Why writing a tag failed. */
enum class WriteFailure {
    /** The tag is locked. */
    READ_ONLY,

    /** The message does not fit. */
    TOO_SMALL,

    /** The tag cannot hold NDEF data, e.g. a protected transport, bank or access card. */
    NOT_NDEF,

    /** The tag left the field mid-operation. */
    TAG_LOST,

    /** The tag answered but refused the format or write. */
    REJECTED,

    /** The pairing could not be read or saved on the phone. */
    IO_ERROR,

    /** The read-back did not match what was written. */
    VERIFY_FAILED,
}

/** Result of writing a FocusTag message. */
sealed interface WriteResult {
    /** Written and verified. */
    data object Written : WriteResult

    /** Not written, for [reason]. */
    data class Failed(val reason: WriteFailure) : WriteResult
}

/** The only door to `android.nfc`. Faked in every Hilt test (`FakeNfcGateway`). */
interface NfcGateway {
    /** Current adapter state; emits again when the user toggles NFC. */
    val availability: Flow<NfcAvailability>

    /** Parses an `NDEF_DISCOVERED` intent; null when the intent carries no tag. */
    fun readTag(intent: Intent): ScannedTag?

    /** Gives [activity] exclusive tag access (D-14). [onTag] runs on a binder thread. */
    fun enableReaderMode(activity: Activity, onTag: (NfcTagHandle) -> Unit)

    /** Ends reader mode started with [enableReaderMode]. */
    fun disableReaderMode(activity: Activity)

    /** Writes the URI record [uri] plus our AAR, then reads it back to verify (D-10). */
    suspend fun writeFocusTag(tag: NfcTagHandle, uri: String): WriteResult
}

/**
 * A paired tag (D-11).
 *
 * @property role what scanning this tag does.
 * @property tagId random UUID written into the tag's URI.
 * @property uidHex hardware UID the scan must match (D-12).
 */
data class TagPairing(val role: TagRole, val tagId: String, val uidHex: String)

/** True when every [TagRole] has a pairing. */
fun Map<TagRole, TagPairing>.isComplete(): Boolean = TagRole.entries.all { it in this }

/** Result of pairing a tag. */
sealed interface PairingResult {
    /** The tag was written and the pairing stored. */
    data class Paired(val pairing: TagPairing) : PairingResult

    /** The tag's UID already belongs to the other role; nothing changed. */
    data object UidUsedByOtherRole : PairingResult

    /** Writing failed for [reason]; the existing pairing is untouched. */
    data class WriteFailed(val reason: WriteFailure) : PairingResult
}

/** Stored pairings. */
interface PairingRepository {
    /** Current pairings keyed by role; missing roles are unpaired. */
    val pairings: Flow<Map<TagRole, TagPairing>>

    /** Stores [pairing]; returns [PairingResult.Paired] or [PairingResult.UidUsedByOtherRole]. */
    suspend fun save(pairing: TagPairing): PairingResult

    /** Forgets the pairing for [role]. */
    suspend fun reset(role: TagRole)

    /** Forgets every pairing. */
    suspend fun resetAll()
}

/**
 * Pairing use case: UID check, new UUID, write, verify, save. The UID check comes first because
 * writing a tag already paired to the other role would invalidate that role. On failure the
 * existing pairing is untouched.
 */
interface TagWriter {
    /** Pairs the tag in the field as [role]. */
    suspend fun pair(tag: NfcTagHandle, role: TagRole): PairingResult
}
