package com.euhomy.fridge.data

/**
 * Credentials required to communicate with the Tuya BLE device.
 *
 * All fields come from the Tuya cloud (or are extracted from the proprietary
 * app during initial setup).
 *
 * @param macAddress  BLE MAC address (e.g. "AA:BB:CC:DD:EE:FF")
 * @param localKey    Tuya local_key (16-char hex string)
 * @param deviceId    Tuya device_id  (20-char alphanumeric)
 * @param uuid        Tuya uuid       (20-char alphanumeric, may equal deviceId)
 * @param deviceName  User-visible label (optional, e.g. "Fridge")
 */
data class DeviceCredentials(
    val macAddress: String,
    val localKey:   String,
    val deviceId:   String,
    val uuid:       String,
    val deviceName: String = "Euhomy Fridge",
)
