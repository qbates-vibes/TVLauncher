@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.simple.tvlauncher.LauncherViewModel
import com.simple.tvlauncher.data.AppInfo
import com.simple.tvlauncher.data.HomeStyle
import com.simple.tvlauncher.data.startSafely
import kotlinx.coroutines.flow.StateFlow
import androidx.tv.material3.ExperimentalTvMaterial3Api

private enum class Screen { Home, Settings }

@Composable
fun LauncherApp(viewModel: LauncherViewModel, homePresses: StateFlow<Int>) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wallpaper by viewModel.wallpaper.collectAsStateWithLifecycle()
    val wallpaperOptions by viewModel.wallpaperOptions.collectAsStateWithLifecycle()
    val logo by viewModel.logo.collectAsStateWithLifecycle()
    val musicTracks by viewModel.musicTracks.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val homeServiceEnabled by viewModel.homeServiceEnabled.collectAsStateWithLifecycle()
    val homePressCount by homePresses.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf(Screen.Home) }
    var optionsFor by remember { mutableStateOf<Pair<AppInfo, Section>?>(null) }
    var showInputs by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Moving?>(null) }
    var focusReset by remember { mutableIntStateOf(0) }

    // Pressing Home while already here returns to a clean home screen.
    LaunchedEffect(homePressCount) {
        screen = Screen.Home
        optionsFor = null
        showInputs = false
        moving = null
        focusReset++
    }

    // Back never leaves the home screen; it only steps out of sub-screens.
    BackHandler {
        when {
            moving != null -> moving = null
            screen == Screen.Settings -> screen = Screen.Home
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(state.settings.backgroundColor))
            ) {
                val picture = wallpaper
                if (state.settings.useWallpaper && picture != null) {
                    Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    // Darken the picture a little so text and tiles stay readable.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                }

                when {
                    screen == Screen.Home && state.settings.homeStyle == HomeStyle.HeroSelect -> HeroScreen(
                        state = state,
                        focusResetKey = focusReset,
                        hasWallpaper = state.settings.useWallpaper && picture != null,
                        logo = logo,
                        onLaunch = { viewModel.launch(it) },
                        onOptions = { optionsFor = it to Section.Apps },
                        onOpenInputs = { showInputs = true },
                        onOpenTvSettings = { startSafely(context, Intent(Settings.ACTION_SETTINGS)) },
                        onOpenLauncherSettings = { screen = Screen.Settings },
                    )
                    screen == Screen.Home -> HomeScreen(
                        state = state,
                        focusResetKey = focusReset,
                        moving = moving,
                        onLaunch = { viewModel.launch(it) },
                        onOptions = { app, section -> optionsFor = app to section },
                        onMove = { delta ->
                            moving?.let {
                                when (it.section) {
                                    Section.Favorites -> viewModel.moveFavorite(it.packageName, delta)
                                    Section.Apps -> viewModel.moveApp(it.packageName, delta)
                                }
                            }
                        },
                        onStopMoving = { moving = null },
                        onOpenInputs = { showInputs = true },
                        onOpenTvSettings = { startSafely(context, Intent(Settings.ACTION_SETTINGS)) },
                        onOpenLauncherSettings = { screen = Screen.Settings },
                    )
                    else -> SettingsScreen(
                        state = state,
                        wallpaperOptions = wallpaperOptions,
                        wallpaperFolder = viewModel.wallpaperFolder,
                        homeServiceEnabled = homeServiceEnabled,
                        onBack = { screen = Screen.Home },
                        onSetBackgroundColor = { viewModel.setBackgroundColor(it) },
                        onSelectWallpaper = { viewModel.setWallpaper(it) },
                        onSetUse24HourClock = { viewModel.setUse24HourClock(it) },
                        onUnhide = { viewModel.setHidden(it, false) },
                        onResetAppOrder = { viewModel.resetAppOrder() },
                        onSetHomeStyle = { viewModel.setHomeStyle(it) },
                        logoFound = logo != null,
                        logoFolder = viewModel.logoFolder,
                        musicTrackCount = musicTracks.size,
                        musicFolder = viewModel.musicFolder,
                        onSetMusicVolume = { viewModel.setMusicVolume(it) },
                        onOpenAccessibilitySettings = {
                            startSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
            }

            optionsFor?.let { (app, section) ->
                val heroSelect = state.settings.homeStyle == HomeStyle.HeroSelect
                val role = state.roleOf(app)
                AppOptionsDialog(
                    app = app,
                    isFavorite = app.packageName in state.settings.favorites,
                    // Hero select groups apps by role, so free reordering doesn't apply there.
                    moveLabel = when {
                        heroSelect -> null
                        section == Section.Favorites -> "Move (reorder favorites)"
                        else -> "Move (reorder all apps)"
                    },
                    roleLabel = if (heroSelect) "Role: ${role.label}  →  ${role.next().label}" else null,
                    onDismiss = { optionsFor = null },
                    onOpen = {
                        optionsFor = null
                        viewModel.launch(app)
                    },
                    onToggleFavorite = {
                        optionsFor = null
                        viewModel.toggleFavorite(app.packageName)
                    },
                    onStartMoving = {
                        optionsFor = null
                        moving = Moving(app.packageName, section)
                    },
                    onChangeRole = {
                        optionsFor = null
                        viewModel.setRole(app.packageName, role.next())
                    },
                    onHide = {
                        optionsFor = null
                        viewModel.setHidden(app.packageName, true)
                    },
                    onAppInfo = {
                        optionsFor = null
                        startSafely(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
                        )
                    },
                )
            }

            if (showInputs) {
                InputsDialog(
                    inputs = inputs,
                    onSelect = {
                        showInputs = false
                        viewModel.openInput(it)
                    },
                    onDismiss = { showInputs = false },
                )
            }
        }
    }
}
