package com.euhomy.fridge.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

// ── DP type codes (mirrors Python const.py) ─────────────────────────────────
object DpType {
    const val RAW    = 0x00
    const val BOOL   = 0x01
    const val INT    = 0x02
    const val STRING = 0x03
    const val ENUM   = 0x04
    const val BITMAP = 0x05
}

// ── DP IDs for the Euhomy CFC-25 ────────────────────────────────────────────
object DpId {
    const val SWITCH        = 101  // bool   – power on/off              [0x65]
    const val LOCK          = 102  // enum   – 0=unlock, 1=lock          [0x66]
    const val MODE          = 103  // enum   – "max" | "eco"             [0x67]
    const val BATTERY_PROT  = 104  // enum   – 0=Low, 1=Med, 2=High      [0x68]
    const val TEMP_UNIT     = 105  // enum   – 0=Celsius, 1=Fahrenheit   [0x69]
    const val TEMP_CURRENT  = 112  // int    – actual temp (°C)          [0x70]
    const val TEMP_SET      = 114  // int    – setpoint (°C), writable   [0x72]
    const val TEMP_CURRENT_F= 117  // int    – actual temp (°F) mirror   [0x75]
    const val TEMP_SET_F    = 119  // int    – setpoint (°F) mirror      [0x77]
    const val BATTERY_MV    = 122  // int    – battery voltage (mV)      [0x7a]
}

// ── Temperature limits ───────────────────────────────────────────────────────
object TempLimits {
    const val MIN_C = -20
    const val MAX_C =  20
    const val STEP  =   1
}

// ── Modes ────────────────────────────────────────────────────────────────────
object Mode {
    const val MAX = "max"
    const val ECO = "eco"
}

// ── BLE UUIDs ────────────────────────────────────────────────────────────────
object TuyaUuids {
    const val SERVICE      = "00001910-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_CHAR  = "00002b10-0000-1000-8000-00805f9b34fb"
    const val WRITE_CHAR   = "00002b11-0000-1000-8000-00805f9b34fb"
}

// ── Tuya BLE manufacturer ID used in BLE advertisements ─────────────────────
const val TUYA_MANUFACTURER_ID = 0x07D0
const val BLE_MTU = 20

// ── Data class ───────────────────────────────────────────────────────────────

/**
 * One Tuya Data Point.
 *
 * [value] is typed dynamically:
 *  - BOOL   → Boolean
 *  - INT    → Int
 *  - STRING / ENUM → String
 *  - BITMAP → Long (unsigned)
 *  - RAW    → ByteArray
 */
data class TuyaDP(
    val dpId:   Int,
    val dpType: Int,
    val value:  Any,
)

// ── Codec ────────────────────────────────────────────────────────────────────

/**
 * Serialise one DP for writing to the device.
 * Wire format: [id(1)] [type(1)] [len(1)] [value(len)]
 *
 * NOTE: 1-byte length (not 2) — confirmed from reverse engineering.
 */
fun encodeDP(dp: TuyaDP): ByteArray {
    val value: ByteArray = when (dp.dpType) {
        DpType.BOOL   -> byteArrayOf(if (dp.value as Boolean) 1 else 0)
        DpType.INT    -> ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                            .putInt(dp.value as Int).array()
        DpType.STRING -> dp.value.toString().toByteArray(Charsets.UTF_8)
        DpType.ENUM   -> when (val v = dp.value) {
                            is ByteArray -> v          // raw bytes (e.g. 0x00/0x01) passed directly
                            else         -> v.toString().toByteArray(Charsets.UTF_8)
                         }
        DpType.BITMAP -> ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                            .putInt((dp.value as Long).toInt()).array()
        else          -> if (dp.value is ByteArray) dp.value else ByteArray(0)
    }
    return byteArrayOf(dp.dpId.toByte(), dp.dpType.toByte(), value.size.toByte()) + value
}

/**
 * Deserialise a DP payload from the device.
 * Wire format: repeated [id(1)] [type(1)] [len(1)] [value(len)]
 */
fun decodeAllDPs(data: ByteArray): List<TuyaDP> {
    val result = mutableListOf<TuyaDP>()
    var pos = 0
    while (pos + 3 <= data.size) {
        val dpId   = data[pos].toInt() and 0xFF
        val dpType = data[pos + 1].toInt() and 0xFF
        val dpLen  = data[pos + 2].toInt() and 0xFF
        if (pos + 3 + dpLen > data.size) break
        val vBytes = data.copyOfRange(pos + 3, pos + 3 + dpLen)
        pos += 3 + dpLen

        val value: Any = try {
            when (dpType) {
                DpType.BOOL   -> vBytes.isNotEmpty() && vBytes[0] != 0.toByte()
                DpType.INT    -> {
                    // Port of Python: struct.unpack(">i", v_bytes.rjust(4, b"\x00"))[0]
                    val buf = ByteArray(4)
                    val src = if (vBytes.size >= 4) vBytes.copyOfRange(vBytes.size - 4, vBytes.size) else vBytes
                    src.copyInto(buf, 4 - src.size)
                    ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
                }
                DpType.STRING,
                DpType.ENUM   -> vBytes.toString(Charsets.UTF_8)
                DpType.BITMAP -> {
                    val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                    repeat(8 - vBytes.size) { buf.put(0) }
                    buf.put(vBytes)
                    buf.getLong(0)
                }
                else          -> vBytes.copyOf()
            }
        } catch (e: Exception) {
            continue
        }

        result.add(TuyaDP(dpId = dpId, dpType = dpType, value = value))
    }
    return result
}
