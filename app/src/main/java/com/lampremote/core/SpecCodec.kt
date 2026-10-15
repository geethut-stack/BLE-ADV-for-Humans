package com.lampremote.core

import kotlin.random.Random

/** Generic codec flow (ble_adv BleAdvCodec.decode_adv / encode_advs); subclasses hold the brand specific crypto. */
abstract class SpecCodec(spec: CodecSpec) : Codec(spec) {

    protected abstract fun decrypt(buffer: ByteArray): ByteArray?
    protected abstract fun encrypt(buffer: ByteArray): ByteArray
    protected abstract fun convertToEnc(decoded: ByteArray): Decoded?
    protected abstract fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray

    private fun regionEquals(ref: ByteArray, raw: ByteArray, start: Int): Boolean {
        if (raw.size < start + ref.size) return false
        for (i in ref.indices) if (raw[start + i] != ref[i]) return false
        return true
    }

    override fun decode(adv: BleAdv): Decoded? {
        val raw = adv.raw
        val s = spec
        val lastPos = raw.size - s.footer.size
        if (adv.type != s.bleType || lastPos - s.header.size - s.headerStartPos != s.len) return null
        if (!regionEquals(s.header, raw, s.headerStartPos) || !regionEquals(s.footer, raw, lastPos)) return null
        val buf = raw.copyOfRange(0, s.headerStartPos) + raw.copyOfRange(s.headerStartPos + s.header.size, lastPos)
        val read = decrypt(buf) ?: return null
        if (!regionEquals(s.prefix, read, 0)) return null
        return convertToEnc(read.copyOfRange(s.prefix.size, read.size))
    }

    override fun encode(cmd: EncCmd, conf: Conf): List<BleAdv> {
        val s = spec
        conf.txCount = (conf.txCount + s.txStep) % s.txMax
        if (conf.txCount == 0) conf.appRestartCount = (conf.appRestartCount + 1) % 255
        if (conf.seed == 0 && s.seedMax > 0) conf.seed = Random.nextLong(1, s.seedMax + 1).toInt()
        val read = convertFromEnc(cmd, conf)
        val enc = encrypt(s.prefix + read)
        val full = enc.copyOfRange(0, s.headerStartPos) + s.header + enc.copyOfRange(s.headerStartPos, enc.size) + s.footer
        return listOf(BleAdv(s.bleType, full, s.adFlag, s.secondType, s.secondRaw))
    }

    // helpers
    protected fun le(b: ByteArray, from: Int, n: Int): Long {
        var v = 0L
        for (i in n - 1 downTo 0) v = (v shl 8) or (b[from + i].u().toLong())
        return v
    }

    protected fun leBytes(v: Long, n: Int) = ByteArray(n) { ((v shr (8 * it)) and 0xFF).toByte() }
}
