@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.simple.tvlauncher.LauncherState
import com.simple.tvlauncher.data.HomeStyle
import com.simple.tvlauncher.data.MusicVolume
import com.simple.tvlauncher.data.WallpaperOption
import androidx.tv.material3.ExperimentalTvMaterial3Api

private val BackgroundColors = listOf(
    0xFF101418, // charcoal (default)
    0xFF000000, // black
    0xFF0D1B2A, // navy
    0xFF13261A, // forest
    0xFF2A1830, // plum
    0xFF2E2A24, // warm gray
)

@Composable
fun SettingsScreen(
    state: LauncherState,
    wallpaperOptions: List<WallpaperOption>,
    wallpaperFolder: String,
    homeServiceEnabled: Boolean,
    onBack: () -> Unit,
    onSetBackgroundColor: (Long) -> Unit,
    onSelectWallpaper: (String) -> Unit,
    onSetUse24HourClock: (Boolean) -> Unit,
    onUnhide: (String) -> Unit,
    onResetAppOrder: () -> Unit,
    onSetHomeStyle: (HomeStyle) -> Unit,
    logoFound: Boolean,
    logoFolder: String,
    musicTrackCount: Int,
    musicFolder: String,
    onSetMusicVolume: (MusicVolume) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val settings = state.settings
    val backButton = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        backButton.requestFocusSafely()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LauncherButton("← Back", onBack, Modifier.focusRequester(backButton))
                Text("Launcher settings", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item { SectionTitle("Home screen style") }
        item {
            val heroSelect = settings.homeStyle == HomeStyle.HeroSelect
            LauncherButton(
                text = "Style: " + if (heroSelect) "Hero select" else "Classic",
                onClick = { onSetHomeStyle(if (heroSelect) HomeStyle.Classic else HomeStyle.HeroSelect) },
            )
        }

        item {
            HelpText(
                if (logoFound) "Hero select logo: found."
                else "Hero select logo: none. To show one, copy a PNG into:\n$logoFolder"
            )
        }

        item { SectionTitle("Background music") }
        item {
            LauncherButton(
                text = "Music: " + settings.musicVolume.label,
                onClick = { onSetMusicVolume(settings.musicVolume.next()) },
            )
        }
        item {
            HelpText(
                when (musicTrackCount) {
                    0 -> "No music yet. Copy MP3, M4A or OGG files into:\n$musicFolder\n(see README)"
                    1 -> "1 song. It loops while the home screen is open."
                    else -> "$musicTrackCount songs. They play once each in A–Z order, then the last one loops."
                }
            )
        }

        item { SectionTitle("Background color") }
        item {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BackgroundColors.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = !settings.useWallpaper && color == settings.backgroundColor,
                        onClick = { onSetBackgroundColor(color) },
                    )
                }
            }
        }
        item { SectionTitle("Wallpaper picture") }
        if (wallpaperOptions.isEmpty()) {
            item {
                HelpText("No pictures yet. Copy JPG, PNG or WebP pictures into:\n$wallpaperFolder\n(see README for the adb push command)")
            }
        } else {
            // Same fallback as the home screen: a missing or unset choice means the first picture.
            val selectedName = (wallpaperOptions.firstOrNull { it.name == settings.wallpaperName } ?: wallpaperOptions.first()).name
            item {
                LazyRow(
                    modifier = Modifier.bleed(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(wallpaperOptions, key = { "wallpaper:${it.name}" }) { option ->
                        WallpaperThumbnail(
                            option = option,
                            selected = settings.useWallpaper && option.name == selectedName,
                            onClick = { onSelectWallpaper(option.name) },
                        )
                    }
                }
            }
            item { HelpText("To add more, copy pictures into $wallpaperFolder (see README).") }
        }

        item { SectionTitle("Clock") }
        item {
            LauncherButton(
                text = "24-hour clock: " + if (settings.use24HourClock) "On" else "Off",
                onClick = { onSetUse24HourClock(!settings.use24HourClock) },
            )
        }

        item { SectionTitle("App order") }
        item {
            HelpText(
                if (settings.appOrder.isEmpty()) "All apps are sorted A–Z. To rearrange, hold OK on an app and choose Move."
                else "You've arranged the All apps grid yourself. New apps are added at the end."
            )
        }
        if (settings.appOrder.isNotEmpty()) {
            item { LauncherButton("Reset to A–Z order", onResetAppOrder) }
        }

        item { SectionTitle("Hidden apps") }
        if (state.hiddenApps.isEmpty()) {
            item { HelpText("No hidden apps. On the home screen, hold OK on an app and choose Hide app.") }
        }
        items(state.hiddenApps, key = { "hidden:${it.packageName}" }) { app ->
            LauncherButton("Unhide ${app.label}", { onUnhide(app.packageName) })
        }

        item { SectionTitle("Home button helper (optional)") }
        item {
            HelpText(
                if (homeServiceEnabled) "On: the Home button opens this launcher."
                else "Off. Turn this on only if the Home button keeps opening the stock Google TV home screen."
            )
        }
        item { LauncherButton("Open accessibility settings", onOpenAccessibilitySettings) }
    }
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
}

@Composable
private fun WallpaperThumbnail(option: WallpaperOption, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 192.dp, height = 108.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TileColor,
            contentColor = Color.White,
            focusedContainerColor = TileColor,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
    ) {
        option.thumbnail?.let {
            Image(it, contentDescription = option.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(
            text = (if (selected) "✓ " else "") + option.name.substringBeforeLast('.'),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ColorSwatch(color: Long, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 72.dp, height = 44.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(color),
            contentColor = Color.White,
            focusedContainerColor = Color(color),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
    ) {
        if (selected) Text("✓", Modifier.align(Alignment.Center))
    }
}
