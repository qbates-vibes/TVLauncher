package com.simple.tvlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class LauncherSettings(
    /** Package names, in display order. */
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val backgroundColor: Long = DEFAULT_BACKGROUND,
    val useWallpaper: Boolean = false,
    /** File name of the chosen wallpaper picture. */
    val wallpaperName: String? = null,
    val use24HourClock: Boolean = false,
    /** Package names in the order you arranged the app grid. Apps not listed go at the end, A–Z. */
    val appOrder: List<String> = emptyList(),
    /** Whether the Home button helper acts at all. Lets you turn it off from the remote. */
    val redirectHome: Boolean = true,
    val homeStyle: HomeStyle = HomeStyle.Classic,
    /** Roles you picked yourself; other apps get [Role.default]. */
    val roleOverrides: Map<String, Role> = emptyMap(),
    /** Background music; plays only on the Hero select screen. */
    val music: SoundLevel = Sound.Music.default,
    /** The sound of a video wallpaper. */
    val videoSound: SoundLevel = Sound.Video.default,
) {
    companion object {
        const val DEFAULT_BACKGROUND = 0xFF101418
    }
}

enum class HomeStyle(val label: String) {
    Classic("Classic"),
    /** Classic without favorites, with the apps low on the screen so the background shows. */
    Scenic("Scenic"),
    HeroSelect("Hero select");

    fun next(): HomeStyle = entries[(ordinal + 1) % entries.size]
}


/** A sound setting: a volume slider position from 0 to 1, and a mute switch. */
data class SoundLevel(val position: Float, val muted: Boolean = false) {
    /**
     * What to play at, as a fraction of the TV's media volume. The slider position is squared
     * because loudness isn't linear: this way each step of the slider sounds about as big as the last.
     */
    val volume: Float get() = if (muted) 0f else position * position

    companion object {
        const val STEP = 0.05f

        /** Rounds to a whole slider step, between 0 and 1. */
        fun snap(position: Float): Float = (Math.round(position / STEP) * STEP).coerceIn(0f, 1f)
    }
}

/** The launcher's two sounds, with their defaults and what the older Off/Quiet/... settings map to. */
enum class Sound(val default: SoundLevel, val legacy: Map<String, SoundLevel>) {
    Music(
        default = SoundLevel(0.35f),
        legacy = mapOf(
            "Off" to SoundLevel(0.35f, muted = true),
            "Quiet" to SoundLevel(0.35f),
            "Medium" to SoundLevel(0.5f),
            "Loud" to SoundLevel(0.65f),
        ),
    ),
    Video(
        default = SoundLevel(0.4f),
        legacy = mapOf(
            "Muted" to SoundLevel(0.4f, muted = true),
            "Quiet" to SoundLevel(0.4f),
            "Medium" to SoundLevel(0.6f),
            "Loud" to SoundLevel(0.75f),
            "Full" to SoundLevel(1f),
        ),
    ),
}

private val Context.dataStore by preferencesDataStore(name = "launcher_settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<LauncherSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            LauncherSettings(
                favorites = p.list(FAVORITES),
                hidden = p[HIDDEN] ?: emptySet(),
                backgroundColor = p[BACKGROUND] ?: LauncherSettings.DEFAULT_BACKGROUND,
                useWallpaper = p[USE_WALLPAPER] ?: false,
                wallpaperName = p[WALLPAPER_NAME],
                use24HourClock = p[USE_24H] ?: false,
                appOrder = p.list(APP_ORDER),
                redirectHome = p[REDIRECT_HOME] ?: true,
                homeStyle = HomeStyle.entries.firstOrNull { it.name == p[HOME_STYLE] } ?: HomeStyle.Classic,
                roleOverrides = p.roleOverrides(),
                music = p.sound(Sound.Music),
                videoSound = p.sound(Sound.Video),
            )
        }

    suspend fun toggleFavorite(pkg: String) = store.edit { p ->
        val current = p.list(FAVORITES)
        p.setFavorites(if (pkg in current) current - pkg else current + pkg)
    }

    /** Moves a favorite left (delta < 0) or right (delta > 0) among the [shown] favorites. */
    suspend fun moveFavorite(pkg: String, delta: Int, shown: Set<String>) = store.edit { p ->
        p.setFavorites(p.list(FAVORITES).moveAmong(shown, pkg, delta))
    }

    /**
     * Moves an app in the grid by [delta] places (±1 for left/right, ±columns for up/down).
     * [visibleApps] is every visible app in A–Z order, used to place apps you haven't arranged yet.
     */
    suspend fun moveApp(pkg: String, delta: Int, visibleApps: List<String>) = store.edit { p ->
        val saved = p.list(APP_ORDER)
        p[APP_ORDER] = (saved + (visibleApps - saved.toSet())).moveAmong(visibleApps.toSet(), pkg, delta).joinToString(",")
    }

    /** Replaces everything a saved setup covers in one step. Redirect Home belongs to this TV, so it stays. */
    suspend fun replaceAll(settings: LauncherSettings) = store.edit { p ->
        p.setFavorites(settings.favorites)
        if (settings.appOrder.isEmpty()) p.remove(APP_ORDER) else p[APP_ORDER] = settings.appOrder.joinToString(",")
        p[HIDDEN] = settings.hidden
        p[BACKGROUND] = settings.backgroundColor
        p[USE_WALLPAPER] = settings.useWallpaper
        settings.wallpaperName?.let { p[WALLPAPER_NAME] = it } ?: p.remove(WALLPAPER_NAME)
        p[USE_24H] = settings.use24HourClock
        p[HOME_STYLE] = settings.homeStyle.name
        p.setRoles(settings.roleOverrides)
        p.setSound(Sound.Music, settings.music)
        p.setSound(Sound.Video, settings.videoSound)
    }

    /** Moves a volume slider by [steps] notches (negative is quieter), and unmutes it. */
    suspend fun stepSound(sound: Sound, steps: Int) = store.edit { p ->
        val current = p.sound(sound)
        p.setSound(sound, SoundLevel(SoundLevel.snap(current.position + steps * SoundLevel.STEP)))
    }

    suspend fun setMuted(sound: Sound, muted: Boolean) = store.edit { p ->
        p.setSound(sound, p.sound(sound).copy(muted = muted))
    }

    suspend fun setHomeStyle(style: HomeStyle) = store.edit { it[HOME_STYLE] = style.name }

    suspend fun setRole(pkg: String, role: Role) = store.edit { p ->
        p.setRoles(p.roleOverrides() + (pkg to role))
    }

    suspend fun resetAppOrder() = store.edit { it.remove(APP_ORDER) }

    suspend fun setHidden(pkg: String, hidden: Boolean) = store.edit { p ->
        val current = p[HIDDEN] ?: emptySet()
        p[HIDDEN] = if (hidden) current + pkg else current - pkg
        if (hidden) p.setFavorites(p.list(FAVORITES) - pkg)
    }

    /** Shows a solid color, turning the wallpaper picture off. */
    suspend fun setBackgroundColor(color: Long) = store.edit {
        it[BACKGROUND] = color
        it[USE_WALLPAPER] = false
    }

    suspend fun setWallpaper(name: String) = store.edit {
        it[WALLPAPER_NAME] = name
        it[USE_WALLPAPER] = true
    }

    suspend fun setUse24HourClock(enabled: Boolean) = store.edit { it[USE_24H] = enabled }

    suspend fun setRedirectHome(enabled: Boolean) = store.edit { it[REDIRECT_HOME] = enabled }

    private fun Preferences.list(key: Preferences.Key<String>): List<String> =
        this[key]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    /** Stored as "package=Role,package=Role". */
    private fun Preferences.roleOverrides(): Map<String, Role> =
        list(ROLES).mapNotNull { entry ->
            val (pkg, roleName) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            Role.entries.firstOrNull { it.name == roleName }?.let { pkg to it }
        }.toMap()

    /** Falls back to the older one-button setting (Off, Quiet, Medium, ...) if the slider hasn't been used yet. */
    private fun Preferences.sound(sound: Sound): SoundLevel {
        val (levelKey, mutedKey, legacyKey) = soundKeys(sound)
        val legacy = this[legacyKey]?.let { sound.legacy[it] } ?: sound.default
        return SoundLevel(this[levelKey] ?: legacy.position, this[mutedKey] ?: legacy.muted)
    }

    private fun MutablePreferences.setSound(sound: Sound, level: SoundLevel) {
        val (levelKey, mutedKey, _) = soundKeys(sound)
        this[levelKey] = level.position
        this[mutedKey] = level.muted
    }

    private fun soundKeys(sound: Sound) = when (sound) {
        Sound.Music -> Triple(MUSIC_LEVEL, MUSIC_MUTED, MUSIC_VOLUME)
        Sound.Video -> Triple(VIDEO_LEVEL, VIDEO_MUTED, VIDEO_VOLUME)
    }

    private fun MutablePreferences.setFavorites(list: List<String>) {
        this[FAVORITES] = list.joinToString(",")
    }

    private fun MutablePreferences.setRoles(roles: Map<String, Role>) {
        this[ROLES] = roles.entries.joinToString(",") { "${it.key}=${it.value.name}" }
    }

    /**
     * Moves [pkg] by [delta] places counting only the [shown] entries. The others (apps that are
     * hidden or not installed on this TV) keep their slots, so they're back in place if the app returns.
     */
    private fun List<String>.moveAmong(shown: Set<String>, pkg: String, delta: Int): List<String> {
        val visible = filter { it in shown }.toMutableList()
        val from = visible.indexOf(pkg)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, visible.lastIndex)
        visible.add(to, visible.removeAt(from))
        val reordered = visible.iterator()
        return map { if (it in shown) reordered.next() else it }
    }

    private companion object {
        // Stored as comma-separated strings because string sets don't keep order.
        val FAVORITES = stringPreferencesKey("favorites")
        val APP_ORDER = stringPreferencesKey("app_order")
        val HOME_STYLE = stringPreferencesKey("home_style")
        val ROLES = stringPreferencesKey("role_overrides")
        val MUSIC_LEVEL = floatPreferencesKey("music_level")
        val MUSIC_MUTED = booleanPreferencesKey("music_muted")
        val VIDEO_LEVEL = floatPreferencesKey("video_level")
        val VIDEO_MUTED = booleanPreferencesKey("video_muted")
        // The older one-button settings, read only to carry them over.
        val MUSIC_VOLUME = stringPreferencesKey("music_volume")
        val VIDEO_VOLUME = stringPreferencesKey("video_volume")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val BACKGROUND = longPreferencesKey("background_color")
        val USE_WALLPAPER = booleanPreferencesKey("use_wallpaper")
        val WALLPAPER_NAME = stringPreferencesKey("wallpaper_name")
        val USE_24H = booleanPreferencesKey("use_24h_clock")
        val REDIRECT_HOME = booleanPreferencesKey("redirect_home")
    }
}

/** Sorts items by their position in [saved]; items not in it keep their current order, after the rest. */
fun <T> List<T>.inSavedOrder(saved: List<String>, key: (T) -> String): List<T> {
    val position = saved.withIndex().associate { (index, pkg) -> pkg to index }
    return sortedBy { position[key(it)] ?: Int.MAX_VALUE }
}
