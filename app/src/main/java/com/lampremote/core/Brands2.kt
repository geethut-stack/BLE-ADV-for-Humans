package com.lampremote.core

/** RW.LIGHT (codecs/rw.py). */
class RwCodec(spec: CodecSpec) : SpecCodec(spec) {
    private fun crc16(b: ByteArray) = crcHqx(b, 0x696B)

    override fun decrypt(buffer: ByteArray): ByteArray? {
        if (buffer.size < 18) return null
        val d = reverseAll(whiten(buffer, 0x69))
        val n = d.size
        if (d[8].u() != 0x4C || d[9].u() != 0xFF || d[10].u() != 0x00 || d[12].u() != 0x01 || d[14].u() != 0x02) return null
        if (((d[n - 2].u() shl 8) or d[n - 1].u()) != crc16(d.copyOfRange(0, n - 2))) return null
        val p = d[11].u() xor d[13].u() xor d[15].u()
        return ints(
            d[0].u() xor p, d[1].u() xor p, d[2].u() xor p xor d[1].u(), d[3].u() xor p xor d[1].u(), d[4].u() xor p xor d[1].u(),
            d[5].u() xor p xor d[2].u(), d[6].u() xor p xor d[2].u(), d[7].u() xor p xor d[2].u(),
            d[11].u() xor p xor d[5].u(), d[13].u() xor p xor d[5].u(), p,
        )
    }

    override fun encrypt(buffer: ByteArray): ByteArray {
        val b = IntArray(buffer.size) { buffer[it].u() }
        val b12 = b[1] xor b[2]
        val enc = ints(
            b[0] xor b[10], b[1] xor b[10], b[2] xor b[1], b[3] xor b[1], b[4] xor b[1],
            b[5] xor b12 xor b[10], b[6] xor b12 xor b[10], b[7] xor b12 xor b[10],
            0x4C, 0xFF, 0x00, b[8] xor b12, 0x01, b[9] xor b12, 0x02, b[8] xor b[9] xor b[10],
        )
        val crc = crc16(enc)
        return whiten(reverseAll(enc + ints(crc shr 8, crc and 0xFF)), 0x69)
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = le(d, 2, 4), index = d[6].u(), txCount = d[1].u(), seed = d[10].u())
        return Decoded(EncCmd(d[0].u(), 0, d[7].u(), d[8].u(), d[9].u()), conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray =
        ints(cmd.cmd, conf.txCount) + leBytes(conf.id, 4) + ints(conf.index, cmd.arg0, cmd.arg1, cmd.arg2, conf.seed)
}

/** LedSpot and HxLight (codecs/hxlight.py). */
class HxLightCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val hx = spec.className == "HxlightCodec"
    private val revArgs = ints(0xFF, 0xF7, 0xFE, 0x01)

    private fun crc(b: ByteArray) = revCrc16Ccit(b, 0xB12B, false, false)

    override fun decrypt(buffer: ByteArray): ByteArray? {
        if (buffer.size < 15) return null
        val d = whiten(buffer, 0x59)
        val n = d.size
        if (crc(d.copyOfRange(0, n - 2)) != le(d, n - 2, 2).toInt()) return null
        if ((d.copyOfRange(0, 12).sumOf { it.u() } and 0xFF) != d[12].u()) return null
        if (d[3].u() <= 0) return null
        return d.copyOfRange(0, 12)
    }

    override fun encrypt(buffer: ByteArray): ByteArray {
        val b = buffer + byteArrayOf((buffer.sumOf { it.u() } and 0xFF).toByte())
        val c = crc(b)
        return whiten(b + ints(c and 0xFF, c shr 8), 0x59)
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = ((d[1].u() shl 8) or d[2].u()).toLong(), index = d[3].u(), txCount = d[11].u())
        val c = EncCmd(d[5].u(), d[4].u(), d[6].u())
        c.arg3 = d[0].u() - 0x83
        if (hx) {
            c.arg1 = if (d[7].u() == (c.param.inv() and 0xFF)) 0 else d[7].u()
            c.arg2 = if (d[8].u() == (c.cmd.inv() and 0xFF)) 0 else d[8].u()
        }
        return Decoded(c, conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        val buf = ints(cmd.arg3 + 0x83, (conf.id shr 8).toInt() and 0xFF, conf.id.toInt() and 0xFF, conf.index, cmd.param, cmd.cmd, cmd.arg0) +
            revArgs + ints(conf.txCount)
        if (hx) {
            val t: IntArray = when {
                cmd.cmd == 0x65 && cmd.param == 0x07 -> intArrayOf(cmd.arg1, cmd.cmd.inv() and 0xFF, 0x55, 0x55)
                cmd.arg3 == 0x01 -> intArrayOf(cmd.param.inv() and 0xFF, cmd.cmd.inv() and 0xFF, 0x55, 0x55)
                else -> intArrayOf(cmd.param.inv() and 0xFF, cmd.cmd.inv() and 0xFF, cmd.arg0.inv() and 0xFF, 0x00)
            }
            for (i in 0 until 4) buf[7 + i] = t[i].toByte()
        }
        return buf
    }
}

/** Mantra (codecs/mantra.py). */
class MantraCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val family = ints(0x12, 0x34, 0x56, 0x78)

    private fun mix(buffer: ByteArray): ByteArray =
        buffer.copyOfRange(0, 5) + whiten16(buffer.copyOfRange(5, buffer.size), (buffer[2].u() shl 8) or buffer[3].u())

    override fun decrypt(buffer: ByteArray): ByteArray? = if (buffer.size < 6) null else mix(buffer)
    override fun encrypt(buffer: ByteArray): ByteArray = mix(buffer)

    override fun convertToEnc(d: ByteArray): Decoded? {
        if (d[2].u() != 0x06) return null
        for (i in 0 until 4) if (d[4 + i] != family[i]) return null
        val conf = Conf(id = ((d[8].u() shl 8) or d[9].u()).toLong(), index = 0, txCount = (d[0].u() shl 8) or d[1].u())
        return Decoded(EncCmd(d[3].u(), d[10].u(), d[11].u(), d[12].u(), d[13].u(), d[14].u(), d[15].u()), conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray =
        ints(conf.txCount shr 8, conf.txCount and 0xFF, 0x06, cmd.cmd) + family +
            ints((conf.id shr 8).toInt() and 0xFF, conf.id.toInt() and 0xFF, cmd.param, cmd.arg0, cmd.arg1, cmd.arg2, cmd.arg3, cmd.arg4)
}

/** Generic remote (codecs/remotes.py RemoteEncoder). */
class RemoteCodec(spec: CodecSpec) : SpecCodec(spec) {
    private fun checksum(b: ByteArray) = b.sumOf { it.u() } and 0xFF

    override fun decrypt(buffer: ByteArray): ByteArray? {
        if (buffer.size < 8) return null
        return if (checksum(buffer.copyOfRange(0, buffer.size - 1)) != buffer[buffer.size - 1].u()) null else buffer
    }

    override fun encrypt(buffer: ByteArray): ByteArray = buffer + byteArrayOf(checksum(buffer).toByte())

    override fun convertToEnc(d: ByteArray): Decoded? {
        val conf = Conf(id = le(d, 1, 4), txCount = d[6].u())
        val c = EncCmd(d[5].u() and 0x3F)
        c.arg0 = d[0].u(); c.arg1 = d[5].u() and 0xC0
        return Decoded(c, conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray =
        ints(cmd.arg0) + leBytes(conf.id, 4) + ints(cmd.cmd or cmd.arg1, conf.txCount)
}
