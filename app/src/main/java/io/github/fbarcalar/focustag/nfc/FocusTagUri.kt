package io.github.fbarcalar.focustag.nfc

/** Builds and strictly parses the tag URI `focustag://toggle/<tagId>` (D-10, D-12). */
object FocusTagUri {
    private const val PREFIX = "focustag://toggle/"
    private val pattern = Regex(
        "(?i:focustag)://(?i:toggle)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})",
    )

    /** The URI written to a tag paired with [tagId]. */
    fun build(tagId: String): String = PREFIX + tagId

    /** The lower-case tag id carried by [uri], or null when [uri] is not exactly a FocusTag URI. */
    fun parseTagId(uri: String): String? = pattern.matchEntire(uri)?.groupValues?.get(1)?.lowercase()
}
