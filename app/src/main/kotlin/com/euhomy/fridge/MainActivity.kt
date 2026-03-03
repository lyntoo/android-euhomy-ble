package com.euhomy.fridge

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.euhomy.fridge.ui.navigation.EuhomyNavGraph
import com.euhomy.fridge.ui.theme.EuhomyTheme

class MainActivity : ComponentActivity() {

    // State to show a rationale dialog if needed
    private var showPermissionRationale by mutableStateOf(false)

    // Launcher for the system permission dialog
    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (!allGranted) showPermissionRationale = true
    }

    // Launcher to enable Bluetooth
    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user either enabled or refused — the scanner handles the disabled case */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBlePermissions()

        setContent {
            EuhomyTheme {
                if (showPermissionRationale) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationale = false },
                        title   = { Text("Bluetooth permission required") },
                        text    = { Text(
                            "This app needs Bluetooth permissions to communicate " +
                            "with your Euhomy fridge. Please grant them in Settings."
                        )},
                        confirmButton = {
                            TextButton(onClick = {
                                showPermissionRationale = false
                                requestBlePermissions()
                            }) { Text("Retry") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionRationale = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                EuhomyNavGraph()
            }
        }
    }

    private fun requestBlePermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        blePermissionLauncher.launch(perms.toTypedArray())

        // Prompt to enable Bluetooth if it's off
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
            ?.adapter
        if (adapter?.isEnabled == false) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}
