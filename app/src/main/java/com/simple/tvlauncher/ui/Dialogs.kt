@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import com.simple.tvlauncher.data.AppInfo
import com.simple.tvlauncher.data.Snapshot
import com.simple.tvlauncher.data.TvInput
import androidx.tv.material3.ExperimentalTvMaterial3Api

@Composable
fun AppOptionsDialog(
    app: AppInfo,
    isFavorite: Boolean,
    /** Null hides the Move option. */
    moveLabel: String?,
    /** Shown on the hero select screen, e.g. "Role: Tank". Null hides it. */
    roleLabel: String?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartMoving: () -> Unit,
    onChangeRole: () -> Unit,
    onHide: () -> Unit,
    onAppInfo: () -> Unit,
) {
    LauncherDialog(title = app.label, onDismiss = onDismiss) { firstItem ->
        DialogButton("Open", onOpen, Modifier.focusRequester(firstItem))
        DialogButton(if (isFavorite) "Remove from favorites" else "Add to favorites", onToggleFavorite)
        if (moveLabel != null) DialogButton(moveLabel, onStartMoving)
        if (roleLabel != null) DialogButton(roleLabel, onChangeRole)
        DialogButton("Hide app", onHide)
        DialogButton("App info", onAppInfo)
    }
}

@Composable
fun RestoreDialog(snapshot: Snapshot, onRestore: () -> Unit, onDismiss: () -> Unit) {
    LauncherDialog(title = "Restore this setup?", onDismiss = onDismiss) { firstItem ->
        Text(
            "Favorites, app order, hidden apps, roles and the look go back to how they were (${snapshot.label}). " +
                "Your current setup is kept in the list first, so you can undo this too.",
            color = Color.White.copy(alpha = 0.7f),
        )
        DialogButton("Cancel", onDismiss, Modifier.focusRequester(firstItem))
        DialogButton("Restore", onRestore)
    }
}

@Composable
fun ResetOrderDialog(onReset: () -> Unit, onDismiss: () -> Unit) {
    LauncherDialog(title = "Reset to A–Z order?", onDismiss = onDismiss) { firstItem ->
        Text(
            "Every app in the All apps grid goes back to alphabetical order, and the places you moved them to are lost. " +
                "This can't be undone.",
            color = Color.White.copy(alpha = 0.7f),
        )
        DialogButton("Cancel", onDismiss, Modifier.focusRequester(firstItem))
        DialogButton("Reset", onReset)
    }
}

@Composable
fun InputsDialog(
    inputs: List<TvInput>,
    onSelect: (TvInput) -> Unit,
    onDismiss: () -> Unit,
) {
    LauncherDialog(title = "Inputs", onDismiss = onDismiss) { firstItem ->
        if (inputs.isEmpty()) {
            Text("This TV didn't report any inputs.", color = Color.White.copy(alpha = 0.7f))
            DialogButton("Close", onDismiss, Modifier.focusRequester(firstItem))
        } else {
            inputs.forEachIndexed { index, input ->
                DialogButton(
                    text = input.label,
                    onClick = { onSelect(input) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                )
            }
        }
    }
}
