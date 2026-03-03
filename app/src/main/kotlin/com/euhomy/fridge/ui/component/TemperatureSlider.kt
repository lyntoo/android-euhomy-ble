package com.euhomy.fridge.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.euhomy.fridge.ble.TempLimits

@Composable
fun TemperatureSlider(
    setpoint:   Int,
    onChanged:  (Int) -> Unit,
    enabled:    Boolean = true,
    modifier:   Modifier = Modifier,
) {
    // Local visual state during drag — BLE command sent only on finger release.
    var dragValue by remember(setpoint) { mutableFloatStateOf(setpoint.toFloat()) }
    val displayTemp = dragValue.toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text  = "Target temperature",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick  = { if (setpoint > TempLimits.MIN_C) onChanged(setpoint - 1) },
                enabled  = enabled && setpoint > TempLimits.MIN_C,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease temperature")
            }

            Slider(
                value                 = dragValue,
                onValueChange         = { dragValue = it },          // visual only
                onValueChangeFinished = { onChanged(dragValue.toInt()) }, // BLE on release
                valueRange            = TempLimits.MIN_C.toFloat()..TempLimits.MAX_C.toFloat(),
                steps                 = TempLimits.MAX_C - TempLimits.MIN_C - 1,
                enabled               = enabled,
                modifier              = Modifier.weight(1f),
            )

            IconButton(
                onClick  = { if (setpoint < TempLimits.MAX_C) onChanged(setpoint + 1) },
                enabled  = enabled && setpoint < TempLimits.MAX_C,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase temperature")
            }
        }
        Text(
            text      = "${displayTemp}°C",
            style     = MaterialTheme.typography.headlineMedium,
            color     = MaterialTheme.colorScheme.primary,
            modifier  = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 4.dp),
        )
    }
}
