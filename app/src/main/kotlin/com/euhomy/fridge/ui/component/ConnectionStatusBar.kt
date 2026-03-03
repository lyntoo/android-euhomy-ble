package com.euhomy.fridge.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.euhomy.fridge.model.ConnectionState

@Composable
fun ConnectionStatusBar(state: ConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        is ConnectionState.Connected    -> "Connected" to Color(0xFF2E7D32)
        is ConnectionState.Connecting   -> "Connecting…" to Color(0xFFF57C00)
        is ConnectionState.Disconnected -> "Disconnected" to Color(0xFF616161)
        is ConnectionState.Error        -> "Error: ${state.message}" to Color(0xFFD32F2F)
    }

    val bg by animateColorAsState(targetValue = color, label = "statusBg")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text      = label,
            color     = Color.White,
            style     = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
