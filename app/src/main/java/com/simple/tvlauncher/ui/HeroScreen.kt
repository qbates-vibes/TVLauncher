@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.simple.tvlauncher.LauncherState
import com.simple.tvlauncher.data.AppInfo
import com.simple.tvlauncher.data.Role

private val Accent = Color(0xFFF99E1A)
private val PortraitColor = Color(0xFF1C3354)
private val PortraitFocusedColor = Color(0xFF2B4D7C)

/** Bold italic condensed system font: the closest built-in match to a hero-select title. */
private val HeroFont = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC))

private fun heroText(size: Int, color: Color = Color.White) = TextStyle(
    fontFamily = HeroFont,
    fontSize = size.sp,
    color = color,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), blurRadius = 8f),
)

/** A home screen styled like a game's hero select: the focused app is the "hero" in the spotlight. */
@Composable
fun HeroScreen(
    state: LauncherState,
    focusResetKey: Int,
    hasWallpaper: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onOptions: (AppInfo) -> Unit,
    onOpenInputs: () -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
) {
    val rosterState = rememberLazyGridState()
    val firstPortrait = remember { FocusRequester() }
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    var hasFocus by remember { mutableStateOf(false) }
    val groups = state.roles.filterValues { it.isNotEmpty() }
    val firstApp = groups.values.firstOrNull()?.firstOrNull()
    val hero = state.apps.firstOrNull { it.packageName == focusedPackage } ?: firstApp

    // Same focus rules as the classic screen: first portrait on load and on Home, and if the focused one disappears.
    LaunchedEffect(state.loaded, focusResetKey) {
        if (!state.loaded) return@LaunchedEffect
        rosterState.scrollToItem(0)
        withFrameNanos { }
        firstPortrait.requestFocusSafely()
    }
    LaunchedEffect(state, hasFocus) {
        if (!state.loaded || hasFocus) return@LaunchedEffect
        withFrameNanos { }
        if (!hasFocus) {
            rosterState.scrollToItem(0)
            withFrameNanos { }
            firstPortrait.requestFocusSafely()
        }
    }

    Box(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        HeroBackdrop(Modifier.fillMaxSize(), alpha = if (hasWallpaper) 0.55f else 1f)

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Text("CHOOSE YOUR APP", style = heroText(30))
                    Text("${state.apps.size} AVAILABLE", style = heroText(14, Color.White.copy(alpha = 0.6f)))
                }
                Spacer(Modifier.weight(1f))
                SkewedButton("INPUTS", onOpenInputs)
                SkewedButton("TV SETTINGS", onOpenTvSettings)
                SkewedButton("LAUNCHER", onOpenLauncherSettings)
                Spacer(Modifier.width(12.dp))
                Clock(state.settings.use24HourClock)
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp)) {
                AnimatedContent(
                    targetState = hero,
                    contentKey = { it?.packageName },
                    transitionSpec = { (slideInHorizontally { it / 10 } + fadeIn()) togetherWith fadeOut() },
                    label = "hero",
                ) { app ->
                    if (app != null) Spotlight(app, state.roleOf(app))
                }
            }

            LazyHorizontalGrid(
                state = rosterState,
                rows = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { (role, apps) ->
                    item(key = "role:${role.name}", span = { GridItemSpan(maxLineSpan) }) {
                        RoleHeader(role, Modifier.padding(start = if (role == groups.keys.first()) 0.dp else 14.dp))
                    }
                    items(apps, key = { "hero:${it.packageName}" }) { app ->
                        Portrait(
                            app = app,
                            onFocused = { focusedPackage = app.packageName },
                            onClick = { onLaunch(app) },
                            onLongClick = { onOptions(app) },
                            modifier = if (app == firstApp) Modifier.focusRequester(firstPortrait) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** The big "selected hero" area: name, role, controls hint and a large slanted banner. */
@Composable
private fun Spotlight(app: AppInfo, role: Role) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleIcon(role, Modifier.size(26.dp), Accent)
                Text(role.label.uppercase(), style = heroText(22, Accent))
            }
            Text(
                text = app.label.uppercase(),
                style = heroText(64),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 6.dp)) {
                ControlHint("OK", "PLAY")
                ControlHint("HOLD OK", "OPTIONS")
            }
        }
        Spacer(Modifier.width(24.dp))
        val shape = SkewedShape(36.dp)
        Box(
            modifier = Modifier
                .width(400.dp)
                .height(225.dp)
                .background(Brush.linearGradient(listOf(PortraitFocusedColor, PortraitColor)), shape)
                .border(3.dp, Accent, shape),
            contentAlignment = Alignment.Center,
        ) {
            val banner = app.banner
            val icon = app.icon
            when {
                banner != null -> Image(
                    bitmap = banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(shape),
                )
                icon != null -> Image(icon, contentDescription = app.label, modifier = Modifier.size(120.dp))
            }
        }
    }
}

@Composable
private fun ControlHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = key,
            style = heroText(14, Color.Black),
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Text(action, style = heroText(16, Color.White.copy(alpha = 0.85f)))
    }
}

/** A roster tile: slanted, showing the app's icon. */
@Composable
private fun Portrait(
    app: AppInfo,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = SkewedShape(12.dp)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .onFocusChanged { if (it.hasFocus) onFocused() },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = PortraitColor,
            contentColor = Color.White,
            focusedContainerColor = PortraitFocusedColor,
            focusedContentColor = Color.White,
        ),
        scale = CardDefaults.scale(focusedScale = 1.15f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Accent), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val icon = app.icon
            val banner = app.banner
            when {
                icon != null -> Image(icon, contentDescription = app.label, modifier = Modifier.size(40.dp))
                banner != null -> Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** A column at the start of each role group in the roster. */
@Composable
private fun RoleHeader(role: Role, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(64.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RoleIcon(role, Modifier.size(30.dp), Color.White)
        Spacer(Modifier.height(6.dp))
        Text(role.label.uppercase(), style = heroText(14, Color.White.copy(alpha = 0.8f)))
    }
}

/** Simple drawn role icons: a shield, three rounds, and a plus. */
@Composable
private fun RoleIcon(role: Role, modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        when (role) {
            Role.Tank -> drawPath(shieldPath(size), color)
            Role.Damage -> drawRounds(color)
            Role.Support -> {
                val bar = size.width * 0.3f
                drawRect(color, Offset((size.width - bar) / 2, 0f), Size(bar, size.height))
                drawRect(color, Offset(0f, (size.height - bar) / 2), Size(size.width, bar))
            }
        }
    }
}

private fun shieldPath(size: Size) = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.15f)
    lineTo(w * 0.9f, h * 0.65f)
    lineTo(w * 0.5f, h)
    lineTo(w * 0.1f, h * 0.65f)
    lineTo(0f, h * 0.15f)
    close()
}

private fun DrawScope.drawRounds(color: Color) {
    val bulletWidth = size.width * 0.22f
    val gap = (size.width - bulletWidth * 3) / 2
    repeat(3) { i ->
        val left = i * (bulletWidth + gap)
        val path = Path().apply {
            moveTo(left, size.height)
            lineTo(left, size.height * 0.3f)
            lineTo(left + bulletWidth / 2, 0f)
            lineTo(left + bulletWidth, size.height * 0.3f)
            lineTo(left + bulletWidth, size.height)
            close()
        }
        drawPath(path, color)
    }
}

/** Deep blue gradient with faint diagonal stripes. Drawn semi-transparent over a wallpaper picture. */
@Composable
private fun HeroBackdrop(modifier: Modifier, alpha: Float) {
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF123764), Color(0xFF0A1A33), Color(0xFF050B17))),
            alpha = alpha,
        )
        val stripe = 90.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = Color.White.copy(alpha = 0.03f),
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = stripe / 3,
            )
            x += stripe
        }
    }
}

@Composable
private fun SkewedButton(text: String, onClick: () -> Unit) {
    val shape = SkewedShape(8.dp)
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Accent,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(text, style = TextStyle(fontFamily = HeroFont, fontSize = 15.sp))
    }
}

/** A parallelogram leaning right, like the tiles on a hero select screen. */
class SkewedShape(private val skew: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { skew.toPx() }
        return Outline.Generic(
            Path().apply {
                moveTo(s, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - s, size.height)
                lineTo(0f, size.height)
                close()
            }
        )
    }
}
