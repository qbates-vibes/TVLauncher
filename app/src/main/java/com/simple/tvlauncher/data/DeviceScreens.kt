package com.simple.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.simple.tvlauncher.HomeButtonService
import com.simple.tvlauncher.StockHomeVisit

/** Opens the TV's own screens, which aren't reached the same way on every platform. */
object DeviceScreens {
    private const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"
    private const val FIRE_TV_LAUNCHER = "com.amazon.tv.launcher"
    private const val FIRE_TV_HOME = "$FIRE_TV_LAUNCHER.ui.HomeActivity_vNext"

    fun isFireTv(context: Context): Boolean = context.packageManager.hasSystemFeature(FIRE_TV_FEATURE)

    fun openTvSettings(context: Context): Boolean {
        // On Fire TV the standard settings intent detours through Amazon's home screen,
        // which the Home button helper would bounce, so go straight to its settings screen.
        if (isFireTv(context) && startSafely(context, fireTvSettings())) return true
        return startSafely(context, Intent(Settings.ACTION_SETTINGS))
    }

    /** Opens the stock home screen until you come back to this launcher. */
    fun openStockHome(context: Context): Boolean {
        val home = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .maxByOrNull { it.priority }
            ?.activityInfo
            ?: return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClassName(home.packageName, home.name)
        return startVisit(context, intent, StockHomeVisit.UntilLauncherOpens)
    }

    /**
     * Opens Amazon's inputs. Fire TV only lets Amazon's own apps switch inputs, so you choose one
     * on its home screen, and the Home button comes back here once you have.
     */
    fun openFireTvInputs(context: Context): Boolean =
        startVisit(context, Intent("com.amazon.tv.action.NAVIGATE_TO_INPUTS").setClassName(FIRE_TV_LAUNCHER, FIRE_TV_HOME), StockHomeVisit.UntilAnotherAppOpens)

    /** Starts [intent] on the stock home screen, with the Home button helper standing aside for the length of [visit]. */
    private fun startVisit(context: Context, intent: Intent, visit: StockHomeVisit): Boolean {
        HomeButtonService.visit = visit
        val opened = startSafely(context, intent)
        if (!opened) HomeButtonService.visit = StockHomeVisit.None
        return opened
    }

    private fun fireTvSettings() = Intent("amazon.intent.action.MAINSETTINGS")
        .addCategory("android.intent.category.MAINSETTINGS")
        .setClassName(FIRE_TV_LAUNCHER, "$FIRE_TV_LAUNCHER.ui.MainSettingsActivity")
}
