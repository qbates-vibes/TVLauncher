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
                favorites = p.favorites(),
                hidden = p[HIDDEN] ?: emptySet(),
                backgroundColor = p[BACKGROUND] ?: LauncherSettings.DEFAULT_BACKGROUND,
                useWallpaper = p[USE_WALLPAPER] ?: false,
                use24HourClock = p[USE_24H] ?: false,
            )
        }

    suspend fun toggleFavorite(pkg: String) = store.edit { p ->
        val current = p.favorites()
        p.setFavorites(if (pkg in current) current - pkg else current + pkg)
    }

    /** Moves a favorite left (delta < 0) or right (delta > 0). */
    suspend fun moveFavorite(pkg: String, delta: Int) = store.edit { p ->
        val list = p.favorites().toMutableList()
        val from = list.indexOf(pkg)
        if (from < 0) return@edit
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        p.setFavorites(list)
    }

    suspend fun setHidden(pkg: String, hidden: Boolean) = store.edit { p ->
        val current = p[HIDDEN] ?: emptySet()
        p[HIDDEN] = if (hidden) current + pkg else current - pkg
        if (hidden) p.setFavorites(p.favorites() - pkg)
    }

    suspend fun setBackgroundColor(color: Long) = store.edit { it[BACKGROUND] = color }
    suspend fun setUseWallpaper(enabled: Boolean) = store.edit { it[USE_WALLPAPER] = enabled }
    suspend fun setUse24HourClock(enabled: Boolean) = store.edit { it[USE_24H] = enabled }

    private fun Preferences.favorites(): List<String> =
        this[FAVORITES]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    private fun MutablePreferences.setFavorites(list: List<String>) {
        this[FAVORITES] = list.joinToString(",")
    }

    private companion object {
        // Stored as a comma-separated string because string sets don't keep order.
        val FAVORITES = stringPreferencesKey("favorites")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val BACKGROUND = longPreferencesKey("background_color")
        val USE_WALLPAPER = booleanPreferencesKey("use_wallpaper")
        val USE_24H = booleanPreferencesKey("use_24h_clock")
    }
}
