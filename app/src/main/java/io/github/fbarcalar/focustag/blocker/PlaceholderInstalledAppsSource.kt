package io.github.fbarcalar.focustag.blocker

import android.graphics.Bitmap
import javax.inject.Inject

/** Empty stand-in until T5 lands `InstalledAppsRepository`. Deleted by T5. */
class PlaceholderInstalledAppsSource @Inject constructor() : InstalledAppsSource {
    override suspend fun launchableApps(): List<InstalledApp> = emptyList()

    override suspend fun icon(packageName: String): Bitmap? = null
}
