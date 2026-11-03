package com.simple.tvlauncher.data

import android.content.pm.ApplicationInfo

/** Hero-select roles. Watch = Tank, Play/Listen = Damage, everything else = Support. */
enum class Role(val label: String) {
    Tank("Tank"),
    Damage("Damage"),
    Support("Support");

    fun next(): Role = entries[(ordinal + 1) % entries.size]

    companion object {
        fun default(app: AppInfo): Role = when {
            app.category == ApplicationInfo.CATEGORY_VIDEO || app.category == ApplicationInfo.CATEGORY_IMAGE -> Tank
            app.category == ApplicationInfo.CATEGORY_GAME || app.category == ApplicationInfo.CATEGORY_AUDIO -> Damage
            // Many apps don't declare a category, so fall back to well-known package names.
            // Listening is checked first ("youtube.tvmusic" is music), and games last ("gameday" is baseball).
            LISTEN_HINTS.any { it in app.packageName } -> Damage
            WATCH_HINTS.any { it in app.packageName } -> Tank
            GAME_HINTS.any { it in app.packageName } -> Damage
            else -> Support
        }

        private val LISTEN_HINTS = listOf(
            "music", "spotify", "pandora", "tidal", "deezer", "audible", "podcast", "sirius", "iheart", "soundcloud",
        )
        private val WATCH_HINTS = listOf(
            "netflix", "youtube", "hulu", "disney", "hbo", "wbd", "amazon.avod", "amazonvideo", "plex",
            "peacock", "paramount", "cbs", "appletv", "atve", "tubi", "pluto", "crunchyroll", "sling",
            "fubo", "vudu", "twitch", "jellyfin", "kodi", "vlc", "philo", "starz", "showtime", "britbox", "mubi",
            "espn", "fox", "nbc", "abc", "pbs", "cnn", "bbc", "news", "sports", "mlb", "bamnetworks", "nfl", "nba", "nhl",
        )
        private val GAME_HINTS = listOf("game", "geforce", "xbox", "steam", "moonlight", "retroarch", "emulat")
    }
}
