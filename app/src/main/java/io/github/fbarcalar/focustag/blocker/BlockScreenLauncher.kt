package io.github.fbarcalar.focustag.blocker

/** Shows the blocking screen over a blocked app; implemented by the accessibility service. */
fun interface BlockScreenLauncher {
    fun showBlockingScreen(packageName: String)
}

/** The package of the window the user is interacting with, if the system reports one. */
fun interface ActiveWindow {
    fun packageName(): String?
}
