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
    val use24HourClock: Boolean = false,
    /** Package names in the order you arranged the app grid. Apps not listed go at the end, A–Z. */
    val appOrder: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_BACKGROUND = 0xFF101418
    }
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
                use24HourClock = p[USE_24H] ?: false,
                appOrder = p.list(APP_ORDER),
            )
        }

    suspend fun toggleFavorite(pkg: String) = store.edit { p ->
        val current = p.list(FAVORITES)
        p.setFavorites(if (pkg in current) current - pkg else current + pkg)
    }

    /** Moves a favorite left (delta < 0) or right (delta > 0). */
    suspend fun moveFavorite(pkg: String, delta: Int) = store.edit { p ->
        val list = p.list(FAVORITES).toMutableList()
        val from = list.indexOf(pkg)
        if (from < 0) return@edit
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        p.setFavorites(list)
    }

    /**
     * Moves an app in the grid by [delta] places (±1 for left/right, ±columns for up/down).
     * [visibleApps] is every visible app in A–Z order, used to place apps you haven't arranged yet.
     */
    suspend fun moveApp(pkg: String, delta: Int, visibleApps: List<String>) = store.edit { p ->
        val list = visibleApps.inSavedOrder(p.list(APP_ORDER)) { it }.toMutableList()
        val from = list.indexOf(pkg)
        if (from < 0) return@edit
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        p[APP_ORDER] = list.joinToString(",")
    }

    suspend fun resetAppOrder() = store.edit { it.remove(APP_ORDER) }

    suspend fun setHidden(pkg: String, hidden: Boolean) = store.edit { p ->
        val current = p[HIDDEN] ?: emptySet()
        p[HIDDEN] = if (hidden) current + pkg else current - pkg
        if (hidden) p.setFavorites(p.list(FAVORITES) - pkg)
    }

    suspend fun setBackgroundColor(color: Long) = store.edit { it[BACKGROUND] = color }
    suspend fun setUseWallpaper(enabled: Boolean) = store.edit { it[USE_WALLPAPER] = enabled }
    suspend fun setUse24HourClock(enabled: Boolean) = store.edit { it[USE_24H] = enabled }

    private fun Preferences.list(key: Preferences.Key<String>): List<String> =
        this[key]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    private fun MutablePreferences.setFavorites(list: List<String>) {
        this[FAVORITES] = list.joinToString(",")
    }

    private companion object {
        // Stored as comma-separated strings because string sets don't keep order.
        val FAVORITES = stringPreferencesKey("favorites")
        val APP_ORDER = stringPreferencesKey("app_order")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val BACKGROUND = longPreferencesKey("background_color")
        val USE_WALLPAPER = booleanPreferencesKey("use_wallpaper")
        val USE_24H = booleanPreferencesKey("use_24h_clock")
    }
}

/** Sorts items by their position in [saved]; items not in it keep their current order, after the rest. */
fun <T> List<T>.inSavedOrder(saved: List<String>, key: (T) -> String): List<T> {
    val position = saved.withIndex().associate { (index, pkg) -> pkg to index }
    return sortedBy { position[key(it)] ?: Int.MAX_VALUE }
}
