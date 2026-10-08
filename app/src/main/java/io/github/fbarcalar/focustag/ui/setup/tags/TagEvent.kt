package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.annotation.StringRes
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole

/** User actions in the Tags section and the pairing dialog. */
sealed interface TagEvent {
    /** Pair, Re-pair or Try again. */
    data class StartPairing(val role: TagRole) : TagEvent
    data class Reset(val role: TagRole) : TagEvent

    /** Cancel, Close or OK in the pairing dialog. */
    data object Dismiss : TagEvent
    data object OpenNfcSettings : TagEvent
}

@StringRes
internal fun TagRole.titleRes(): Int = when (this) {
    TagRole.ACTIVATE -> R.string.setup_tag_activate_title
    TagRole.DEACTIVATE -> R.string.setup_tag_deactivate_title
}

@StringRes
internal fun TagRole.purposeRes(): Int = when (this) {
    TagRole.ACTIVATE -> R.string.setup_tag_activate_role
    TagRole.DEACTIVATE -> R.string.setup_tag_deactivate_role
}
