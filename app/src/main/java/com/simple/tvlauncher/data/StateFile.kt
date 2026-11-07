package com.simple.tvlauncher.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/** Which TV a saved setup came from. Shown after an import; nothing is applied from it. */
data class StateSource(
    val model: String,
    val android: String,
    val fireTv: Boolean,
    val launcherVersion: String,
    val exportedAt: String,
) {
    companion object {
        fun thisTv(context: Context) = StateSource(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = Build.VERSION.RELEASE,
            fireTv = DeviceScreens.isFireTv(context),
            launcherVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "",
            exportedAt = Instant.now().toString(),
        )
    }
}

data class SavedSetup(val settings: LauncherSettings, val source: StateSource?)

/**
 * A launcher setup as a small JSON file, so it can be backed up, copied to another TV or shared.
 * It holds every setting except this TV's Home button behavior. Media files travel next to it.
 * Unknown fields are ignored and missing ones get their defaults, so older and newer files still load.
 */
object StateFile {
    private const val FORMAT = "simple-launcher-state"
    private const val VERSION = 1

    fun write(settings: LauncherSettings, source: StateSource): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put(
            "source", JSONObject()
                .put("model", source.model)
                .put("android", source.android)
                .put("fireTv", source.fireTv)
                .put("launcherVersion", source.launcherVersion)
                .put("exportedAt", source.exportedAt)
        )
        .put("favorites", JSONArray(settings.favorites))
        .put("appOrder", JSONArray(settings.appOrder))
        .put("hidden", JSONArray(settings.hidden.sorted()))
        .put("roles", JSONObject(settings.roleOverrides.mapValues { it.value.name }))
        .put("homeStyle", settings.homeStyle.name)
        .put("music", settings.music.toJson())
        .put("videoSound", settings.videoSound.toJson())
        .put("backgroundColor", "#%08X".format(settings.backgroundColor))
        .put("useWallpaper", settings.useWallpaper)
        .put("wallpaperName", settings.wallpaperName ?: JSONObject.NULL)
        .put("use24HourClock", settings.use24HourClock)
        .toString(2)

    /** Throws [IllegalArgumentException] if [text] isn't a launcher setup. */
    fun read(text: String): SavedSetup {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Not valid JSON: ${e.message}")
        }
        require(json.optString("format") == FORMAT) { "Not a Simple Launcher setup (format isn't \"$FORMAT\")" }
        val defaults = LauncherSettings()
        val roles = json.optJSONObject("roles")
        val settings = LauncherSettings(
            favorites = json.packages("favorites"),
            appOrder = json.packages("appOrder"),
            hidden = json.packages("hidden").toSet(),
            roleOverrides = roles?.keys()?.asSequence()
                ?.filter { PACKAGE_NAME.matches(it) }
                ?.mapNotNull { pkg -> Role.entries.firstOrNull { it.name == roles.optString(pkg) }?.let { pkg to it } }
                ?.toMap()
                ?: emptyMap(),
            homeStyle = HomeStyle.entries.firstOrNull { it.name == json.optString("homeStyle") } ?: defaults.homeStyle,
            music = json.sound("music", "musicVolume", Sound.Music),
            videoSound = json.sound("videoSound", "videoVolume", Sound.Video),
            backgroundColor = json.optString("backgroundColor").removePrefix("#").toLongOrNull(16) ?: defaults.backgroundColor,
            useWallpaper = json.optBoolean("useWallpaper", defaults.useWallpaper),
            wallpaperName = json.optString("wallpaperName").takeIf { it.isNotBlank() && !json.isNull("wallpaperName") },
            use24HourClock = json.optBoolean("use24HourClock", defaults.use24HourClock),
        )
        val source = json.optJSONObject("source")?.let {
            StateSource(
                model = it.optString("model"),
                android = it.optString("android"),
                fireTv = it.optBoolean("fireTv"),
                launcherVersion = it.optString("launcherVersion"),
                exportedAt = it.optString("exportedAt"),
            )
        }
        return SavedSetup(settings, source)
    }

    private fun SoundLevel.toJson() = JSONObject().put("level", Math.round(position * 100) / 100.0).put("muted", muted)

    /** Reads a volume slider and mute switch, or the older one-word setting (Off, Quiet, ...) from earlier setups. */
    private fun JSONObject.sound(name: String, legacyName: String, sound: Sound): SoundLevel {
        optJSONObject(name)?.let {
            return SoundLevel(SoundLevel.snap(it.optDouble("level", sound.default.position.toDouble()).toFloat()), it.optBoolean("muted"))
        }
        return sound.legacy[optString(legacyName)] ?: sound.default
    }

    /** Package names only: anything else could break the comma-separated way they're stored. */
    private fun JSONObject.packages(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.optString(it) }.filter { PACKAGE_NAME.matches(it) }.distinct()
    }

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
}
