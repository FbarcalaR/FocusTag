package io.github.fbarcalar.focustag.system.zen

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/** Zen effects need the API 35 rule builder and device effects (D-30). */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun zenRulesSupported(): Boolean = zenRulesSupportedOn(Build.VERSION.SDK_INT)

/** [zenRulesSupported] for a given SDK level. */
fun zenRulesSupportedOn(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM
