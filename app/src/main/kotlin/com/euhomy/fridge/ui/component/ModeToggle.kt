package com.euhomy.fridge.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.euhomy.fridge.ble.Mode

@Composable
fun ModeToggle(
    current:  String?,
    onSelect: (String) -> Unit,
    enabled:  Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        FilterChip(
            selected = current == Mode.MAX,
            onClick  = { onSelect(Mode.MAX) },
            label    = { Text("MAX", style = MaterialTheme.typography.labelSmall) },
            enabled  = enabled,
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = current == Mode.ECO,
            onClick  = { onSelect(Mode.ECO) },
            label    = { Text("ECO", style = MaterialTheme.typography.labelSmall) },
            enabled  = enabled,
        )
    }
}
