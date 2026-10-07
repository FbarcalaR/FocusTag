package io.github.fbarcalar.focustag.ui.setup

import android.graphics.Bitmap
import io.github.fbarcalar.focustag.blocker.InstalledApp
import io.github.fbarcalar.focustag.blocker.InstalledAppsSource

/** Fixed app list without icons. */
class FakeInstalledAppsSource(private val apps: List<InstalledApp>) : InstalledAppsSource {
    override suspend fun launchableApps(): List<InstalledApp> = apps

    override suspend fun icon(packageName: String): Bitmap? = null
}
