package com.lampremote.core

/** LE Light (ble_adv codecs/le.py). */
class LeCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val xb = intArrayOf(0xCB, 0x6A, 0x95, 0x8D, 0xB6, 0x7B, 0x35, 0x5A, 0x6E, 0x49, 0x5C, 0x85, 0x37, 0x3C, 0xA6, 0x88)

    private fun checksum(b: ByteArray): Int = ((b.sumOf { it.u() } + 1) and 0xFF) xor 0xFF
    private fun xorBuf(b: ByteArray, salt: Int): ByteArray { val k = xb[salt and 15]; return ByteArray(b.size) { (b[it].u() xor k).toByte() } }

    override fun decrypt(buffer: ByteArray): ByteArray? {
        if (buffer.size < 6) return null
        val dataLen = buffer[0].u()
        val zeroLen = spec.len - dataLen - 1
        if (zeroLen < 0 || buffer[1].u() != 0x01) return null
        if (zeroLen > 0) {
            if (buffer.size < zeroLen) return null
            for (i in buffer.size - zeroLen until buffer.size) if (buffer[i].u() != 0) return null
        }
        val end = minOf(dataLen + 1, buffer.size)
        val tail = if (end > 6) buffer.copyOfRange(6, end) else ByteArray(0)
        val base = buffer.copyOfRange(2, 6) + xorBuf(tail, buffer[2].u())
        if (base.size < 6 || base[4].u() != 0xFE) return null
        if (checksum(base.copyOfRange(0, base.size - 1)) != base[base.size - 1].u()) return null
        return base.copyOfRange(0, base.size - 1)
    }

    override fun encrypt(buffer: ByteArray): ByteArray {
        val data = byteArrayOf((buffer.size + 2).toByte(), 1) + buffer + byteArrayOf(checksum(buffer).toByte())
        val zero = ByteArray(maxOf(0, spec.len - data.size))
        return data.copyOfRange(0, 6) + xorBuf(data.copyOfRange(6, data.size), data[2].u()) + zero
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = le(d, 0, 4), index = d[5].u(), txCount = d[6].u())
        val c = EncCmd(d[7].u())
        c.param = d[8].u()
        c.arg0 = if (c.param < 1) 0 else d[9].u()
        c.arg1 = if (c.param < 2) 0 else d[10].u()
        c.arg2 = if (c.param < 3) 0 else d[11].u()
        return Decoded(c, conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        val args = intArrayOf(cmd.arg0, cmd.arg1, cmd.arg2)
        return leBytes(conf.id, 4) + ints(0xFE, conf.index, conf.txCount, cmd.cmd, cmd.param, *args.copyOfRange(0, minOf(cmd.param, 3)))
    }
}

/** Dudugu (codecs/dudugu.py). */
class DuduguCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val key = ints(0x99, 0x6B, 0x75, 0x96)
    private val crcAdd = (le(key, 0, 2) + le(key, 2, 2)).toInt()

    private fun crc(buf: ByteArray, seed: Int) = (crcHqx(buf, 0xFF00 or (seed.inv() and 0xFF)) + crcAdd) and 0xFFFF

    override fun decrypt(buffer: ByteArray): ByteArray? {
        val data = whiten(buffer, 0x53)
        val n = data.size
        if (n < 8) return null
        val k = data[n - 3]
        for (i in 0 until n - 3) data[i] = (data[i].u() xor k.u()).toByte()
        if (le(data, n - 2, 2).toInt() != crc(data.copyOfRange(0, n - 2), data[n - 3].u())) return null
        for (i in 5 until n - 3) data[i] = (data[i].u() xor key[(i - 5) and 3].u()).toByte()
        return data.copyOfRange(0, n - 2)
    }

    override fun encrypt(buffer: ByteArray): ByteArray {
        val b = buffer.copyOf()
        val n = b.size
        for (i in 5 until n - 1) b[i] = (b[i].u() xor key[(i - 5) and 3].u()).toByte()
        val crc = crc(b, b[n - 1].u())
        val last = b[n - 1].u()
        for (i in 0 until n - 1) b[i] = (b[i].u() xor last).toByte()
        return whiten(b + ints(crc and 0xFF, crc shr 8), 0x53)
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = le(d, 0, 4), index = le(d, 4, 2).toInt(), txCount = d[7].u(), seed = d[20].u())
        return Decoded(EncCmd(d[8].u(), 0, d[11].u(), d[12].u(), d[13].u()), conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray =
        leBytes(conf.id, 4) + leBytes(conf.index.toLong(), 2) +
            ints(0x10, conf.txCount, cmd.cmd, 0, 0, cmd.arg0, cmd.arg1, cmd.arg2, 0, 0, 0, 0, 0, 0x93, conf.seed)
}

/** RuiXin lamps and remotes (codecs/ruixin.py). */
class RuiXinCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val padding = if (spec.className == "RuiXinRemoteEncoder") ints(0x07, 0x08, 0x09, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15) else ByteArray(6)

    private fun checksum(b: ByteArray, from: Int, to: Int): Int { var s = 0; for (i in from until to) s += b[i].u(); return s and 0xFF }

    override fun decrypt(buffer: ByteArray): ByteArray? {
        if (buffer.size < 16) return null
        val b0 = buffer[0].u()
        val out = ByteArray(16)
        out[0] = buffer[0]; out[1] = buffer[1]
        for (i in 0 until 14) out[2 + i] = (((buffer[2 + i].u() - b0 - i) + 256) % 256).toByte()
        if (checksum(out, 2, 15) != out[15].u()) return null
        return out.copyOfRange(0, 10)
    }

    override fun encrypt(buffer: ByteArray): ByteArray {
        val b = buffer + padding
        b[15] = checksum(b, 2, 15).toByte()
        val out = b.copyOf()
        for (i in 0 until 14) out[2 + i] = (((b[2 + i].u() + b[0].u() + i) + 256) % 256).toByte()
        return out
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = le(d, 2, 4), seed = d[0].u(), txCount = d[1].u())
        return Decoded(EncCmd(d[6].u(), 0, d[7].u(), d[8].u(), d[9].u()), conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray =
        ints(conf.seed, conf.txCount) + leBytes(conf.id, 4) + ints(cmd.cmd, cmd.arg0, cmd.arg1, cmd.arg2)
}

/** eWeLink BLE ADV devices (codecs/ewelink.py). */
class EWeLinkCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val xm = ints(0x41, 0x92, 0x53, 0x2A, 0xFC, 0xAB, 0xCE, 0x26, 0x0D, 0x1E, 0x99, 0x78, 0x00, 0x22, 0x99, 0xDE)

    override fun decrypt(buffer: ByteArray): ByteArray? {
        val n = buffer.size - 2
        if (n < 9 || n > xm.size) return null
        val data = ByteArray(n) { (buffer[it].u() xor xm[it].u()).toByte() }
        val last = data[n - 1].u()
        for (i in 7 until n - 1) data[i] = (data[i].u() xor last).toByte()
        if (le(buffer, buffer.size - 2, 2).toInt() != crcHqx(data, 0x5555)) return null
        return data
    }

    override fun encrypt(buffer: ByteArray): ByteArray {
        val crc = crcHqx(buffer, 0x5555)
        val b = buffer.copyOf()
        val last = b[b.size - 1].u()
        for (i in 7 until b.size - 1) b[i] = (b[i].u() xor last).toByte()
        for (i in b.indices) b[i] = (b[i].u() xor xm[i].u()).toByte()
        return b + ints(crc and 0xFF, crc shr 8)
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = le(d, 1, 4), txCount = d[0].u(), seed = d[13].u())
        return Decoded(EncCmd(d[6].u(), d[7].u(), d[8].u(), d[9].u(), d[10].u()), conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray =
        ints(conf.txCount) + leBytes(conf.id, 4) + ints(0x01, cmd.cmd, cmd.param, cmd.arg0, cmd.arg1, cmd.arg2, 0, 0, conf.seed)
}

/** Smart Elfin (codecs/smartelfin.py): no crypto, a second AD structure is appended. */
class SmartElfinCodec(spec: CodecSpec) : SpecCodec(spec) {
    override fun decrypt(buffer: ByteArray): ByteArray? = buffer
    override fun encrypt(buffer: ByteArray): ByteArray = buffer

    override fun newConf(id: Long, index: Int, params: List<Any?>?): Conf {
        val extra = (params?.getOrNull(0) as? List<*>)?.map { (it as Number).toInt() }?.toIntArray() ?: IntArray(4)
        return Conf(id, index, extra = extra)
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = le(d, 0, 3), txCount = d[3].u(), extra = IntArray(4) { d[4 + it].u() })
        val c = EncCmd(d[9].u()); c.param = d[8].u(); c.arg0 = d[10].u(); c.arg1 = d[11].u()
        return Decoded(c, conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray =
        leBytes(conf.id, 3) + ints(conf.txCount, *conf.extra, cmd.param, cmd.cmd, cmd.arg0, cmd.arg1)
}
