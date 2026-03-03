package com.euhomy.fridge.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.euhomy.fridge.model.BleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * BLE scanner that emits a continuously updated list of discovered [BleDevice].
 *
 * Scans with NO filter (the Euhomy/Tuya TY device does NOT include the service
 * UUID in its advertisement packets — only the manufacturer ID 0x07D0 and the
 * device name "TY"). The service UUID only appears after GATT discovery.
 *
 * All visible BLE devices are shown; Tuya devices are flagged with [BleDevice.isTuya].
 */
class BleScanner(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bleScanner = bluetoothManager.adapter?.bluetoothLeScanner

    /**
     * Start a BLE scan and emit an updated [List<BleDevice>] whenever a new device
     * is found or its RSSI changes.
     *
     * Requires BLUETOOTH_SCAN permission (API 31+) or ACCESS_FINE_LOCATION (API 26-30).
     */
    fun scan(): Flow<List<BleDevice>> = callbackFlow {
        val found = mutableMapOf<String, BleDevice>()

        // Tracks insertion order so the list stays stable
        val insertionOrder = mutableListOf<String>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                val isTuya = record?.manufacturerSpecificData?.get(TUYA_MANUFACTURER_ID) != null
                val address = result.device.address
                val isNew = address !in found
                val device = BleDevice(
                    address = address,
                    name    = result.device.name ?: record?.deviceName,
                    rssi    = result.rssi,
                    isTuya  = isTuya,
                )
                found[address] = device
                // New devices are inserted sorted by RSSI; existing ones stay in place
                if (isNew) {
                    val idx = insertionOrder.indexOfFirst {
                        (found[it]?.rssi ?: Int.MIN_VALUE) < result.rssi
                    }
                    if (idx == -1) insertionOrder.add(address) else insertionOrder.add(idx, address)
                }
                trySend(insertionOrder.mapNotNull { found[it] })
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }


            override fun onScanFailed(errorCode: Int) {
                close(Exception("BLE scan failed: errorCode=$errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // No filter — the TY device does not advertise its service UUID
        bleScanner?.startScan(null, settings, callback)
            ?: close(Exception("Bluetooth LE scanner not available"))

        awaitClose {
            try { bleScanner?.stopScan(callback) } catch (_: Exception) {}
        }
    }

    /** @return true if the device's Bluetooth adapter is enabled. */
    fun isBluetoothEnabled(): Boolean =
        bluetoothManager.adapter?.isEnabled == true
}
