package io.github.fbarcalar.focustag.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Launchable apps from the package manager (D-24); icons are decoded only on request and cached. */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : InstalledAppsSource {
    private val packageManager: PackageManager = context.packageManager
    private val icons = LruCache<String, Bitmap>(ICON_CACHE_ENTRIES)
    private val iconSizePx by lazy { (ICON_SIZE_DP * context.resources.displayMetrics.density).roundToInt() }

    override suspend fun launchableApps(): List<InstalledApp> = withContext(ioDispatcher) {
        packageManager.queryIntentActivities(launcherIntent(), PackageManager.ResolveInfoFlags.of(0))
            .distinctBy { it.packageName() }
            .filter { it.packageName() != context.packageName }
            .map { InstalledApp(it.packageName(), it.loadLabel(packageManager).toString()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    override suspend fun icon(packageName: String): Bitmap? =
        icons[packageName] ?: withContext(ioDispatcher) { loadIcon(packageName) }?.also { icons.put(packageName, it) }

    /** The app's user-visible name; null when [packageName] is not installed. */
    suspend fun label(packageName: String): String? = withContext(ioDispatcher) {
        try {
            val info = packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            info.loadLabel(packageManager).toString()
        } catch (_: NameNotFoundException) {
            null
        }
    }

    private fun loadIcon(packageName: String): Bitmap? = try {
        packageManager.getApplicationIcon(packageName).toBitmap(iconSizePx, iconSizePx)
    } catch (_: NameNotFoundException) {
        null
    }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private fun ResolveInfo.packageName(): String = activityInfo.packageName

    private companion object {
        const val ICON_CACHE_ENTRIES = 64
        const val ICON_SIZE_DP = 48
    }
}
