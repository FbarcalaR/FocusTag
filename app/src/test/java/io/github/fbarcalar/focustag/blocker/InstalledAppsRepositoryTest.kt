package io.github.fbarcalar.focustag.blocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class InstalledAppsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = shadowOf(context.packageManager)
    private val repository = InstalledAppsRepository(context, Dispatchers.Unconfined)

    @Test
    fun `apps are sorted by label ignoring case`() = runTest {
        install("com.example.z", "zeta")
        install("com.example.a", "Alpha")
        install("com.example.m", "mike")

        val labels = repository.launchableApps().map { it.label }

        assertThat(labels).containsExactly("Alpha", "mike", "zeta").inOrder()
    }

    @Test
    fun `our own app is excluded`() = runTest {
        install("com.example.a", "Alpha")

        val packages = repository.launchableApps().map { it.packageName }

        assertThat(packages).doesNotContain(context.packageName)
    }

    @Test
    fun `an app with two launcher activities is listed once`() = runTest {
        install("com.example.a", "Alpha", activities = listOf("Main", "Second"))

        val packages = repository.launchableApps().map { it.packageName }

        assertThat(packages).containsExactly("com.example.a")
    }

    @Test
    fun `an app without a launcher activity is excluded`() = runTest {
        install("com.example.service", "Service only", activities = emptyList())

        assertThat(repository.launchableApps()).isEmpty()
    }

    @Test
    fun `an installed app has an icon`() = runTest {
        install("com.example.a", "Alpha")
        packageManager.setApplicationIcon("com.example.a", ColorDrawable(Color.RED))

        assertThat(repository.icon("com.example.a")).isNotNull()
    }

    @Test
    fun `an unknown package has no icon`() = runTest {
        assertThat(repository.icon("com.example.missing")).isNull()
    }

    @Test
    fun `an installed app has its label`() = runTest {
        install("com.example.a", "Alpha")

        assertThat(repository.label("com.example.a")).isEqualTo("Alpha")
    }

    @Test
    fun `an unknown package has no label`() = runTest {
        assertThat(repository.label("com.example.missing")).isNull()
    }

    private fun install(packageName: String, label: String, activities: List<String> = listOf("Main")) {
        val app = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
        }
        packageManager.installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = app
            },
        )
        activities.map { ComponentName(packageName, "$packageName.$it") }.forEach { component ->
            packageManager.addActivityIfNotPresent(component)
            packageManager.addIntentFilterForActivity(component, launcherFilter())
        }
    }

    private fun launcherFilter() = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
}
