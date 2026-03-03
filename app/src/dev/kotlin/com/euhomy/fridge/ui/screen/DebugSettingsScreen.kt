package com.euhomy.fridge.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.euhomy.fridge.data.CredentialsStore
import com.euhomy.fridge.data.DeviceCredentials
import com.euhomy.fridge.data.PreferencesRepository
import com.euhomy.fridge.viewmodel.FridgeViewModel

/**
 * Debug-flavor settings screen — always accessible (not gated behind first-launch).
 *
 * Provides:
 * - Display of current stored credentials
 * - One-tap pre-fill with known test device (Euhomy CFC-25)
 * - Clear credentials
 * - Toggle display unit
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    onNavigateToSetup: () -> Unit,
    onBack:            () -> Unit,
    vm: FridgeViewModel = viewModel(),
) {
    val context   = LocalContext.current
    val store     = remember { CredentialsStore(context) }
    val prefs     = remember { PreferencesRepository(context) }
    var creds     by remember { mutableStateOf(store.load()) }
    var showF     by remember { mutableStateOf(prefs.showFahrenheit) }
    val rawDps    by vm.fridgeState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("[DEBUG] Settings") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Stored credentials ───────────────────────────────────────────
            Text("Stored credentials", style = MaterialTheme.typography.titleLarge)
            if (creds != null) {
                CredentialRow("MAC",        creds!!.macAddress)
                CredentialRow("Local Key",  creds!!.localKey)
                CredentialRow("Device ID",  creds!!.deviceId)
                CredentialRow("UUID",       creds!!.uuid)
            } else {
                Text("None saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // ── Actions ──────────────────────────────────────────────────────
            Button(
                onClick  = { onNavigateToSetup() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Edit credentials") }

            // Pre-fill with placeholder values (fill in your own device credentials)
            OutlinedButton(
                onClick  = {
                    val testCreds = DeviceCredentials(
                        macAddress = "XX:XX:XX:XX:XX:XX",
                        localKey   = "",
                        deviceId   = "",
                        uuid       = "",
                        deviceName = "Euhomy CFC-25 [test]",
                    )
                    store.save(testCreds)
                    creds = store.load()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pre-fill placeholder") }

            Button(
                onClick  = { store.clear(); creds = null },
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear credentials") }

            Spacer(Modifier.height(8.dp))

            // ── Raw DPs received from device ─────────────────────────────────
            Text("Raw DPs (live)", style = MaterialTheme.typography.titleLarge)
            if (rawDps.rawDps.isEmpty()) {
                Text("No DPs received yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rawDps.rawDps.entries.sortedBy { it.key }.forEach { (id, value) ->
                    CredentialRow("DP $id", value)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Display unit toggle ──────────────────────────────────────────
            Text("Display unit", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Show Fahrenheit", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked         = showF,
                    onCheckedChange = {
                        showF = it
                        prefs.showFahrenheit = it
                    },
                )
            }
        }
    }
}

@Composable
private fun CredentialRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            value,
            style    = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(0.65f),
        )
    }
}
