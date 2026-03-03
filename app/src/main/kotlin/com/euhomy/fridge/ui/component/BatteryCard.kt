package com.euhomy.fridge.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BatteryCard(
    batteryMv:   Int?,
    batteryProt: String?,
    modifier:    Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Battery", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            if (batteryMv != null) {
                val volts = batteryMv / 1000f
                Row {
                    Text("Voltage: ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "%.2f V".format(volts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text("Voltage: —", style = MaterialTheme.typography.bodyLarge)
            }

            if (batteryProt != null) {
                val label = when (batteryProt) {
                    "h"  -> "High (recommended)"
                    "m"  -> "Medium"
                    "l"  -> "Low"
                    else -> batteryProt
                }
                Row {
                    Text("Protection: ", style = MaterialTheme.typography.bodyLarge)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
