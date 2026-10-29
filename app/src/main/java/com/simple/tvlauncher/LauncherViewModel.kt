package com.simple.tvlauncher

import android.app.Application
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simple.tvlauncher.data.AppInfo
import com.simple.tvlauncher.data.AppRepository
import com.simple.tvlauncher.data.HomeStyle
import com.simple.tvlauncher.data.Role
import com.simple.tvlauncher.data.LauncherSettings
import com.simple.tvlauncher.data.SettingsStore
import com.simple.tvlauncher.data.TvInput
import com.simple.tvlauncher.data.TvInputs
import com.simple.tvlauncher.data.Wallpaper
import com.simple.tvlauncher.data.WallpaperOption
import com.simple.tvlauncher.data.inSavedOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LauncherState(
    val loaded: Boolean = false,
    val settings: LauncherSettings = LauncherSettings(),
    val favorites: List<AppInfo> = emptyList(),
    /** Every visible (not hidden) app, favorites included. */
    val apps: List<AppInfo> = emptyList(),
    val hiddenApps: List<AppInfo> = emptyList(),
    /** Visible apps grouped by hero-select role, in Tank, Damage, Support order. */
    val roles: Map<Role, List<AppInfo>> = emptyMap(),
) {
    fun roleOf(app: AppInfo): Role = settings.roleOverrides[app.packageName] ?: Role.default(app)
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private val repository = AppRepository(application)
    private val store = SettingsStore(application)

    val state: StateFlow<LauncherState> = combine(repository.apps(), store.settings) { apps, settings ->
        val byPackage = apps.associateBy { it.packageName }
        val visible = apps.filter { it.packageName !in settings.hidden }.inSavedOrder(settings.appOrder) { it.packageName }
        LauncherState(
            loaded = true,
            settings = settings,
            favorites = settings.favorites.filter { it !in settings.hidden }.mapNotNull { byPackage[it] },
            apps = visible,
            hiddenApps = apps.filter { it.packageName in settings.hidden },
        ).let { state ->
            val grouped = visible.groupBy { state.roleOf(it) }
            state.copy(roles = Role.entries.associateWith { grouped[it].orEmpty() })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherState())

    /** Pictures in the wallpaper folder as path + last-modified time, so they're only re-decoded when files change. */
    private val wallpaperFiles = MutableStateFlow<List<Pair<String, Long>>>(emptyList())

    val wallpaper: StateFlow<ImageBitmap?> = combine(store.settings, wallpaperFiles) { settings, files ->
        if (!settings.useWallpaper) return@combine null
        // If the chosen picture was deleted (or none was chosen yet), use the first one.
        files.firstOrNull { File(it.first).name == settings.wallpaperName } ?: files.firstOrNull()
    }
        .distinctUntilChanged()
        .map { file -> file?.let { withContext(Dispatchers.IO) { Wallpaper.load(File(it.first)) } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val wallpaperOptions: StateFlow<List<WallpaperOption>> = wallpaperFiles
        .map { files ->
            withContext(Dispatchers.IO) {
                files.map { (path, _) -> File(path).let { WallpaperOption(it.name, Wallpaper.load(it, maxWidth = 320)) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wallpaperFolder: String = Wallpaper.folderPath(application)

    private val _inputs = MutableStateFlow<List<TvInput>>(emptyList())
    val inputs: StateFlow<List<TvInput>> = _inputs.asStateFlow()

    private val _homeServiceEnabled = MutableStateFlow(false)
    val homeServiceEnabled: StateFlow<Boolean> = _homeServiceEnabled.asStateFlow()

    /** Called every time the home screen comes back into view. */
    fun onResume() {
        _homeServiceEnabled.value = HomeButtonService.isEnabled(app)
        viewModelScope.launch(Dispatchers.IO) {
            wallpaperFiles.value = Wallpaper.list(app).map { it.path to it.lastModified() }
            _inputs.value = TvInputs.list(app)
        }
    }

    fun launch(appInfo: AppInfo) {
        if (!repository.launch(appInfo)) toast("Couldn't open ${appInfo.label}")
    }

    fun openInput(input: TvInput) {
        if (!TvInputs.open(app, input)) toast("Couldn't switch to ${input.label}")
    }

    fun toggleFavorite(pkg: String) = viewModelScope.launch { store.toggleFavorite(pkg) }
    fun moveFavorite(pkg: String, delta: Int) = viewModelScope.launch { store.moveFavorite(pkg, delta) }
    fun moveApp(pkg: String, delta: Int) = viewModelScope.launch {
        val visibleAToZ = state.value.apps.sortedBy { it.label.lowercase() }.map { it.packageName }
        store.moveApp(pkg, delta, visibleAToZ)
    }

    fun setHomeStyle(style: HomeStyle) = viewModelScope.launch { store.setHomeStyle(style) }
    fun setRole(pkg: String, role: Role) = viewModelScope.launch { store.setRole(pkg, role) }
    fun resetAppOrder() = viewModelScope.launch { store.resetAppOrder() }
    fun setHidden(pkg: String, hidden: Boolean) = viewModelScope.launch { store.setHidden(pkg, hidden) }
    fun setUse24HourClock(enabled: Boolean) = viewModelScope.launch { store.setUse24HourClock(enabled) }
    fun setWallpaper(name: String) = viewModelScope.launch { store.setWallpaper(name) }
    fun setBackgroundColor(color: Long) = viewModelScope.launch { store.setBackgroundColor(color) }

    private fun toast(message: String) = Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
}
