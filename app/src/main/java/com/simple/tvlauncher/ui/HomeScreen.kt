@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.simple.tvlauncher.LauncherState
import com.simple.tvlauncher.data.AppInfo
import androidx.tv.material3.ExperimentalTvMaterial3Api

private const val COLUMNS = 5
private val SidePadding = 48.dp
private val TileSpacing = 20.dp
private val FavoriteWidth = 200.dp

@Composable
fun HomeScreen(
    state: LauncherState,
    focusResetKey: Int,
    movingPackage: String?,
    onLaunch: (AppInfo) -> Unit,
    onOptions: (AppInfo) -> Unit,
    onMove: (delta: Int) -> Unit,
    onStopMoving: () -> Unit,
    onOpenInputs: () -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val firstTile = remember { FocusRequester() }

    var hasFocus by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SidePadding, end = SidePadding, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LauncherButton("Inputs", onOpenInputs)
            LauncherButton("TV settings", onOpenTvSettings)
            LauncherButton("Launcher settings", onOpenLauncherSettings)
            Spacer(Modifier.weight(1f))
            Clock(state.settings.use24HourClock)
        }

        if (state.loaded) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.fillMaxSize().padding(horizontal = SidePadding),
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                if (state.favorites.isNotEmpty()) {
                    item(key = "favorites-title", span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle("Favorites")
                    }
                    item(key = "favorites", span = { GridItemSpan(maxLineSpan) }) {
                        FavoritesRow(
                            favorites = state.favorites,
                            movingPackage = movingPackage,
                            firstTile = firstTile,
                            onLaunch = onLaunch,
                            onOptions = onOptions,
                            onMove = onMove,
                            onStopMoving = onStopMoving,
                        )
                    }
                }
                item(key = "apps-title", span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("All apps  ·  hold OK on an app for options")
                }
                itemsIndexed(state.apps, key = { _, app -> "app:${app.packageName}" }) { index, app ->
                    val isFirst = index == 0 && state.favorites.isEmpty()
                    AppTile(
                        app = app,
                        onClick = { onLaunch(app) },
                        onLongClick = { onOptions(app) },
                        modifier = if (isFirst) Modifier.focusRequester(firstTile) else Modifier,
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
    val rowState = rememberLazyListState()
    val movingIndex = favorites.indexOfFirst { it.packageName == movingPackage }

    // Keep the tile being moved on screen.
    LaunchedEffect(movingIndex) {
        if (movingIndex < 0) return@LaunchedEffect
        val visible = rowState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: return@LaunchedEffect
        val last = visible.last().index
        when {
            movingIndex <= first -> rowState.animateScrollToItem(movingIndex)
            movingIndex >= last -> rowState.animateScrollToItem((movingIndex - (last - first) + 1).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = rowState,
        modifier = Modifier.bleed(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        itemsIndexed(favorites, key = { _, app -> "fav:${app.packageName}" }) { index, app ->
            val moving = app.packageName == movingPackage
            val requester = remember { FocusRequester() }
            LaunchedEffect(moving) {
                if (moving) requester.requestFocusSafely()
            }
            AppTile(
                app = app,
                onClick = { onLaunch(app) },
                onLongClick = { onOptions(app) },
                moving = moving,
                modifier = Modifier
                    .width(FavoriteWidth)
                    .focusRequester(requester)
                    .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                    .moveModeKeys(moving, onMove, onStopMoving),
            )
        }
    }
}

/** While a favorite is being moved, Left/Right move it and OK/Back finish. */
private fun Modifier.moveModeKeys(active: Boolean, onMove: (Int) -> Unit, onDone: () -> Unit): Modifier =
    if (!active) this else onPreviewKeyEvent { event ->
        val isDown = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.DirectionLeft -> { if (isDown) onMove(-1); true }
            Key.DirectionRight -> { if (isDown) onMove(1); true }
            Key.DirectionUp, Key.DirectionDown -> true
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> {
                if (event.type == KeyEventType.KeyUp) onDone()
                true
            }
            else -> false
        }
    }
