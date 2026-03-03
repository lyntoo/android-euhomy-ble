package com.euhomy.fridge.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

// ── Tuya BLE v3 command codes ────────────────────────────────────────────────
object TuyaCmd {
    const val DEVICE_INFO         = 0x0000
    const val PAIR                = 0x0001
    const val SEND_DPS            = 0x0002
    const val DEVICE_STATUS       = 0x0003
    const val RECEIVE_DP          = 0x8001
    const val RECEIVE_TIME_DP     = 0x8003
    const val RECEIVE_SIGN_DP     = 0x8004
    const val RECEIVE_SIGN_TIME_DP= 0x8005
    const val TIME1_REQ           = 0x8011
    const val TIME2_REQ           = 0x8012
}

private const val PROTOCOL_VERSION = 2

/**
 * Build the list of MTU-sized BLE write packets for one logical Tuya BLE v3
 * message. Direct port of Python's `_build_packets()`.
 *
 * @param seqNum      monotonically increasing sequence counter
 * @param code        [TuyaCmd] value
 * @param data        unencrypted payload bytes
 * @param loginKey    MD5(localKey[:6]) — used only for DEVICE_INFO (flag=0x04)
 * @param sessionKey  MD5(localKey[:6] + srand) — used for everything else (flag=0x05)
 * @param responseTo  seqNum of the message we are responding to (0 for commands)
 * @param mtu         max bytes per BLE packet (default 20)
 */
fun buildPackets(
    seqNum:     Int,
    code:       Int,
    data:       ByteArray,
    loginKey:   ByteArray,
    sessionKey: ByteArray?,
    responseTo: Int = 0,
    mtu:        Int = BLE_MTU,
): List<ByteArray> {

    // 1. Choose key and security flag
    val key:          ByteArray
    val securityFlag: Byte
    if (code == TuyaCmd.DEVICE_INFO) {
        key          = loginKey
        securityFlag = 0x04
    } else {
        key          = sessionKey ?: loginKey
        securityFlag = 0x05
    }

    // 2. Build inner payload: seq(4) + responseTo(4) + code(2) + len(2) + data + crc(2)
    val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }

    val inner = ByteBuffer.allocate(4 + 4 + 2 + 2 + data.size + 2)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(seqNum)
        .putInt(responseTo)
        .putShort(code.toShort())
        .putShort(data.size.toShort())
        .put(data)
        .let { buf ->
            // crc over everything so far
            val bytes = buf.array().copyOf(buf.position())
            val crc = crc16(bytes)
            buf.putShort(crc.toShort())
            buf.array().copyOf(buf.position())
        }

    // 3. Zero-pad inner to 16-byte boundary
    val padded = if (inner.size % 16 != 0) {
        inner.copyOf(inner.size + (16 - inner.size % 16))
    } else {
        inner
    }

    // 4. Encrypt
    val encrypted: ByteArray = byteArrayOf(securityFlag) + iv + aesCbcEncrypt(key, iv, padded)

    // 5. Fragment into MTU-sized packets
    val packets = mutableListOf<ByteArray>()
    var packetNum = 0
    var pos = 0
    val totalLen = encrypted.size

    while (pos < totalLen) {
        val header = packInt(packetNum).let { pn ->
            if (packetNum == 0) {
                pn + packInt(totalLen) + byteArrayOf((PROTOCOL_VERSION shl 4).toByte())
            } else {
                pn
            }
        }
        val chunkSize = mtu - header.size
        val chunk = encrypted.copyOfRange(pos, minOf(pos + chunkSize, totalLen))
        packets.add(header + chunk)
        pos += chunk.size
        packetNum++
    }

    return packets
}
