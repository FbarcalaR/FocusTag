package io.github.fbarcalar.focustag.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Resolves the D-22 set from the package, telecom and input-method services. */
class AndroidAlwaysAllowedResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AlwaysAllowedResolver {
    override fun resolve(): Set<String> = buildSet {
        add(context.packageName)
        addAll(FIXED_PACKAGES)
        addAll(listOfNotNull(defaultActivityPackage(homeIntent()), defaultActivityPackage(settingsIntent())))
        addAll(skippingSecurityErrors(::dialers))
        addAll(skippingSecurityErrors(::inputMethods))
    }

    private fun defaultActivityPackage(intent: Intent): String? = context.packageManager
        .resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        ?.activityInfo
        ?.packageName

    private fun dialers(): List<String> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return listOfNotNull(telecom.defaultDialerPackage, telecom.systemDialerPackage)
    }

    private fun inputMethods(): List<String> {
        val inputMethods = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        return inputMethods.enabledInputMethodList.map { it.packageName }
    }

    private fun skippingSecurityErrors(lookup: () -> List<String>): List<String> = try {
        lookup()
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun settingsIntent() = Intent(Settings.ACTION_SETTINGS)

    private companion object {
        /** SystemUI, Settings, and the phone process hosting the emergency dialer. */
        val FIXED_PACKAGES = setOf("com.android.systemui", "com.android.settings", "com.android.phone")
    }
}
