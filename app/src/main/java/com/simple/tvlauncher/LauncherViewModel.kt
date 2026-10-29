package com.simple.tvlauncher

import android.app.Application
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simple.tvlauncher.data.AppInfo
import com.simple.tvlauncher.data.AppRepository
import com.simple.tvlauncher.data.LauncherSettings
import com.simple.tvlauncher.data.SettingsStore
import com.simple.tvlauncher.data.TvInput
import com.simple.tvlauncher.data.TvInputs
import com.simple.tvlauncher.data.Wallpaper
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
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private val repository = AppRepository(application)
    private val store = SettingsStore(application)

    val state: StateFlow<LauncherState> = combine(repository.apps(), store.settings) { apps, settings ->
        val byPackage = apps.associateBy { it.packageName }
        LauncherState(
            loaded = true,
            settings = settings,
            favorites = settings.favorites.filter { it !in settings.hidden }.mapNotNull { byPackage[it] },
            apps = apps.filter { it.packageName !in settings.hidden },
            hiddenApps = apps.filter { it.packageName in settings.hidden },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherState())

    /** Path + last-modified time, so the picture is only re-decoded when the file changes. */
    private val wallpaperFile = MutableStateFlow<Pair<String, Long>?>(null)

    val wallpaper: StateFlow<ImageBitmap?> = combine(
        store.settings.map { it.useWallpaper }.distinctUntilChanged(),
        wallpaperFile,
    ) { use, file ->
        if (use && file != null) withContext(Dispatchers.IO) { Wallpaper.load(File(file.first)) } else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val wallpaperFound: StateFlow<Boolean> =
        wallpaperFile.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val wallpaperFolder: String = Wallpaper.folderPath(application)

    private val _inputs = MutableStateFlow<List<TvInput>>(emptyList())
    val inputs: StateFlow<List<TvInput>> = _inputs.asStateFlow()

    private val _homeServiceEnabled = MutableStateFlow(false)
    val homeServiceEnabled: StateFlow<Boolean> = _homeServiceEnabled.asStateFlow()

    /** Called every time the home screen comes back into view. */
    fun onResume() {
        _homeServiceEnabled.value = HomeButtonService.isEnabled(app)
        viewModelScope.launch(Dispatchers.IO) {
            wallpaperFile.value = Wallpaper.find(app)?.let { it.path to it.lastModified() }
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
    fun setHidden(pkg: String, hidden: Boolean) = viewModelScope.launch { store.setHidden(pkg, hidden) }
    fun setUse24HourClock(enabled: Boolean) = viewModelScope.launch { store.setUse24HourClock(enabled) }
    fun setUseWallpaper(enabled: Boolean) = viewModelScope.launch { store.setUseWallpaper(enabled) }

    fun setBackgroundColor(color: Long) = viewModelScope.launch {
        store.setBackgroundColor(color)
        store.setUseWallpaper(false)
    }

    private fun toast(message: String) = Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
}
