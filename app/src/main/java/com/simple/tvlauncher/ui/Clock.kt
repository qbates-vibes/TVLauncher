@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.simple.tvlauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.tv.material3.ExperimentalTvMaterial3Api

@Composable
fun Clock(use24Hour: Boolean, modifier: Modifier = Modifier) {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Wake up right at the start of the next minute.
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a")),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}
