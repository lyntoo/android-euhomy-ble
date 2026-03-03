package com.euhomy.fridge.model

/**
 * A BLE device discovered during a scan.
 * Wraps the Android BluetoothDevice address + display info.
 */
data class BleDevice(
    val address:  String,
    val name:     String?,
    val rssi:     Int,
    val isTuya:   Boolean,   // manufacturer_id == 0x07D0
)
