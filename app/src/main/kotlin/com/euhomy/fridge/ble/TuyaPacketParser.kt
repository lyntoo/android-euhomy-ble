package com.euhomy.fridge.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

// ── Parsed message sealed class ──────────────────────────────────────────────

sealed class ParsedMessage {
    /** Step-1 handshake response: session key should be derived from [srand]. */
    data class DeviceInfo(val srand: ByteArray, val seqNum: Int, val responseTo: Int) : ParsedMessage()

    /** Step-2 handshake response: [result] 0=ok, 2=already paired. */
    data class PairResult(val result: Int, val seqNum: Int, val responseTo: Int) : ParsedMessage()

    /** ACK for a DP write (SEND_DPS). */
    data class DpWriteAck(val result: Int, val seqNum: Int, val responseTo: Int) : ParsedMessage()

    /** ACK for a status query (DEVICE_STATUS). */
    data class StatusAck(val result: Int, val seqNum: Int, val responseTo: Int) : ParsedMessage()

    /** DP push from the device (RECEIVE_DP / RECEIVE_TIME_DP). */
    data class DpUpdate(val dps: List<TuyaDP>, val seqNum: Int, val code: Int) : ParsedMessage()

    /** Signed DP push — includes dp_seq and flags for ACK. */
    data class SignedDpUpdate(
        val dps: List<TuyaDP>,
        val dpSeq: Int,
        val flags: Int,
        val seqNum: Int,
        val code: Int,
    ) : ParsedMessage()

    /** Device requests millisecond timestamp. */
    data class Time1Request(val seqNum: Int) : ParsedMessage()

    /** Device requests local time struct. */
    data class Time2Request(val seqNum: Int) : ParsedMessage()

    /** Unknown or unhandled code. */
    data class Unknown(val code: Int, val data: ByteArray, val seqNum: Int) : ParsedMessage()
}

// ── Reassembler ──────────────────────────────────────────────────────────────

/**
 * Stateful reassembler for fragmented BLE notifications.
 *
 * Call [feed] for each incoming raw notification. When enough fragments have
 * accumulated to form a complete message, [feed] calls [onComplete] with the
 * fully assembled (still encrypted) payload.
 */
class PacketReassembler(private val onComplete: (ByteArray) -> Unit) {

    private var inBuf:          ByteArray? = null
    private var expectedPacket: Int        = 0
    private var expectedLen:    Int        = 0

    fun feed(raw: ByteArray) {
        if (raw.isEmpty()) return
        try {
            var pos = 0
            val (packetNum, nextPos) = unpackInt(raw, pos)
            pos = nextPos

            when {
                packetNum == 0 -> {
                    // First fragment: parse total length + version byte
                    val (totalLen, afterLen) = unpackInt(raw, pos)
                    pos = afterLen + 1   // skip protocol_version byte
                    expectedLen    = totalLen
                    inBuf          = byteArrayOf()
                    expectedPacket = 1
                }
                packetNum == expectedPacket -> expectedPacket++
                else -> {
                    // Out-of-order — reset
                    reset()
                    return
                }
            }

            val buf = inBuf ?: return
            inBuf = buf + raw.copyOfRange(pos, raw.size)

            val current = inBuf!!
            if (current.size >= expectedLen) {
                onComplete(current.copyOf(expectedLen))
                reset()
            }
        } catch (_: Exception) {
            reset()
        }
    }

    fun reset() {
        inBuf          = null
        expectedPacket = 0
        expectedLen    = 0
    }
}

// ── Message parser ───────────────────────────────────────────────────────────

/**
 * Decrypt and parse one complete Tuya BLE payload into a [ParsedMessage].
 *
 * @param payload    assembled bytes from [PacketReassembler]
 * @param loginKey   MD5(localKey[:6])
 * @param sessionKey MD5(localKey[:6] + srand), null before handshake completes
 */
fun parseMessage(
    payload:    ByteArray,
    loginKey:   ByteArray,
    sessionKey: ByteArray?,
): ParsedMessage? {
    if (payload.size < 17) return null

    val securityFlag = payload[0].toInt() and 0xFF
    val iv           = payload.copyOfRange(1, 17)
    val encrypted    = payload.copyOfRange(17, payload.size)

    val key = if (securityFlag == 4) loginKey else (sessionKey ?: loginKey)

    val raw = try {
        aesCbcDecrypt(key, iv, encrypted)
    } catch (_: Exception) {
        return null
    }

    if (raw.size < 12) return null

    val buf        = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
    val seqNum     = buf.int          // offset 0
    val responseTo = buf.int          // offset 4
    val code       = buf.short.toInt() and 0xFFFF  // offset 8
    val dataLen    = buf.short.toInt() and 0xFFFF  // offset 10
    val dataStart  = 12
    val dataEnd    = dataStart + dataLen
    if (dataEnd > raw.size) return null
    val data = raw.copyOfRange(dataStart, dataEnd)

    return when (code) {
        TuyaCmd.DEVICE_INFO -> {
            // srand is at bytes [6..12) of the data payload
            val srand = if (data.size >= 12) data.copyOfRange(6, 12) else ByteArray(0)
            ParsedMessage.DeviceInfo(srand, seqNum, responseTo)
        }

        TuyaCmd.PAIR -> {
            val result = if (data.isNotEmpty()) data[0].toInt() and 0xFF else 1
            ParsedMessage.PairResult(result, seqNum, responseTo)
        }

        TuyaCmd.SEND_DPS -> {
            val result = if (data.isNotEmpty()) data[0].toInt() and 0xFF else 0
            ParsedMessage.DpWriteAck(result, seqNum, responseTo)
        }

        TuyaCmd.DEVICE_STATUS -> {
            val result = if (data.isNotEmpty()) data[0].toInt() and 0xFF else 0
            ParsedMessage.StatusAck(result, seqNum, responseTo)
        }

        TuyaCmd.RECEIVE_DP,
        TuyaCmd.RECEIVE_TIME_DP -> {
            val dps = decodeAllDPs(data)
            ParsedMessage.DpUpdate(dps, seqNum, code)
        }

        TuyaCmd.RECEIVE_SIGN_DP,
        TuyaCmd.RECEIVE_SIGN_TIME_DP -> {
            if (data.size < 3) return null
            val dpSeq  = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            val flags  = data[2].toInt() and 0xFF
            val dps    = decodeAllDPs(data.copyOfRange(3, data.size))
            ParsedMessage.SignedDpUpdate(dps, dpSeq, flags, seqNum, code)
        }

        TuyaCmd.TIME1_REQ -> ParsedMessage.Time1Request(seqNum)
        TuyaCmd.TIME2_REQ -> ParsedMessage.Time2Request(seqNum)

        else -> ParsedMessage.Unknown(code, data, seqNum)
    }
}

