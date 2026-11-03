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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import com.simple.tvlauncher.LauncherState
import com.simple.tvlauncher.data.AppInfo
import com.simple.tvlauncher.data.Role
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val Accent = Color(0xFFF99E1A)
private val PortraitColor = Color(0xFF1C3354)
private val PortraitFocusedColor = Color(0xFF2B4D7C)
private val FocusGlow = Glow(elevationColor = Accent.copy(alpha = 0.8f), elevation = 12.dp)

/** Bold italic condensed system font: the closest built-in match to a hero-select title. */
private val HeroFont = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC))

private fun heroText(size: Int, color: Color = Color.White) = TextStyle(
    fontFamily = HeroFont,
    fontSize = size.sp,
    color = color,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), blurRadius = 8f),
)

private val Role.tagline: String
    get() = when (this) {
        Role.Tank -> "Front-line streaming"
        Role.Damage -> "Games and music"
        Role.Support -> "Tools and utilities"
    }

/**
 * A home screen styled like a game's hero select: the focused app is the "hero" in the spotlight,
 * and every app sits in a role-grouped roster along the bottom.
 */
@Composable
fun HeroScreen(
    state: LauncherState,
    focusResetKey: Int,
    hasWallpaper: Boolean,
    logo: ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onOptions: (AppInfo) -> Unit,
    onOpenInputs: () -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
) {
    val rosterState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Ends of the roster, top and bottom row, for wrapping around between Tank and Support.
    val firstTop = remember { FocusRequester() }
    val firstBottom = remember { FocusRequester() }
    val lastTop = remember { FocusRequester() }
    val lastBottom = remember { FocusRequester() }
    val firstPortrait = firstTop
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    var hasFocus by remember { mutableStateOf(false) }
    val groups = state.roles.filterValues { it.isNotEmpty() }
    val firstApp = groups.values.firstOrNull()?.firstOrNull()
    val hero = state.apps.firstOrNull { it.packageName == focusedPackage } ?: firstApp
    val firstGroup = groups.values.firstOrNull().orEmpty()
    val lastGroup = groups.values.lastOrNull().orEmpty()

    /** Scrolls the roster to [groupIndex] and focuses [target], which may have been off screen. */
    fun wrapTo(groupIndex: Int, target: FocusRequester) {
        scope.launch {
            rosterState.scrollToItem(groupIndex)
            withFrameNanos { }
            target.requestFocusSafely()
        }
    }

    /** Extra modifiers for portraits at either end of the roster, so Left/Right wrap around. */
    fun wrapModifier(groupIndex: Int, index: Int): Modifier {
        val column = index / 2
        val isTop = index % 2 == 0
        var modifier: Modifier = Modifier
        if (groupIndex == 0 && column == 0) {
            val target = if (!isTop && lastGroup.size % 2 == 0) lastBottom else lastTop
            modifier = modifier
                .focusRequester(if (isTop) firstTop else firstBottom)
                .onArrowKey(Key.DirectionLeft) { wrapTo(groups.size - 1, target) }
        }
        if (groupIndex == groups.size - 1 && column == (lastGroup.size - 1) / 2) {
            val target = if (!isTop && firstGroup.size >= 2) firstBottom else firstTop
            modifier = modifier
                .focusRequester(if (isTop) lastTop else lastBottom)
                .onArrowKey(Key.DirectionRight) { wrapTo(0, target) }
        }
        return modifier
    }

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
            Box(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 20.dp)) {
                TitleBlock(logo, Modifier.align(Alignment.CenterStart))
                MatchClock(state.settings.use24HourClock, Modifier.align(Alignment.TopCenter))
                Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkewedButton("INPUTS", onOpenInputs)
                    SkewedButton("SETTINGS", onOpenTvSettings)
                    SkewedButton("LAUNCHER", onOpenLauncherSettings)
                }
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

            LazyRow(
                state = rosterState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp),
                // Centered when the whole roster fits; scrolls when it doesn't.
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            ) {
                groups.entries.forEachIndexed { groupIndex, (role, apps) ->
                    item(key = "group:${role.name}") {
                        RoleGroup(
                            role = role,
                            apps = apps,
                            portraitModifier = { index -> wrapModifier(groupIndex, index) },
                            onFocused = { focusedPackage = it.packageName },
                            onLaunch = onLaunch,
                            onOptions = onOptions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(logo: ImageBitmap?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (logo != null) {
            Image(logo, contentDescription = null, modifier = Modifier.heightIn(max = 52.dp).widthIn(max = 160.dp))
        }
        Text("OVERWATCH", style = heroText(32))
    }
}

/** The clock, styled like a match countdown timer. */
@Composable
private fun MatchClock(use24Hour: Boolean, modifier: Modifier = Modifier) {
    val now = currentTime()
    val time = now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm"))
    val details = now.format(DateTimeFormatter.ofPattern(if (use24Hour) "EEE MMM d" else "a  ·  EEE MMM d")).uppercase()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .background(Color.Black.copy(alpha = 0.45f), SkewedShape(10.dp))
                .padding(horizontal = 26.dp)
        ) {
            Text(time, style = heroText(36))
        }
        Box(Modifier.width(64.dp).height(3.dp).background(Accent))
        Text(details, style = heroText(12, Color.White.copy(alpha = 0.7f)), modifier = Modifier.padding(top = 2.dp))
    }
}

/** The big "selected hero" area: role, name, controls hint and a large slanted banner. */
@Composable
private fun Spotlight(app: AppInfo, role: Role) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleIcon(role, Modifier.size(24.dp), Accent)
                Text(role.label.uppercase(), style = heroText(22, Accent))
                Text("·  ${role.tagline.uppercase()}", style = heroText(16, Color.White.copy(alpha = 0.7f)))
            }
            Text(
                text = app.label.uppercase(),
                style = heroText(58),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 4.dp)) {
                ControlHint("OK", "OPEN")
                ControlHint("HOLD OK", "OPTIONS")
            }
        }
        Spacer(Modifier.width(24.dp))
        val shape = SkewedShape(30.dp)
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(180.dp)
                .background(Brush.linearGradient(listOf(PortraitFocusedColor, PortraitColor)), shape)
                .clip(shape)
                .border(3.dp, Accent, shape),
            contentAlignment = Alignment.Center,
        ) {
            AppPicture(app, preferBanner = true, iconSize = 110.dp)
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

/** One role's section of the roster: a header with the role icon above two rows of portraits. */
@Composable
private fun RoleGroup(
    role: Role,
    apps: List<AppInfo>,
    portraitModifier: (index: Int) -> Modifier,
    onFocused: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOptions: (AppInfo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleIcon(role, Modifier.size(18.dp), Color.White)
            Text(role.label.uppercase(), style = heroText(16))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Fill column by column, top then bottom, like a hero roster.
            apps.chunked(2).forEachIndexed { columnIndex, column ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    column.forEachIndexed { row, app ->
                        Portrait(
                            app = app,
                            onFocused = { onFocused(app) },
                            onClick = { onLaunch(app) },
                            onLongClick = { onOptions(app) },
                            modifier = portraitModifier(columnIndex * 2 + row),
                        )
                    }
                }
            }
        }
    }
}

/** A roster tile: slanted, showing the app's icon, with a white outline and orange glow when selected. */
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
            .width(76.dp)
            .height(60.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() },
        shape = CardDefaults.shape(shape),
        colors = portraitColors(),
        scale = CardDefaults.scale(focusedScale = 1.15f),
        border = portraitBorder(shape),
        glow = CardDefaults.glow(focusedGlow = FocusGlow),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
        ) {
            AppPicture(app, preferBanner = false, iconSize = 38.dp)
        }
    }
}

/** Runs [action] instead of moving focus when [key] is pressed. */
private fun Modifier.onArrowKey(key: Key, action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != key) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) action()
    true
}

@Composable
private fun portraitColors() = CardDefaults.colors(
    containerColor = PortraitColor,
    contentColor = Color.White,
    focusedContainerColor = PortraitFocusedColor,
    focusedContentColor = Color.White,
)

@Composable
private fun portraitBorder(shape: Shape) = CardDefaults.border(
    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), shape = shape),
    focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
)

/** The app's banner filling the space, or its icon centered. */
@Composable
private fun AppPicture(app: AppInfo, preferBanner: Boolean, iconSize: Dp = 36.dp) {
    val banner = app.banner
    val icon = app.icon
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            preferBanner && banner != null ->
                Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            icon != null -> Image(icon, contentDescription = app.label, modifier = Modifier.size(iconSize))
            banner != null ->
                Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
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

/** Deep blue gradient with a faint hexagon pattern and darkened edges. Semi-transparent over a wallpaper. */
@Composable
private fun HeroBackdrop(modifier: Modifier, alpha: Float) {
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF123764), Color(0xFF0A1A33), Color(0xFF050B17))),
            alpha = alpha,
        )
        val radius = 34.dp.toPx()
        val stepX = sqrt(3f) * radius
        val stepY = 1.5f * radius
        val stroke = Stroke(width = 1.dp.toPx())
        var row = 0
        var y = 0f
        while (y < size.height + radius) {
            var x = if (row % 2 == 1) stepX / 2 else 0f
            while (x < size.width + stepX) {
                drawPath(hexagon(Offset(x, y), radius), Color.White.copy(alpha = 0.035f), style = stroke)
                x += stepX
            }
            y += stepY
            row++
        }
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = center,
                radius = size.maxDimension * 0.7f,
            )
        )
    }
}

/** A pointy-topped hexagon. */
private fun hexagon(center: Offset, radius: Float) = Path().apply {
    for (i in 0 until 6) {
        val angle = (PI / 180 * (60 * i - 30)).toFloat()
        val point = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

@Composable
private fun SkewedButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(SkewedShape(8.dp)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Accent,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(text, style = TextStyle(fontFamily = HeroFont, fontSize = 14.sp))
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
