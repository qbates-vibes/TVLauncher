@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.simple.tvlauncher.data.AppInfo

val TileColor = Color(0xFF2A2F36)
val TileFocusedColor = Color(0xFF3A414B)
val MovingColor = Color(0xFFFFC107)
val DialogColor = Color(0xFF1F2933)

private val TileShape = RoundedCornerShape(10.dp)

/** An app tile: the TV banner if the app has one, otherwise its icon and name. */
@Composable
fun AppTile(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.aspectRatio(16f / 9f),
        shape = CardDefaults.shape(TileShape),
        colors = CardDefaults.colors(
            containerColor = TileColor,
            contentColor = Color.White,
            focusedContainerColor = TileFocusedColor,
            focusedContentColor = Color.White,
        ),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, if (moving) MovingColor else Color.White), shape = TileShape),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    app.icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(44.dp)) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (moving) {
                Text(
                    text = "◀  ▶",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .background(MovingColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun launcherButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.10f),
    contentColor = Color.White,
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black,
)

@Composable
fun LauncherButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier, colors = launcherButtonColors()) {
        Text(text)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.75f),
        modifier = modifier.padding(top = 8.dp),
    )
}

/**
 * A centered dialog for the remote. [content] receives a FocusRequester to attach to the
 * first button so it gets focus when the dialog opens.
 */
@Composable
fun LauncherDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(firstItem: FocusRequester) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val firstItem = remember { FocusRequester() }
        // Dialogs often open from a long press, so the OK button is still held down. Ignore key
        // events until a fresh press starts, or releasing OK would click the first button.
        var armed by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (!armed && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    armed = true
                }
                !armed
            },
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = DialogColor, contentColor = Color.White),
        ) {
            Column(
                modifier = Modifier.width(440.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content(firstItem)
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            firstItem.requestFocusSafely()
        }
    }
}

@Composable
fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LauncherButton(text, onClick, modifier.fillMaxWidth())
}

/** requestFocus() throws if the target isn't on screen yet; ignore that case. */
fun FocusRequester.requestFocusSafely() {
    try {
        requestFocus()
    } catch (e: IllegalStateException) {
        // Not attached yet; nothing to focus.
    }
}

/** Lets a child draw [horizontal] past its parent's edges on both sides, so focused tiles aren't clipped. */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
