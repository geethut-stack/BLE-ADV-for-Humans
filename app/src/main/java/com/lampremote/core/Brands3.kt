package com.lampremote.core

/** Smart Light / Agarce lamps and remotes (codecs/agarce.py). */
class AgarceCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val remote = spec.className == "AgarceRemoteEncoder"
    private val sl = if (remote) 4 else 2
    private val added = if (remote) 0x1A else 0
    private val matrix = ints(0xAA, 0xBB, 0xCC, 0xDD, 0x5A, 0xA5, 0xA5, 0x5A)

    private fun crypt(buffer: ByteArray, seed: ByteArray): ByteArray {
        val pivot = seed + seed.reversedArray()
        return ByteArray(buffer.size) { (buffer[it].u() xor pivot[it % (2 * sl)].u() xor matrix[it % 8].u()).toByte() }
    }

    private fun sum(b: ByteArray, to: Int) = (0 until to).sumOf { b[it].u() }

    private fun decryptBase(buffer: ByteArray): ByteArray? {
        if (buffer.size < 2 + sl) return null
        if (((sum(buffer, buffer.size - 1) + added) and 0xFF) != buffer[buffer.size - 1].u()) return null
        val decoded = crypt(buffer.copyOfRange(1 + sl, buffer.size - 1), buffer.copyOfRange(1, 1 + sl))
        if (decoded.isEmpty() || (sum(decoded, decoded.size - 1) and 0xFF) != decoded[decoded.size - 1].u()) return null
        return buffer.copyOfRange(0, 1 + sl) + decoded
    }

    private fun encryptBase(buffer: ByteArray): ByteArray {
        val dec = buffer.copyOfRange(1 + sl, buffer.size)
        val withSum = dec + byteArrayOf((dec.sumOf { it.u() } and 0xFF).toByte())
        val out = buffer.copyOfRange(0, 1 + sl) + crypt(withSum, buffer.copyOfRange(1, 1 + sl))
        return out + byteArrayOf(((out.sumOf { it.u() } + added) and 0xFF).toByte())
    }

    override fun decrypt(buffer: ByteArray): ByteArray? {
        val d = decryptBase(buffer) ?: return null
        if (remote) return d
        if (d.size < 17) return null
        val isPair = (d[11].u() and 0xF0) == 0x00
        if (isPair && d[15].u() == 0x00) return null
        if (isPair) {
            d[0] = (d[0].u() or ((d[13].u() and 0x0F) shl 4)).toByte()
            d[15] = ((((d[15].u() and 0x0F) shl 4) + d[14].u()) and 0xFF).toByte()
            d[14] = 0
            d[13] = 0
        } else {
            d[15] = ((((d[15].u() and 0x0F) shl 4) + (d[11].u() and 0x0F)) and 0xFF).toByte()
        }
        return d.copyOfRange(0, d.size - 1)
    }

    override fun encrypt(buffer: ByteArray): ByteArray {
        if (remote) return encryptBase(buffer)
        val d = buffer.copyOf()
        val isPair = d[11].u() == 0x00
        if (isPair) {
            d[13] = ((d[0].u() and 0xF0) shr 4).toByte()
            d[14] = (d[15].u() and 0x0F).toByte()
            d[15] = ((((d[15].u() shr 4) and 0x0F) + 0xC0) and 0xFF).toByte()
            d[0] = (d[0].u() and 0x0F).toByte()
        } else {
            d[11] = (d[11].u() or (d[15].u() and 0x0F)).toByte()
            d[15] = ((d[15].u() shr 4) and 0x0F).toByte()
        }
        return encryptBase(d)
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        if (remote) {
            val conf = Conf(id = le(d, 8, 6), txCount = d[4].u(), appRestartCount = d[5].u(), seed = le(d, 0, 4).toInt())
            return Decoded(EncCmd(d[15].u(), 0, d[16].u(), d[17].u(), d[18].u()), conf)
        }
        val c = EncCmd(d[10].u() and 0xF0, 0, d[11].u(), d[12].u(), d[13].u())
        val conf = Conf(id = le(d, 6, 4), index = d[14].u(), txCount = d[2].u(), appRestartCount = d[3].u(), seed = le(d, 0, 2).toInt())
        return Decoded(c, conf)
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        if (remote) {
            val rolling = (conf.txCount xor cmd.cmd) and 0xFF
            return leBytes(conf.seed.toLong() and 0xFFFFFFFFL, 4) + ints(conf.txCount, conf.appRestartCount, 0, 0) + leBytes(conf.id, 6) +
                ints(0, cmd.cmd, if (cmd.arg0 != 0) cmd.arg0 else rolling, if (cmd.arg1 != 0) cmd.arg1 else 0xFF, cmd.arg2)
        }
        return leBytes(conf.seed.toLong() and 0xFFFF, 2) + ints(conf.txCount, conf.appRestartCount, 0x00, 0x10) + leBytes(conf.id, 4) +
            ints(cmd.cmd, cmd.arg0, cmd.arg1, cmd.arg2, conf.index)
    }
}

/** Zhi Jia / Zhi Guang (codecs/zhijia.py): V0, V1, V2 and the remote. */
class ZhijiaCodec(spec: CodecSpec) : SpecCodec(spec) {
    private enum class V { V0, V1, V2, REMOTE }

    private val v = when (spec.className) {
        "ZhijiaEncoderV0" -> V.V0
        "ZhijiaEncoderV1" -> V.V1
        "ZhijiaEncoderV2" -> V.V2
        else -> V.REMOTE
    }
    private val mac = spec.attrBytes("_mac")
    private val pivotIndex: Set<Int> = when (v) {
        V.V0 -> setOf(0, 1, 6, 7)
        V.V1, V.REMOTE -> setOf(2, 4, 9, 12, 13, 15)
        V.V2 -> setOf(3, 7, 11, 12, 13, 15)
    }
    private val pivotXor = v != V.V0

    private fun crc16(b: ByteArray, seed: Int) = crc16Le(b, seed, 0x8408, true, true)

    private fun pivot(buf: IntArray): IntArray {
        var p = 0
        for (i in buf.indices) if (i in pivotIndex) p = p xor buf[i]
        if (pivotXor) p = p xor (((p and 1) - 1) and 0xFF)
        return IntArray(buf.size) { buf[it] xor p }
    }

    private fun IntArray.bytes() = ByteArray(size) { this[it].toByte() }
    private fun ByteArray.ia() = IntArray(size) { this[it].u() }

    override fun decrypt(buffer: ByteArray): ByteArray? = when (v) {
        V.V0 -> {
            val d = whiten(whiten(buffer, 0x37), 0x7F)
            if (d.size < 3 || le(d, d.size - 2, 2).toInt() != crc16(d.copyOfRange(0, d.size - 2), 0)) null else d.copyOfRange(0, d.size - 2)
        }
        V.V1 -> {
            val d = whiten(buffer, 0x37)
            if (d.size < 3 || le(d, d.size - 2, 2).toInt() != crc16(d.copyOfRange(0, d.size - 2), 0)) null else d.copyOfRange(0, d.size - 2)
        }
        V.V2 -> {
            val b1 = whiten(buffer, 0x6F)
            if (b1.size < 9) null else {
                val b2 = whiten(b1.copyOfRange(0, b1.size - 2), 0xD3) + b1.copyOfRange(b1.size - 2, b1.size)
                if (b2.copyOfRange(b2.size - 7, b2.size).any { it.u() != 0 }) null else b2.copyOfRange(0, b2.size - 7)
            }
        }
        V.REMOTE -> buffer
    }

    override fun encrypt(buffer: ByteArray): ByteArray = when (v) {
        V.V0 -> {
            val c = crc16(buffer, 0)
            whiten(whiten(buffer + ints(c and 0xFF, c shr 8), 0x7F), 0x37)
        }
        V.V1 -> {
            val c = crc16(buffer, 0)
            whiten(buffer + ints(c and 0xFF, c shr 8), 0x37)
        }
        V.V2 -> {
            val b1 = buffer + ByteArray(7)
            whiten(whiten(b1.copyOfRange(0, b1.size - 2), 0xD3) + b1.copyOfRange(b1.size - 2, b1.size), 0x6F)
        }
        V.REMOTE -> buffer
    }

    // ---- V1 / V2 / remote shared
    private fun commonToEnc(d: IntArray): Decoded? {
        val addr = intArrayOf(d[7], d[10], d[13] xor d[4])
        if (!addr.contentEquals(mac.ia())) return null
        val conf = Conf(
            id = (d[2].toLong()) or ((d[12] xor d[2]).toLong() shl 8) or ((d[15] xor d[9]).toLong() shl 16),
            index = d[6], txCount = d[4],
        )
        return Decoded(EncCmd(d[9], 0, d[0], d[3], d[5]), conf)
    }

    private fun commonFromEnc(c: EncCmd, conf: Conf): IntArray {
        val m = mac.ia()
        val u = intArrayOf((conf.id and 0xFF).toInt(), ((conf.id shr 8) and 0xFF).toInt(), ((conf.id shr 16) and 0xFF).toInt())
        var key = c.cmd xor c.arg0 xor c.arg1 xor c.arg2
        key = key xor u[0] xor u[1] xor u[2] xor conf.txCount xor conf.index xor m[0] xor m[1] xor m[2]
        return intArrayOf(
            c.arg0, key, u[0], c.arg1, conf.txCount, c.arg2, conf.index, m[0], 0x00, c.cmd, m[1], 0x00,
            u[1] xor u[0], m[2] xor conf.txCount, 0x00, u[2] xor c.cmd, 0x00,
        )
    }

    override fun convertToEnc(decoded: ByteArray): Decoded? {
        when (v) {
            V.V0 -> {
                val d = pivot(decoded.ia())
                val conf = Conf(id = (d[0].toLong()) or (d[5].toLong() shl 8), index = d[2], txCount = d[0] xor d[6])
                return Decoded(EncCmd(d[4], 0, d[1], d[3], d[1] xor d[7]), conf)
            }
            V.V1 -> {
                val d = pivot(decoded.ia())
                val r = commonToEnc(d) ?: return null
                if (d[8] != 0 || d[11] != 0 || d[7] != d[14]) return null
                return r
            }
            V.V2 -> {
                val d = pivot(decoded.ia())
                val r = commonToEnc(d) ?: return null
                if ((d[2] xor d[3] xor d[4] xor d[7]) != d[8] || d[11] != 0 || (d[2] xor d[3] xor d[4] xor d[9]) != d[14]) return null
                return r
            }
            V.REMOTE -> {
                val raw = decoded.ia()
                val d = IntArray(raw.size) { raw[it] xor raw[5] }
                val r = commonToEnc(d) ?: return null
                if (d[8] != 1 || d[11] != 2 || d[2] != d[14]) return null
                return r
            }
        }
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        when (v) {
            V.V0 -> {
                val id = conf.id.toInt()
                val u0 = id and 0xFF; val u1 = (id shr 8) and 0xFF
                return pivot(intArrayOf(u0, cmd.arg0, conf.index, cmd.arg1, cmd.cmd, u1, u0 xor conf.txCount, cmd.arg0 xor cmd.arg2)).bytes()
            }
            V.V1 -> {
                val o = commonFromEnc(cmd, conf)
                o[14] = o[7]
                return pivot(o).bytes()
            }
            V.V2 -> {
                val o = commonFromEnc(cmd, conf)
                o[1] = o[1] xor o[9]
                o[8] = o[2] xor o[3] xor o[4] xor o[7]
                o[14] = o[2] xor o[3] xor o[4] xor o[9]
                return pivot(o).bytes()
            }
            V.REMOTE -> {
                val o = commonFromEnc(cmd, conf)
                o[1] = o[1] xor 0x04
                o[8] = 0x01
                o[11] = 0x02
                o[14] = o[2]
                o[16] = 0x06
                return IntArray(o.size) { o[it] xor 0xC9 xor 0x06 }.bytes()
            }
        }
    }
}

/** Zhi Mei Deng Kong (codecs/zhimei.py): V0, V1, V2. */
class ZhimeiCodec(spec: CodecSpec) : SpecCodec(spec) {
    private val ver = when (spec.className) { "ZhimeiEncoderV0" -> 0; "ZhimeiEncoderV1" -> 1; else -> 2 }
    private val matrix = intArrayOf(29, 4, 17, 32, 152, 117, 40, 70, 11, 175, 67, 172, 214, 190, 137, 142)

    private fun applyMatrix(b: ByteArray, key: Int): ByteArray {
        val pivot = matrix[((b[1].u() shr 4) and 15) xor (b[1].u() and 15)]
        return ByteArray(b.size) { (((b[it].u() xor pivot) + matrix[(key + it) and 0xF]) + 256).rem(256).toByte() }
    }

    private fun unapplyMatrix(b: ByteArray, key: Int): ByteArray {
        val pivot = ((b[0].u() - matrix[key and 0xF]) and 0xFF) xor 0xFF
        return ByteArray(b.size) { (((b[it].u() - matrix[(key + it) and 0xF] + 256) % 256) xor pivot).toByte() }
    }

    private fun checksum0(b: ByteArray, to: Int): Int = ((0 until to).sumOf { b[it].u() } + spec.header.sumOf { it.u() }) and 0xFF

    override fun decrypt(buffer: ByteArray): ByteArray? {
        when (ver) {
            0 -> {
                if (buffer.size < 2 || checksum0(buffer, buffer.size - 1) != buffer[buffer.size - 1].u()) return null
                return buffer
            }
            1 -> {
                val s = spec.headerStartPos
                if (buffer.size - s < 14) return null
                var d = unapplyMatrix(buffer.copyOfRange(s, buffer.size), 6)
                if (crcHqx(d.copyOfRange(0, d.size - 3), 0) != le(d, d.size - 2, 2).toInt()) return null
                d = d.copyOfRange(0, d.size - 2)
                if (d.size < 11) return null
                if (d[7].u() != 0xB4) d = d.copyOfRange(0, 9) + unapplyMatrix(d.copyOfRange(9, d.size), 10)
                if (d[2] != d[10] || d[0].u() != 0xFF || d[9].u() != 0xFF) return null
                for (i in 0 until s) if (buffer[i] != d[2 + i]) return null
                return d
            }
            else -> {
                val d = whiten(buffer, 0x48)
                if (d.size < 3 || revCrc16Ccit(d.copyOfRange(0, d.size - 2), 0) != le(d, d.size - 2, 2).toInt()) return null
                return d.copyOfRange(0, d.size - 2)
            }
        }
    }

    override fun encrypt(buffer: ByteArray): ByteArray = when (ver) {
        0 -> buffer + byteArrayOf(checksum0(buffer, buffer.size).toByte())
        1 -> {
            var b = buffer
            if (b[7].u() != 0xB4) b = b.copyOfRange(0, 9) + applyMatrix(b.copyOfRange(9, b.size), 10)
            val c = crcHqx(b.copyOfRange(0, b.size - 1), 0)
            b += ints(c and 0xFF, c shr 8)
            b.copyOfRange(2, 2 + spec.headerStartPos) + applyMatrix(b, 6)
        }
        else -> {
            val c = revCrc16Ccit(buffer, 0)
            whiten(buffer + ints(c and 0xFF, c shr 8), 0x48)
        }
    }

    override fun convertToEnc(d: ByteArray): Decoded? {
        when (ver) {
            0 -> return Decoded(EncCmd(d[4].u(), 0, d[5].u(), d[6].u(), d[7].u()), Conf(id = le(d, 2, 2), index = d[0].u(), txCount = d[1].u()))
            1 -> return Decoded(
                EncCmd(d[7].u(), 0, d[11].u(), d[12].u(), d[13].u()),
                Conf(id = le(d, 3, 4), index = d[8].u(), txCount = d[2].u(), seed = d[1].u()),
            )
            else -> {
                val x = d[0].u() xor d[1].u() xor d[6].u() xor d[7].u()
                val p = IntArray(d.size) { d[it].u() xor x }
                val conf = Conf(id = (p[5].toLong()) or (p[0].toLong() shl 8), index = p[2], txCount = p[6] xor p[0])
                return Decoded(EncCmd(p[4], 0, p[1], p[3], p[7] xor p[1]), conf)
            }
        }
    }

    override fun convertFromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        when (ver) {
            0 -> return ints(conf.index, conf.txCount) + leBytes(conf.id, 2) + ints(cmd.cmd, cmd.arg0, cmd.arg1, cmd.arg2)
            1 -> return ints(0xFF, conf.seed, conf.txCount) + leBytes(conf.id, 4) +
                ints(cmd.cmd, conf.index, 0xFF, conf.txCount, cmd.arg0, cmd.arg1, cmd.arg2)
            else -> {
                val u0 = (conf.id and 0xFF).toInt(); val u1 = ((conf.id shr 8) and 0xFF).toInt()
                val d = intArrayOf(u1, cmd.arg0, conf.index, cmd.arg1, cmd.cmd, u0, conf.txCount xor u1, cmd.arg0 xor cmd.arg2)
                val p = d[0] xor d[1] xor d[6] xor d[7]
                return ByteArray(d.size) { (d[it] xor p).toByte() }
            }
        }
    }
}
