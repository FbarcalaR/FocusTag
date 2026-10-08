package io.github.fbarcalar.focustag.blocker

/** Packages that are never blocked, even when listed (D-22). */
fun interface AlwaysAllowedResolver {
    /** Resolved on each call, so a changed default launcher, dialer or keyboard applies at once. */
    fun resolve(): Set<String>
}
