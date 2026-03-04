package com.euhomy.fridge

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.euhomy.fridge.ui.navigation.EuhomyNavGraph
import com.euhomy.fridge.ui.theme.EuhomyTheme

class MainActivity : ComponentActivity() {

    // True only after all required BLE permissions are granted.
    // EuhomyNavGraph (and thus FridgeViewModel) must NOT be created before this —
    // connectGatt() throws SecurityException if BLUETOOTH_CONNECT is missing.
    private var permissionsGranted by mutableStateOf(false)

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            permissionsGranted = true
        }
        // If denied, the UI below shows a Retry button — do nothing else here.
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user either enabled or refused — handled by scanner */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if permissions are already granted (re-launch after first grant).
        permissionsGranted = checkBlePermissionsGranted()
        if (!permissionsGranted) requestBlePermissions()

        setContent {
            EuhomyTheme {
                if (permissionsGranted) {
                    // Permissions are in place — safe to create ViewModel and connect.
                    EuhomyNavGraph()
                } else {
                    // Waiting for user to grant permissions: show a simple prompt.
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier              = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement   = Arrangement.Center,
                            horizontalAlignment   = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Bluetooth permission required",
                                style     = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Allow the app to find nearby devices so it can " +
                                "connect to your Euhomy fridge.",
                                style     = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { requestBlePermissions() }) {
                                Text("Grant permission")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkBlePermissionsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)    == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
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

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
        if (adapter?.isEnabled == false) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}
