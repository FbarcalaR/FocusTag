package io.github.fbarcalar.focustag.blocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Stub declared in the manifest by T1; T5 implements blocking (D-20). */
class FocusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit
}
