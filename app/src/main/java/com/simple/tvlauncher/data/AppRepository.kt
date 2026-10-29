package com.simple.tvlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isTvApp: Boolean,
    /** 16:9 TV banner, if the app provides one. */
    val banner: ImageBitmap?,
    /** Square icon, used only when there is no banner. */
    val icon: ImageBitmap?,
)

class AppRepository(context: Context) {
    private val context = context.applicationContext
    private val pm = this.context.packageManager

    /** Emits the installed app list now, and again whenever an app is installed, removed or changed. */
    fun apps(): Flow<List<AppInfo>> = callbackFlow {
        val refresh = Channel<Unit>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                refresh.trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // These are system broadcasts; the receiver only triggers a harmless reload.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        refresh.trySend(Unit)
        launch {
            for (signal in refresh) {
                send(withContext(Dispatchers.IO) { loadApps() })
            }
        }
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun loadApps(): List<AppInfo> {
        val byPackage = LinkedHashMap<String, AppInfo>()
        // TV apps first, so their TV activity wins when an app has both.
        for ((category, isTv) in listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER to true,
            Intent.CATEGORY_LAUNCHER to false,
        )) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo
                if (activity.packageName == context.packageName) continue
                if (activity.packageName in byPackage) continue
                toAppInfo(activity, isTv)?.let { byPackage[activity.packageName] = it }
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    private fun toAppInfo(activity: ActivityInfo, isTv: Boolean): AppInfo? = try {
        val banner = activity.loadBanner(pm)
        AppInfo(
            packageName = activity.packageName,
            activityName = activity.name,
            label = activity.loadLabel(pm).toString(),
            isTvApp = isTv,
            banner = banner?.toBitmap(BANNER_WIDTH, BANNER_HEIGHT)?.asImageBitmap(),
            icon = if (banner == null) activity.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE).asImageBitmap() else null,
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping ${activity.packageName}", e)
        null
    }

    fun launch(app: AppInfo): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return startSafely(context, intent)
    }

    private companion object {
        const val TAG = "AppRepository"
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180
        const val ICON_SIZE = 160
    }
}

fun startSafely(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    Log.w("Launcher", "Could not start $intent", e)
    false
}
