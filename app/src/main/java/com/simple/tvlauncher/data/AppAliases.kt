package com.simple.tvlauncher.data

/**
 * Apps that are the same app under a different package name on Fire TV and Google TV, so an
 * imported setup keeps their places. Add a group after checking both names with
 * `adb shell pm list packages` on each kind of TV.
 */
object AppAliases {
    private val GROUPS = listOf(
        setOf("com.google.android.youtube.tv", "com.amazon.firetv.youtube"),
    )

    /** The packages that were swapped for an installed equivalent, as old to new. */
    data class Result(val settings: LauncherSettings, val swapped: Map<String, String>)

    /** Swaps each app that isn't [installed] for an installed equivalent, if there is one. */
    fun apply(settings: LauncherSettings, installed: Set<String>): Result {
        val swapped = mutableMapOf<String, String>()
        fun swap(pkg: String): String =
            if (pkg in installed) pkg
            else GROUPS.firstOrNull { pkg in it }?.firstOrNull { it in installed }?.also { swapped[pkg] = it } ?: pkg

        val result = settings.copy(
            favorites = settings.favorites.map(::swap).distinct(),
            appOrder = settings.appOrder.map(::swap).distinct(),
            hidden = settings.hidden.map(::swap).toSet(),
            roleOverrides = settings.roleOverrides.mapKeys { swap(it.key) },
        )
        return Result(result, swapped)
    }
}
