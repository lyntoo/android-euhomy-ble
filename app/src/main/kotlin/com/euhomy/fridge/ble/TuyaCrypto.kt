package com.euhomy.fridge.ble

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ── MD5 ──────────────────────────────────────────────────────────────────────

/** Compute MD5 digest (16 bytes). Used for key derivation. */
fun md5(data: ByteArray): ByteArray =
    MessageDigest.getInstance("MD5").digest(data)

// ── AES-128-CBC ──────────────────────────────────────────────────────────────

/**
 * Encrypt [data] with AES-128-CBC.
 * [data] must already be padded to a 16-byte multiple (Tuya uses zero-padding, not PKCS7).
 */
fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(data)
}

/**
 * Decrypt [data] with AES-128-CBC.
 * Returns raw decrypted bytes (zero-padding intact — callers trim via data_len field).
 */
fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(data)
}

// ── CRC-16 (IBM / reversed polynomial 0xA001) ────────────────────────────────

/** Port of Python _crc16(). Used to protect the inner Tuya BLE payload. */
fun crc16(data: ByteArray): Int {
    var crc = 0xFFFF
    for (byte in data) {
        crc = crc xor (byte.toInt() and 0xFF)
        repeat(8) {
            val tmp = crc and 1
            crc = crc ushr 1
            if (tmp != 0) crc = crc xor 0xA001
        }
    }
    return crc and 0xFFFF
}

// ── Variable-length integer encoding (LEB128 unsigned) ───────────────────────

/**
 * Encode [value] as a variable-length integer (LEB128).
 * Used in the BLE packet framing header.
 */
fun packInt(value: Int): ByteArray {
    val result = mutableListOf<Byte>()
    var v = value
    while (true) {
        var curr = v and 0x7F
        v = v ushr 7
        if (v != 0) curr = curr or 0x80
        result.add(curr.toByte())
        if (v == 0) break
    }
    return result.toByteArray()
}

/**
 * Decode a variable-length integer starting at [pos] in [data].
 * Returns (value, nextPos).
 */
fun unpackInt(data: ByteArray, pos: Int): Pair<Int, Int> {
    var result = 0
    for (offset in 0..4) {
        val idx = pos + offset
        if (idx >= data.size) error("Truncated varint at pos $pos")
        val curr = data[idx].toInt() and 0xFF
        result = result or ((curr and 0x7F) shl (offset * 7))
        if (curr and 0x80 == 0) return Pair(result, idx + 1)
    }
    error("Varint too long at pos $pos")
}
