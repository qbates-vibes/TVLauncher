package com.simple.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional helper for Google TV devices whose Home button always opens the stock launcher.
 * It does nothing unless you turn it on in Accessibility settings.
 *
 * Two strategies, because devices differ:
 *  1. Catch the Home key itself and open this launcher instead (no flicker, but some
 *     devices handle Home before accessibility services ever see it).
 *  2. Fallback: if a stock home screen activity comes to the front anyway, immediately
 *     bring this launcher back on top.
 */
class HomeButtonService : AccessibilityService() {
    private var stockHomeActivities: Set<ComponentName> = emptySet()

    override fun onServiceConnected() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        stockHomeActivities = packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != packageName }
            // Home activities are sometimes aliases; the window reports the real class.
            .map { ComponentName(it.packageName, it.targetActivity ?: it.name) }
            .toSet()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (ComponentName(pkg, cls) in stockHomeActivities) openLauncher()
    }

    override fun onInterrupt() = Unit

    private fun openLauncher() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val self = ComponentName(context, HomeButtonService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }
}
