package com.simple.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.simple.tvlauncher.data.SettingsStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** A trip to the stock home screen on purpose, which the helper shouldn't bounce. Opening this launcher always ends it. */
enum class StockHomeVisit {
    None,

    /** You asked for the stock home screen, so Home stays with it until you come back to this launcher. */
    UntilLauncherOpens,

    /** You only went there to open something else, so Home comes back to this launcher once you have. */
    UntilAnotherAppOpens,
}

/**
 * Optional helper for Google TV devices whose Home button always opens the stock launcher.
 * It does nothing unless you turn it on in Accessibility settings.
 *
 * Two strategies, because devices differ:
 *  1. Catch the Home key itself and open this launcher instead (no flicker, but some
 *     devices handle Home before accessibility services ever see it).
 *  2. Fallback: if a stock home screen activity comes to the front anyway, immediately
 *     bring this launcher back on top.
 *
 * It stands aside when the Redirect Home button setting is off, and during a [StockHomeVisit].
 */
class HomeButtonService : AccessibilityService() {
    private val scope = MainScope()
    private var stockHomeActivities: Set<ComponentName> = emptySet()
    private var stockHomePackages: Set<String> = emptySet()

    /** Null until the saved setting has loaded. */
    private var redirectHome: Boolean? = null
    private var stockHomeOpenedWhileLoading = false

    override fun onServiceConnected() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        stockHomeActivities = packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != packageName }
            // Home activities are sometimes aliases; the window reports the real class.
            .map { ComponentName(it.packageName, it.targetActivity ?: it.name) }
            .toSet()
        stockHomePackages = stockHomeActivities.map { it.packageName }.toSet() + packageName

        scope.launch {
            SettingsStore(this@HomeButtonService).settings.collect {
                redirectHome = it.redirectHome
                if (stockHomeOpenedWhileLoading && it.redirectHome && visit == StockHomeVisit.None) openLauncher()
                stockHomeOpenedWhileLoading = false
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || redirectHome != true || visit != StockHomeVisit.None) return false
        if (event.action == KeyEvent.ACTION_UP) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (ComponentName(pkg, cls) !in stockHomeActivities) {
            // Overlays also report windows, so only another app counts as leaving the stock home screen.
            if (visit == StockHomeVisit.UntilAnotherAppOpens && pkg !in stockHomePackages && pkg !in OVERLAY_PACKAGES) {
                visit = StockHomeVisit.None
            }
            return
        }
        when (redirectHome) {
            null -> stockHomeOpenedWhileLoading = true
            true -> if (visit == StockHomeVisit.None) openLauncher()
            false -> Unit
        }
    }

    override fun onInterrupt() = Unit

    private fun openLauncher() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    companion object {
        private val OVERLAY_PACKAGES = setOf("android", "com.android.systemui")

        @Volatile
        var visit = StockHomeVisit.None

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val self = ComponentName(context, HomeButtonService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }
}
