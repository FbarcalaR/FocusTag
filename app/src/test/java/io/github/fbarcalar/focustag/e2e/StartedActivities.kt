package io.github.fbarcalar.focustag.e2e

import android.app.Application
import android.content.Intent
import io.github.fbarcalar.focustag.blocker.BlockingActivity
import io.github.fbarcalar.focustag.blocker.EXTRA_BLOCKED_PACKAGE
import org.robolectric.Shadows.shadowOf

/** Accumulates activity starts (Robolectric hands each one out only once). */
internal class StartedActivities(private val app: Application) {
    private val seen = mutableListOf<Intent>()

    /** Packages the blocking screen was started for, oldest first. */
    fun blockedPackages(): List<String?> {
        val shadowApp = shadowOf(app)
        generateSequence { shadowApp.nextStartedActivity }.forEach { seen += it }
        return seen
            .filter { it.component?.className == BlockingActivity::class.java.name }
            .map { it.getStringExtra(EXTRA_BLOCKED_PACKAGE) }
    }
}
