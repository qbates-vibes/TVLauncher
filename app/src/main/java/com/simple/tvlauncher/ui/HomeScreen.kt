@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package com.simple.tvlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.simple.tvlauncher.LauncherState
import com.simple.tvlauncher.data.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.ExperimentalTvMaterial3Api

private const val COLUMNS = 5
private val SidePadding = 48.dp
private val TileSpacing = 20.dp
private val FavoriteWidth = 200.dp
private val GridTopPadding = 12.dp
/** Room below the first row in Scenic; no more than the gap between rows, so the second row stays out of sight. */
private val ScenicBottomMargin = TileSpacing
private const val SCENIC_RESTING_ALPHA = 0.45f
private const val SCENIC_IDLE_MS = 30_000L

enum class Section { Favorites, Apps }

/** The tile currently being moved, and which section it's being moved in. */
data class Moving(val packageName: String, val section: Section)

/**
 * The Classic home screen: buttons and clock at the top, favorites, then every app in a grid.
 * With [scenic] there are no favorites and the grid starts low on the screen with only its first
 * row showing, so the wallpaper stays visible; moving down scrolls the rest of the apps up. The
 * apps rest semi-transparent, turn solid as soon as the remote is used, and fade back after
 * [SCENIC_IDLE_MS] without a button press.
 */
@Composable
fun HomeScreen(
    state: LauncherState,
    scenic: Boolean,
    focusResetKey: Int,
    moving: Moving?,
    onLaunch: (AppInfo) -> Unit,
    onOptions: (AppInfo, Section) -> Unit,
    onMove: (delta: Int) -> Unit,
    onStopMoving: () -> Unit,
    onOpenInputs: () -> Unit,
    onOpenTvSettings: () -> Unit,
    stockHomeLabel: String,
    onOpenStockHome: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val firstTile = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val showFavorites = !scenic && state.favorites.isNotEmpty()

    var hasFocus by remember { mutableStateOf(false) }

    var buttonPresses by remember { mutableIntStateOf(0) }
    var awake by remember { mutableStateOf(false) }
    LaunchedEffect(buttonPresses) {
        if (buttonPresses == 0) return@LaunchedEffect
        awake = true
        delay(SCENIC_IDLE_MS)
        awake = false
    }
    LaunchedEffect(focusResetKey) { awake = false }
    val appsAlpha by animateFloatAsState(
        targetValue = if (!scenic || awake) 1f else SCENIC_RESTING_ALPHA,
        animationSpec = tween(durationMillis = if (awake) 200 else 1200),
        label = "apps",
    )

    // Focus the first tile when apps first load, and whenever Home is pressed.
    LaunchedEffect(state.loaded, focusResetKey) {
        if (!state.loaded) return@LaunchedEffect
        gridState.scrollToItem(0)
        withFrameNanos { }
        firstTile.requestFocusSafely()
    }

    // If the focused tile disappears (hidden, unfavorited, uninstalled), don't leave the remote with nothing focused.
    LaunchedEffect(state, hasFocus) {
        if (!state.loaded || hasFocus) return@LaunchedEffect
        withFrameNanos { }
        if (!hasFocus) {
            gridState.scrollToItem(0)
            withFrameNanos { }
            firstTile.requestFocusSafely()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent {
                // Any button wakes the Scenic apps; the press still does its usual job.
                if (scenic && it.type == KeyEventType.KeyDown) buttonPresses++
                false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SidePadding, end = SidePadding, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LauncherButton("Inputs", onOpenInputs)
            LauncherButton("TV settings", onOpenTvSettings)
            LauncherButton(stockHomeLabel, onOpenStockHome)
            LauncherButton("Launcher settings", onOpenLauncherSettings)
            Spacer(Modifier.weight(1f))
            Clock(state.settings.use24HourClock)
        }

        if (state.loaded) BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = SidePadding)) {
            // Scenic: empty space above the first row, sized so that row sits just above the bottom edge.
            val tileHeight = (maxWidth - TileSpacing * (COLUMNS - 1)) / COLUMNS * 9f / 16f
            val scenicGap = (maxHeight - GridTopPadding - TileSpacing - tileHeight - ScenicBottomMargin).coerceAtLeast(0.dp)
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = appsAlpha },
                contentPadding = PaddingValues(top = GridTopPadding, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                if (scenic) {
                    item(key = "scenic-gap", span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(scenicGap))
                    }
                }
                if (showFavorites) {
                    item(key = "favorites-title", span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle("Favorites")
                    }
                    item(key = "favorites", span = { GridItemSpan(maxLineSpan) }) {
                        FavoritesRow(
                            favorites = state.favorites,
                            movingPackage = moving?.takeIf { it.section == Section.Favorites }?.packageName,
                            firstTile = firstTile,
                            onLaunch = onLaunch,
                            onOptions = { onOptions(it, Section.Favorites) },
                            onMove = onMove,
                            onStopMoving = onStopMoving,
                        )
                    }
                }
                if (!scenic) {
                    item(key = "apps-title", span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle("All apps  ·  hold OK on an app for options")
                    }
                }
                itemsIndexed(state.apps, key = { _, app -> "app:${app.packageName}" }) { index, app ->
                    val isFirst = index == 0 && !showFavorites
                    // Scenic: coming back to the first row slides the grid back down to show the wallpaper.
                    val backToScenic = if (scenic && index < COLUMNS) {
                        Modifier.onFocusChanged {
                            if (it.hasFocus) scope.launch {
                                withFrameNanos { }
                                gridState.animateScrollToItem(0)
                            }
                        }
                    } else {
                        Modifier
                    }
                    MovableTile(
                        app = app,
                        moving = moving == Moving(app.packageName, Section.Apps),
                        position = index,
                        verticalStep = COLUMNS,
                        onClick = { onLaunch(app) },
                        onLongClick = { onOptions(app, Section.Apps) },
                        onMove = onMove,
                        onStopMoving = onStopMoving,
                        modifier = backToScenic.then(if (isFirst) Modifier.focusRequester(firstTile) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesRow(
    favorites: List<AppInfo>,
    movingPackage: String?,
    firstTile: FocusRequester,
    onLaunch: (AppInfo) -> Unit,
    onOptions: (AppInfo) -> Unit,
    onMove: (delta: Int) -> Unit,
    onStopMoving: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.bleed(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        itemsIndexed(favorites, key = { _, app -> "fav:${app.packageName}" }) { index, app ->
            MovableTile(
                app = app,
                moving = app.packageName == movingPackage,
                position = index,
                verticalStep = null,
                onClick = { onLaunch(app) },
                onLongClick = { onOptions(app) },
                onMove = onMove,
                onStopMoving = onStopMoving,
                modifier = Modifier
                    .width(FavoriteWidth)
                    .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
            )
        }
    }
}

/** An [AppTile] that can be put in move mode: it grabs focus and stays on screen as it moves. */
@Composable
private fun MovableTile(
    app: AppInfo,
    moving: Boolean,
    position: Int,
    verticalStep: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (delta: Int) -> Unit,
    onStopMoving: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(moving) {
        if (moving) focusRequester.requestFocusSafely()
    }
    LaunchedEffect(moving, position) {
        if (!moving) return@LaunchedEffect
        withFrameNanos { }
        bringIntoView.bringIntoView()
    }
    AppTile(
        app = app,
        onClick = onClick,
        onLongClick = onLongClick,
        moving = moving,
        modifier = modifier
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoView)
            .moveModeKeys(moving, verticalStep, onMove, onStopMoving),
    )
}

/**
 * While a tile is being moved: Left/Right move it one place, Up/Down move it [verticalStep]
 * places (or do nothing if null), and OK/Back finish.
 */
private fun Modifier.moveModeKeys(
    active: Boolean,
    verticalStep: Int?,
    onMove: (Int) -> Unit,
    onDone: () -> Unit,
): Modifier = if (!active) this else onPreviewKeyEvent { event ->
    val isDown = event.type == KeyEventType.KeyDown
    fun move(delta: Int?): Boolean {
        if (isDown && delta != null) onMove(delta)
        return true
    }
    when (event.key) {
        Key.DirectionLeft -> move(-1)
        Key.DirectionRight -> move(1)
        Key.DirectionUp -> move(verticalStep?.let { -it })
        Key.DirectionDown -> move(verticalStep)
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> {
            if (event.type == KeyEventType.KeyUp) onDone()
            true
        }
        else -> false
    }
}
