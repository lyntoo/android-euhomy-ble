package com.euhomy.fridge.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.euhomy.fridge.viewmodel.SetupResult
import com.euhomy.fridge.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    prefillMac:  String?    = null,
    onComplete:  () -> Unit = {},
    vm: SetupViewModel = viewModel(),
) {
    val result by vm.result.collectAsState()

    val existing = vm.existing
    var mac        by remember { mutableStateOf(prefillMac ?: existing?.macAddress ?: "") }
    var localKey   by remember { mutableStateOf(existing?.localKey   ?: "") }
    var deviceId   by remember { mutableStateOf(existing?.deviceId   ?: "") }
    var uuid       by remember { mutableStateOf(existing?.uuid       ?: "") }
    var deviceName by remember { mutableStateOf(existing?.deviceName ?: "Euhomy Fridge") }

    LaunchedEffect(result) {
        if (result is SetupResult.Success) onComplete()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Device setup") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Enter the Tuya credentials for your fridge. " +
                "These can be obtained from the Tuya cloud or local key extractor.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            SetupField(
                value        = mac,
                onValueChange= { mac = it.uppercase() },
                label        = "BLE MAC Address",
                hint         = "AA:BB:CC:DD:EE:FF",
            )
            SetupField(
                value        = localKey,
                onValueChange= { localKey = it },
                label        = "Local Key",
                hint         = "16-char key from Tuya cloud",
            )
            SetupField(
                value        = deviceId,
                onValueChange= { deviceId = it },
                label        = "Device ID",
                hint         = "Tuya device_id (20 chars)",
            )
            SetupField(
                value        = uuid,
                onValueChange= { uuid = it },
                label        = "UUID",
                hint         = "Tuya uuid (may equal device ID)",
            )
            SetupField(
                value        = deviceName,
                onValueChange= { deviceName = it },
                label        = "Name (optional)",
                hint         = "Euhomy Fridge",
            )

            if (result is SetupResult.Error) {
                Text(
                    (result as SetupResult.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = { vm.save(mac, localKey, deviceId, uuid, deviceName) },
                enabled  = result !is SetupResult.Saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (result is SetupResult.Saving) "Saving…" else "Save & connect")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetupField(
    value:         String,
    onValueChange: (String) -> Unit,
    label:         String,
    hint:          String,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        placeholder   = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect    = false,
        ),
    )
}
