package com.simple.tvlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
    val musicVolume: MusicVolume = MusicVolume.Quiet,
) {
    companion object {
        const val DEFAULT_BACKGROUND = 0xFF101418
    }
}

enum class HomeStyle { HeroSelect, Classic }

/** Background music loudness, as a fraction of the TV's media volume. */
enum class MusicVolume(val label: String, val level: Float) {
    Off("Off", 0f),
    Quiet("Quiet", 0.12f),
    Medium("Medium", 0.25f),
    Loud("Loud", 0.45f);

    fun next(): MusicVolume = entries[(ordinal + 1) % entries.size]
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
                musicVolume = MusicVolume.entries.firstOrNull { it.name == p[MUSIC_VOLUME] } ?: MusicVolume.Quiet,
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
        p[MUSIC_VOLUME] = settings.musicVolume.name
    }

    suspend fun setMusicVolume(volume: MusicVolume) = store.edit { it[MUSIC_VOLUME] = volume.name }

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
        val MUSIC_VOLUME = stringPreferencesKey("music_volume")
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
