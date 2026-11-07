@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.simple.tvlauncher.data.Sound
import com.simple.tvlauncher.data.SoundLevel
import com.simple.tvlauncher.data.Snapshot
import com.simple.tvlauncher.data.StateTransfer
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
    onSetRedirectHome: (Boolean) -> Unit,
    onUnhide: (String) -> Unit,
    onResetAppOrder: () -> Unit,
    onSetHomeStyle: (HomeStyle) -> Unit,
    onStepScenicOpacity: (steps: Int) -> Unit,
    logoFound: Boolean,
    logoFolder: String,
    musicTrackCount: Int,
    musicFolder: String,
    onStepSound: (Sound, steps: Int) -> Unit,
    onSetMuted: (Sound, Boolean) -> Unit,
    /** Newest first. */
    snapshots: List<Snapshot>,
    onRestore: (Snapshot) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val settings = state.settings
    val backButton = remember { FocusRequester() }
    var confirming by remember { mutableStateOf<Snapshot?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }
    var resetRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        backButton.requestFocusSafely()
    }

    var showHidden by remember { mutableStateOf(false) }
    val hiddenTiles = remember { mutableMapOf<String, FocusRequester>() }
    // The last focusable item above the hidden apps, for when the last one is unhidden.
    val aboveHidden = remember { FocusRequester() }
    var unhiddenIndex by remember { mutableStateOf<Int?>(null) }
    // An unhidden app's tile disappears, so move focus to the app that took its place.
    LaunchedEffect(state.hiddenApps) {
        val index = unhiddenIndex ?: return@LaunchedEffect
        unhiddenIndex = null
        withFrameNanos { }
        val next = state.hiddenApps.getOrNull(index.coerceAtMost(state.hiddenApps.lastIndex))
        if (next == null) {
            showHidden = false
            aboveHidden.requestFocusSafely()
        } else {
            hiddenTiles[next.packageName]?.requestFocusSafely()
        }
    }
    // The reset button disappears once the order is cleared, so move focus to the item above it.
    LaunchedEffect(settings.appOrder.isEmpty()) {
        if (!resetRequested || settings.appOrder.isNotEmpty()) return@LaunchedEffect
        resetRequested = false
        withFrameNanos { }
        aboveHidden.requestFocusSafely()
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
            LauncherButton(
                text = "Style: " + settings.homeStyle.label,
                onClick = { onSetHomeStyle(settings.homeStyle.next()) },
            )
        }
        item {
            HelpText(
                when (settings.homeStyle) {
                    HomeStyle.Classic -> "Classic: favorites and all your apps in a grid."
                    HomeStyle.Scenic -> "Scenic: like Classic without favorites, with the apps low on the screen so your wallpaper shows. Press Down to bring up more apps."
                    HomeStyle.HeroSelect -> "Hero select: apps as heroes in a game-style roster."
                }
            )
        }
        if (settings.homeStyle == HomeStyle.Scenic) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Apps when idle", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(190.dp))
                    PercentSlider(settings.scenicOpacity, onStep = onStepScenicOpacity)
                }
            }
            item { HelpText("How see-through the apps are until you press a button: 0% hides them, 100% keeps them solid.") }
        }

        item {
            HelpText(
                if (logoFound) "Hero select logo: found."
                else "Hero select logo: none. To show one, copy a PNG into:\n$logoFolder"
            )
        }

        item { SectionTitle("Background music (Hero select only)") }
        item {
            SoundControls("Music", settings.music, { onStepSound(Sound.Music, it) }, { onSetMuted(Sound.Music, it) })
        }
        item {
            HelpText(
                when (musicTrackCount) {
                    0 -> "No music yet. Copy MP3, M4A or OGG files into:\n$musicFolder\n(see README)"
                    1 -> "1 song. It loops while the home screen is open, starting 30 seconds in unless the TV just turned on."
                    else -> "$musicTrackCount songs. When the TV turns on they play once each in A–Z order, then the last one loops. " +
                        "Other visits to the home screen play only the last one, after 30 seconds."
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
        item { SectionTitle("Wallpaper picture or video") }
        if (wallpaperOptions.isEmpty()) {
            item {
                HelpText("No pictures or videos yet. Copy JPG, PNG or WebP pictures, or MP4, MKV or WebM videos, into:\n$wallpaperFolder\n(see README for the adb push command)")
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
            item { HelpText("To add more, copy pictures or videos into $wallpaperFolder (see README).") }
        }
        item {
            SoundControls("Video sound", settings.videoSound, { onStepSound(Sound.Video, it) }, { onSetMuted(Sound.Video, it) })
        }
        item {
            HelpText("For video wallpapers. Background music stays off while a video's sound is on.")
        }

        item { SectionTitle("Clock") }
        item {
            LauncherButton(
                text = "24-hour clock: " + if (settings.use24HourClock) "On" else "Off",
                onClick = { onSetUse24HourClock(!settings.use24HourClock) },
                modifier = if (settings.appOrder.isEmpty()) Modifier.focusRequester(aboveHidden) else Modifier,
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
            item { LauncherButton("Reset to A–Z order", { confirmingReset = true }, Modifier.focusRequester(aboveHidden)) }
        }

        item { SectionTitle("Hidden apps") }
        if (state.hiddenApps.isEmpty()) {
            item { HelpText("No hidden apps. On the home screen, hold OK on an app and choose Hide app.") }
        } else {
            item {
                LauncherButton(
                    text = if (showHidden) "Close list" else "Show hidden apps (${state.hiddenApps.size})",
                    onClick = { showHidden = !showHidden },
                )
            }
            if (showHidden) {
                item {
                    LazyRow(
                        modifier = Modifier.bleed(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(state.hiddenApps, key = { _, app -> "hidden:${app.packageName}" }) { index, app ->
                            AppTile(
                                app = app,
                                onClick = {
                                    unhiddenIndex = index
                                    onUnhide(app.packageName)
                                },
                                onLongClick = {},
                                modifier = Modifier
                                    .width(192.dp)
                                    .focusRequester(hiddenTiles.getOrPut(app.packageName) { FocusRequester() }),
                            )
                        }
                    }
                }
                item { HelpText("Select an app to put it back on the home screen.") }
            }
        }

        item { SectionTitle("Saved setups") }
        item {
            HelpText(
                if (snapshots.isEmpty()) "Nothing here yet. Each time you load a setup from your Mac (see README), the one it replaces is kept here."
                else "Each time a setup loads or is restored, the one it replaced is kept here (the newest ${StateTransfer.KEEP_SNAPSHOTS})."
            )
        }
        snapshots.firstOrNull()?.let { latest ->
            item { LauncherButton("Undo last change", { confirming = latest }) }
        }
        items(snapshots, key = { "snapshot:${it.folder.name}" }) { snapshot ->
            LauncherButton("Restore: ${snapshot.label}", { confirming = snapshot })
        }

        item { SectionTitle("Home button helper (optional)") }
        item {
            HelpText(
                when {
                    !homeServiceEnabled -> "Off. Turn this on only if the Home button keeps opening the stock home screen."
                    settings.redirectHome -> "On: the Home button opens this launcher."
                    else -> "Installed, but redirecting is off: the Home button opens the stock home screen."
                }
            )
        }
        item {
            LauncherButton(
                text = "Redirect Home button: " + if (settings.redirectHome) "On" else "Off",
                onClick = { onSetRedirectHome(!settings.redirectHome) },
            )
        }
        item { LauncherButton("Open accessibility settings", onOpenAccessibilitySettings) }
    }

    confirming?.let { snapshot ->
        RestoreDialog(
            snapshot = snapshot,
            onRestore = {
                confirming = null
                onRestore(snapshot)
            },
            onDismiss = { confirming = null },
        )
    }

    if (confirmingReset) {
        ResetOrderDialog(
            onReset = {
                confirmingReset = false
                resetRequested = true
                onResetAppOrder()
            },
            onDismiss = { confirmingReset = false },
        )
    }
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
}

/** A mute button and a volume slider side by side. */
@Composable
private fun SoundControls(name: String, sound: SoundLevel, onStep: (Int) -> Unit, onSetMuted: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LauncherButton(
            text = "$name: " + if (sound.muted) "Muted" else "On",
            onClick = { onSetMuted(!sound.muted) },
            modifier = Modifier.width(190.dp),
        )
        PercentSlider(sound.position, onStep = onStep, dimmed = sound.muted, label = if (sound.muted) "Muted" else null)
    }
}

/** A slider for the remote: select it, then press Left and Right. Shows [label], or the position as a percentage. */
@Composable
private fun PercentSlider(position: Float, onStep: (Int) -> Unit, dimmed: Boolean = false, label: String? = null) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onStep(-1); true }
                    Key.DirectionRight -> { onStep(1); true }
                    else -> false
                }
            }
            .focusable()
            .background(Color.White.copy(alpha = if (focused) 0.18f else 0.08f), shape)
            .border(if (focused) 3.dp else 1.dp, Color.White.copy(alpha = if (focused) 1f else 0.3f), shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("−", style = MaterialTheme.typography.titleMedium)
        Box(
            Modifier
                .width(260.dp)
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(position)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = if (dimmed) 0.35f else 1f), RoundedCornerShape(4.dp))
            )
        }
        Text("+", style = MaterialTheme.typography.titleMedium)
        Text(
            text = label ?: "${Math.round(position * 100)}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(56.dp),
        )
    }
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
            text = (if (selected) "✓ " else "") + (if (option.isVideo) "▶ " else "") + option.name.substringBeforeLast('.'),
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
