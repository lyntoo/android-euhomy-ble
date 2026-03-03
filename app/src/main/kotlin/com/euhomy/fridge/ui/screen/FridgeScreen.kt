package com.euhomy.fridge.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.euhomy.fridge.model.ConnectionState
import com.euhomy.fridge.ui.component.ConnectionStatusBar
import com.euhomy.fridge.ui.component.ModeToggle
import com.euhomy.fridge.ui.component.TemperatureSlider
import com.euhomy.fridge.viewmodel.FridgeViewModel

// Shared vertical padding inside each card — keeps all cards uniform.
private val CARD_V = 10.dp
private val CARD_H = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeScreen(
    onOpenSettings: () -> Unit = {},
    vm: FridgeViewModel = viewModel(),
) {
    val connState  by vm.connectionState.collectAsState()
    val state      by vm.fridgeState.collectAsState()
    val isConnected = connState == ConnectionState.Connected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Euhomy Fridge") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ConnectionStatusBar(connState)

            if (!vm.hasCredentials) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No device configured. Please go to settings.")
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CARD_H, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── Current temperature (compact single-line) ─────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CARD_H, vertical = CARD_V),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Current temp", style = MaterialTheme.typography.titleMedium)
                        val isFahrenheit = state.isFahrenheit == true
                        val displayTemp = if (isFahrenheit) {
                            state.currentTempC?.let { c -> "%.0f°F".format(c * 9f / 5 + 32) } ?: "—"
                        } else {
                            state.currentTempC?.let { "${it}°C" } ?: "—"
                        }
                        Text(
                            displayTemp,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // ── Target temperature ────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = CARD_H, vertical = CARD_V)) {
                        TemperatureSlider(
                            setpoint  = state.setpointC ?: 0,
                            onChanged = { vm.setTemperature(it) },
                            enabled   = isConnected,
                        )
                    }
                }

                // ── Power ─────────────────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CARD_H, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Power  ${if (state.isOn == true) "On" else "Off"}",
                            style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked         = state.isOn == true,
                            onCheckedChange = { vm.setPower(it) },
                            enabled         = isConnected,
                        )
                    }
                }

                // ── Mode ──────────────────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CARD_H, vertical = CARD_V),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Mode", style = MaterialTheme.typography.titleMedium)
                        ModeToggle(
                            current  = state.mode,
                            onSelect = { vm.setMode(it) },
                            enabled  = isConnected,
                        )
                    }
                }

                // ── Temperature unit ──────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CARD_H, vertical = CARD_V),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Unit", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.isFahrenheit != true,
                                onClick  = { vm.setTempUnit(false) },
                                label    = { Text("°C") },
                                enabled  = isConnected,
                            )
                            FilterChip(
                                selected = state.isFahrenheit == true,
                                onClick  = { vm.setTempUnit(true) },
                                label    = { Text("°F") },
                                enabled  = isConnected,
                            )
                        }
                    }
                }

                // ── Panel lock ────────────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CARD_H, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (state.isLocked == true) Icons.Default.Lock
                                else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Lock  ${if (state.isLocked == true) "On" else "Off"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Switch(
                            checked         = state.isLocked == true,
                            onCheckedChange = { vm.setLock(it) },
                            enabled         = isConnected,
                        )
                    }
                }

                // ── Battery ───────────────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = CARD_H, vertical = CARD_V)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Battery", style = MaterialTheme.typography.titleMedium)
                            val voltsText = state.batteryMv?.let { "%.2f V".format(it / 1000f) } ?: "—"
                            Text(voltsText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Protection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = state.batteryProt == "l",
                                    onClick  = { vm.setBatteryProt("l") },
                                    label    = { Text("L") },
                                    enabled  = isConnected,
                                )
                                FilterChip(
                                    selected = state.batteryProt == "m",
                                    onClick  = { vm.setBatteryProt("m") },
                                    label    = { Text("M") },
                                    enabled  = isConnected,
                                )
                                FilterChip(
                                    selected = state.batteryProt == "h",
                                    onClick  = { vm.setBatteryProt("h") },
                                    label    = { Text("H") },
                                    enabled  = isConnected,
                                )
                            }
                        }
                    }
                }

                // ── Reconnect button ──────────────────────────────────────────
                if (connState is ConnectionState.Disconnected ||
                    connState is ConnectionState.Error) {
                    TextButton(
                        onClick  = { vm.reconnect() },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Reconnect")
                    }
                }
            }
        }
    }
}
