package com.lampremote.core

/** Low level helpers shared by the codecs (ported from ble_adv/codecs/utils.py). */

internal fun ints(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

internal fun Byte.u() = toInt() and 0xFF

/** CRC16 CCITT (same as Python binascii.crc_hqx). */
fun crcHqx(data: ByteArray, seed: Int): Int {
    var crc = seed and 0xFFFF
    for (b in data) {
        crc = crc xor (b.u() shl 8)
        repeat(8) {
            crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
        }
    }
    return crc
}

internal fun reverse8(x: Int): Int {
    var v = x and 0xFF
    v = ((v and 0x55) shl 1) or ((v and 0xAA) shr 1)
    v = ((v and 0x33) shl 2) or ((v and 0xCC) shr 2)
    return ((v and 0x0F) shl 4) or ((v and 0xF0) shr 4)
}

internal fun reverseAll(buf: ByteArray) = ByteArray(buf.size) { reverse8(buf[it].toInt()).toByte() }

/** 7 bit LFSR whitening (symmetric: whitens and un-whitens). */
internal fun whiten(buffer: ByteArray, seed: Int): ByteArray {
    val out = ByteArray(buffer.size)
    var r = seed
    for ((idx, v) in buffer.withIndex()) {
        var b = 0
        for (j in 0 until 8) {
            r = r shl 1
            if (r and 0x80 != 0) {
                r = r xor 0x11
                b = b or (1 shl j)
            }
            r = r and 0x7F
        }
        out[idx] = (v.u() xor b).toByte()
    }
    return out
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Parses "02 01 1a, 0x1b ..." style text (any non hex-digit separator, optional 0x) into bytes. Null if odd/empty. */
fun hexToBytes(text: String): ByteArray? {
    val cleaned = text.replace(Regex("0[xX]"), " ").filter { it.isLetterOrDigit() }
    if (cleaned.isEmpty() || cleaned.length % 2 != 0 || !cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return ByteArray(cleaned.length / 2) { cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal fun reverse16(x: Int): Int {
    var v = x and 0xFFFF
    v = ((v and 0x5555) shl 1) or ((v shr 1) and 0x5555)
    v = ((v and 0x3333) shl 2) or ((v shr 2) and 0x3333)
    v = ((v and 0x0F0F) shl 4) or ((v shr 4) and 0x0F0F)
    return ((v and 0x00FF) shl 8) or ((v shr 8) and 0x00FF)
}

/** CRC16 CCITT on the bit reversed buffer with bit reversed result (utils.rev_crc16_ccit). */
internal fun revCrc16Ccit(buffer: ByteArray, seed: Int, refIn: Boolean = true, refOut: Boolean = true): Int {
    val crc = crcHqx(reverseAll(buffer), if (!refIn) seed else seed xor 0xFFFF)
    return reverse16(if (!refOut) crc else crc xor 0xFFFF)
}

/** 16 bit whitening used by Mantra (utils.whiten16). */
internal fun whiten16(buffer: ByteArray, seed: Int, param: Int = 4777, xorer: Int = 73): ByteArray {
    val out = ByteArray(buffer.size)
    var r = seed and 0xFFFF
    for ((idx, v) in buffer.withIndex()) {
        var b = 0
        for (j in 0 until 8) {
            val high = 0x8000 and r
            r = (r shl 1) and 0xFFFF
            if (high != 0) {
                r = r xor param
                b = b or (1 shl (7 - j))
            }
            if (r == 0) r = 1061
        }
        out[idx] = (v.u() xor xorer xor b).toByte()
    }
    return out
}

/** utils.crc16_le */
internal fun crc16Le(buffer: ByteArray, seed: Int, poly: Int = 0x8408, refIn: Boolean = true, refOut: Boolean = true): Int {
    var crc = if (!refIn) seed else seed xor 0xFFFF
    for (b in buffer) {
        crc = crc xor b.u()
        repeat(8) { crc = if (crc and 1 != 0) (crc shr 1) xor poly else crc shr 1 }
    }
    return if (!refOut) crc else crc xor 0xFFFF
}
