package com.lampremote.core

import kotlin.random.Random

/**
 * Port of ble_adv/codecs/fanlamp.py: FanLamp Pro, LampSmart Pro (and the clones using their protocol)
 * for both the phone apps and the physical remotes. Verified against the Python codecs (see tests).
 */
class FanLampCodec(spec: CodecSpec) : Codec(spec) {

    enum class Kind { V1, V1AA, V1R0, V1R1, V2 }

    private val kind: Kind = when (spec.className) {
        "FanLampEncoderV1" -> Kind.V1
        "FanLampEncoderV1aa" -> Kind.V1AA
        "FanLampEncoderV1R0" -> Kind.V1R0
        "FanLampEncoderV1R1" -> Kind.V1R1
        else -> Kind.V2
    }
    private val arg2: Int = spec.attrInt("_arg2")
    private val arg2OnlyOnPair: Boolean = spec.attrBool("_arg2_only_on_pair", true)
    private val xor1: Boolean = spec.attrBool("_xor1")
    private val forcedCrc2: Int? = (spec.attrs["_forced_crc2"] as? Number)?.toInt()
    private val deviceType: Int = spec.attrInt("_device_type")

    companion object {
        private val PREFIX = ints(0xAA, 0x98, 0x43, 0xAF, 0x0B, 0x46, 0x46, 0x46)

        private val XBOXES = intArrayOf(
            0xB7, 0xFD, 0x93, 0x26, 0x36, 0x3F, 0xF7, 0xCC, 0x34, 0xA5, 0xE5, 0xF1, 0x71, 0xD8, 0x31, 0x15,
            0x04, 0xC7, 0x23, 0xC3, 0x18, 0x96, 0x05, 0x9A, 0x07, 0x12, 0x80, 0xE2, 0xEB, 0x27, 0xB2, 0x75,
            0xD0, 0xEF, 0xAA, 0xFB, 0x43, 0x4D, 0x33, 0x85, 0x45, 0xF9, 0x02, 0x7F, 0x50, 0x3C, 0x9F, 0xA8,
            0x51, 0xA3, 0x40, 0x8F, 0x92, 0x9D, 0x38, 0xF5, 0xBC, 0xB6, 0xDA, 0x21, 0x10, 0xFF, 0xF3, 0xD2,
            0xE0, 0x32, 0x3A, 0x0A, 0x49, 0x06, 0x24, 0x5C, 0xC2, 0xD3, 0xAC, 0x62, 0x91, 0x95, 0xE4, 0x79,
            0xE7, 0xC8, 0x37, 0x6D, 0x8D, 0xD5, 0x4E, 0xA9, 0x6C, 0x56, 0xF4, 0xEA, 0x65, 0x7A, 0xAE, 0x08,
            0xE1, 0xF8, 0x98, 0x11, 0x69, 0xD9, 0x8E, 0x94, 0x9B, 0x1E, 0x87, 0xE9, 0xCE, 0x55, 0x28, 0xDF,
            0x8C, 0xA1, 0x89, 0x0D, 0xBF, 0xE6, 0x42, 0x68, 0x41, 0x99, 0x2D, 0x0F, 0xB0, 0x54, 0xBB, 0x16,
        )

    }

    override val isV2: Boolean get() = kind == Kind.V2
    private val header: ByteArray get() = spec.header
    private val len: Int get() = spec.len
    private val seedMax: Int get() = spec.seedMax.toInt()

    override fun newConf(id: Long, index: Int, params: List<Any?>?): Conf {
        val conf = Conf(id, index)
        val p = params ?: spec.matchParams
        if (kind == Kind.V2 && p.size >= 2) {
            conf.withSign = p[0] as? Boolean ?: true
            val pre = (p[1] as? List<*>)?.map { (it as Number).toInt() }
            if (pre != null && pre.size == 3) conf.prefix = pre.toIntArray()
        }
        return conf
    }

    // ------------------------------------------------------------------ decode

    override fun decode(adv: BleAdv): Decoded? {
        if (adv.type != spec.bleType || adv.raw.size - header.size != len) return null
        for (i in header.indices) if (adv.raw[i] != header[i]) return null
        val buffer = adv.raw.copyOfRange(header.size, adv.raw.size)
        val decoded = decrypt(buffer) ?: return null
        return when (kind) {
            Kind.V1, Kind.V1AA -> v1ToEnc(decoded)
            Kind.V1R0, Kind.V1R1 -> v1r0ToEnc(decoded)
            Kind.V2 -> v2ToEnc(decoded)
        }
    }

    private fun crc2(buffer: ByteArray): Int = forcedCrc2 ?: crcHqx(buffer, 0xA5BE)

    private fun decrypt(buffer: ByteArray): ByteArray? = when (kind) {
        Kind.V1, Kind.V1R0, Kind.V1R1 -> {
            val d = reverseAll(whiten(buffer, 0x0C))
            val body = d.copyOfRange(0, d.size - 2)
            if (crc2(body) != ((d[d.size - 2].u() shl 8) or d[d.size - 1].u())) null else body
        }
        Kind.V1AA -> {
            val d = reverseAll(whiten(buffer, 0x2B))
            if (d[d.size - 1].u() != 0xAA) null else d.copyOfRange(0, d.size - 1)
        }
        Kind.V2 -> {
            val n = buffer.size
            val seed = buffer[n - 4].u() or (buffer[n - 3].u() shl 8)
            val crc = buffer[n - 2].u() or (buffer[n - 1].u() shl 8)
            if (crcHqx(buffer.copyOfRange(0, n - 2), seed xor 0xFFFF) != crc) null
            else buffer.copyOfRange(0, 2) +
                v2Whiten(buffer.copyOfRange(2, n - 4), seed and 0xFF, (buffer[1].u() and 0x3) shl 5) +
                buffer.copyOfRange(n - 4, n - 2)
        }
    }

    private fun encrypt(buffer: ByteArray): ByteArray = when (kind) {
        Kind.V1, Kind.V1R0, Kind.V1R1 -> {
            val c = crc2(buffer)
            whiten(reverseAll(buffer + ints(c shr 8, c and 0xFF)), 0x0C)
        }
        Kind.V1AA -> whiten(reverseAll(buffer + byteArrayOf(0xAA.toByte())), 0x2B)
        Kind.V2 -> {
            val n = buffer.size
            val seed = buffer[n - 2].u() or (buffer[n - 1].u() shl 8)
            val ob = buffer.copyOfRange(0, 2) +
                v2Whiten(buffer.copyOfRange(2, n - 2), seed and 0xFF, (buffer[1].u() and 0x3) shl 5) +
                buffer.copyOfRange(n - 2, n)
            val crc = crcHqx(ob, seed xor 0xFFFF)
            ob + ints(crc and 0xFF, crc shr 8)
        }
    }

    // ------------------------------------------------------------------ V1

    private fun getArg2(cmd: Int, a2: Int): Int =
        if (cmd == 0x22) a2
        else if (cmd == 0x28 || (!arg2OnlyOnPair && cmd != 0x12 && cmd != 0x13 && cmd != 0x1E && cmd != 0x1F)) arg2
        else 0

    private fun v1ToEnc(d: ByteArray): Decoded? {
        val seed = (d[10].u() shl 8) or d[11].u()
        val seed8 = seed and 0xFF
        val crc = (d[12].u() shl 8) or d[13].u()
        if (crcHqx(d.copyOfRange(0, 12), seed xor 0xFFFF) != crc) return null
        if (getArg2(d[0].u(), d[5].u()) != d[5].u()) return null
        if ((if (xor1) seed8 xor 1 else seed8) != d[9].u()) return null

        val conf = Conf()
        val groupIndex = d[1].u() or (d[2].u() shl 8)
        conf.index = (groupIndex and 0x0F00) shr 8
        conf.id = ((groupIndex and 0xF0FF) or ((seed8 xor d[8].u()) shl 16)).toLong()
        conf.txCount = d[6].u()
        conf.seed = seed

        val cmd = EncCmd(d[0].u())
        if (cmd.cmd != 0x28) {
            cmd.param = d[7].u()
            cmd.arg0 = d[3].u()
            cmd.arg1 = d[4].u()
            if (cmd.cmd == 0x22) cmd.arg2 = d[5].u()
        } else {
            cmd.param = if (arg2 == 0x00) 0 else d[7].u()
            if ((conf.id and 0xFF).toInt() != d[3].u() || ((conf.id shr 8) and 0xF0).toInt() != d[4].u()) return null
        }
        return Decoded(cmd, conf)
    }

    private fun v1FromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        val isPair = cmd.cmd == 0x28
        val id = conf.id.toInt()
        val o = java.io.ByteArrayOutputStream()
        o.write(cmd.cmd)
        val gi = (id and 0xF0FF) or ((conf.index and 0x0F) shl 8)
        o.write(gi and 0xFF); o.write((gi shr 8) and 0xFF)
        o.write(if (isPair) id and 0xFF else cmd.arg0)
        o.write(if (isPair) (id shr 8) and 0xF0 else cmd.arg1)
        o.write(getArg2(cmd.cmd, cmd.arg2))
        o.write(conf.txCount)
        o.write(if (arg2 == 0x00 && isPair) header[0].u() else cmd.param)
        val seed8 = conf.seed and 0xFF
        o.write(if (xor1) seed8 xor 1 else seed8 xor ((id shr 16) and 0xFF))
        o.write(if (xor1) seed8 xor 1 else seed8)
        o.write((conf.seed shr 8) and 0xFF); o.write(conf.seed and 0xFF)
        val body = o.toByteArray()
        val crc = crcHqx(body, conf.seed xor 0xFFFF)
        return body + ints(crc shr 8, crc and 0xFF)
    }

    // ------------------------------------------------------------------ V1 R0 / R1 (receive only remotes)

    private fun v1r0ToEnc(d: ByteArray): Decoded? {
        if (kind == Kind.V1R1) for (i in 8 until minOf(14, d.size)) if (d[i].u() != 0) return null
        val conf = Conf()
        val gi = d[1].u() or (d[2].u() shl 8)
        conf.index = (gi and 0x0F00) shr 8
        conf.id = (gi and 0xF0FF).toLong()
        val cmd = EncCmd(d[0].u(), param = d[7].u(), arg0 = d[3].u(), arg1 = d[4].u(), arg2 = d[5].u(), arg3 = d[6].u())
        return Decoded(cmd, conf)
    }

    private fun v1r0FromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        val id = conf.id.toInt()
        val uid = id or (conf.index shl 8)
        val trailers: IntArray = when {
            kind == Kind.V1R1 -> intArrayOf(0, 0, 0, 0, 0, 0)
            cmd.arg3 == 0 -> intArrayOf(if (cmd.arg2 != 0) 0x05 else 0x02, 0, 0, 0, if (cmd.arg2 != 0) 0x02 else 0x01, 0)
            else -> intArrayOf((id shr 8) and 0xFF, id and 0xFF, 0, 0, cmd.arg3, if (cmd.arg3 == 0x01) 0x01 else 0x00)
        }
        return ints(cmd.cmd, uid and 0xFF, (uid shr 8) and 0xFF, cmd.arg0, cmd.arg1, cmd.arg2, cmd.arg3, cmd.param, *trailers)
    }

    // ------------------------------------------------------------------ V2

    private fun v2Whiten(buf: ByteArray, seed: Int, salt: Int) =
        ByteArray(buf.size) { i -> (XBOXES[((seed + i + 9) and 0x1F) + salt] xor seed xor buf[i].u()).toByte() }

    private fun v2Sign(buf16: ByteArray, txCount: Int, seed: Int): Int {
        val key = ints(
            seed and 0xFF, (seed shr 8) and 0xFF, txCount, 0x0D, 0xBF, 0xE6, 0x42, 0x68,
            0x41, 0x99, 0x2D, 0x0F, 0xB0, 0x54, 0xBB, 0x16,
        )
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"))
        val ct = cipher.doFinal(buf16)
        val sign = ct[0].u() or (ct[1].u() shl 8)
        return if (sign != 0) sign else 0xFFFF
    }

    private fun v2ToEnc(d: ByteArray): Decoded? {
        val n = d.size
        val conf = Conf()
        conf.seed = d[n - 2].u() or (d[n - 1].u() shl 8)
        val sign = d[n - 5].u() or (d[n - 4].u() shl 8)
        if (sign != 0 && v2Sign(d.copyOfRange(1, 17), d[3].u(), conf.seed) != sign) return null
        if (deviceType != (d[4].u() or (d[5].u() shl 8))) return null

        conf.withSign = sign != 0
        conf.prefix = intArrayOf(d[0].u(), d[1].u(), d[2].u())
        conf.txCount = d[3].u()
        conf.id = d[6].u().toLong() or (d[7].u().toLong() shl 8) or (d[8].u().toLong() shl 16) or (d[9].u().toLong() shl 24)
        conf.index = d[10].u()
        val cmd = EncCmd(d[11].u(), param = d[13].u(), arg0 = d[14].u(), arg1 = d[15].u(), arg2 = d[16].u())
        return Decoded(cmd, conf)
    }

    private fun v2FromEnc(cmd: EncCmd, conf: Conf): ByteArray {
        val id = conf.id
        val base = ints(
            conf.prefix[0], conf.prefix[1], conf.prefix[2], conf.txCount,
            deviceType and 0xFF, (deviceType shr 8) and 0xFF,
            (id and 0xFF).toInt(), ((id shr 8) and 0xFF).toInt(), ((id shr 16) and 0xFF).toInt(), ((id shr 24) and 0xFF).toInt(),
            conf.index, cmd.cmd, 0, cmd.param, cmd.arg0, cmd.arg1, cmd.arg2,
        )
        val sign = if (conf.withSign) v2Sign(base.copyOfRange(1, 17), base[3].u(), conf.seed) else 0
        return base + ints(sign and 0xFF, sign shr 8, 0, conf.seed and 0xFF, (conf.seed shr 8) and 0xFF)
    }

    // ------------------------------------------------------------------ encode

    override fun encode(cmd: EncCmd, conf: Conf): List<BleAdv> {
        conf.txCount = (conf.txCount + 1) % spec.txMax
        if (conf.seed == 0 && seedMax > 0) conf.seed = Random.nextInt(1, seedMax + 1)
        val readable = when (kind) {
            Kind.V1, Kind.V1AA -> v1FromEnc(cmd, conf)
            Kind.V1R0, Kind.V1R1 -> v1r0FromEnc(cmd, conf)
            Kind.V2 -> v2FromEnc(cmd, conf)
        }
        return listOf(BleAdv(spec.bleType, header + encrypt(readable), spec.adFlag))
    }
}

/** Registry of all supported codecs, built from the generated specs (codec_specs.txt). */
object Codecs {
    private val factories: Map<String, (CodecSpec) -> Codec> = mapOf(
        "FanLampEncoderV1" to { s -> FanLampCodec(s) },
        "FanLampEncoderV1aa" to { s -> FanLampCodec(s) },
        "FanLampEncoderV1R0" to { s -> FanLampCodec(s) },
        "FanLampEncoderV1R1" to { s -> FanLampCodec(s) },
        "FanLampEncoderV2" to { s -> FanLampCodec(s) },
        "LeEncoder" to { s -> LeCodec(s) },
        "DuduguCodec" to { s -> DuduguCodec(s) },
        "RuiXinEncoder" to { s -> RuiXinCodec(s) },
        "RuiXinRemoteEncoder" to { s -> RuiXinCodec(s) },
        "EWeLinkCodec" to { s -> EWeLinkCodec(s) },
        "SmartElfinEncoder" to { s -> SmartElfinCodec(s) },
        "RwEncoder" to { s -> RwCodec(s) },
        "LedSpotCodec" to { s -> HxLightCodec(s) },
        "HxlightCodec" to { s -> HxLightCodec(s) },
        "MantraEncoder" to { s -> MantraCodec(s) },
        "RemoteEncoder" to { s -> RemoteCodec(s) },
        "AgarceEncoder" to { s -> AgarceCodec(s) },
        "AgarceRemoteEncoder" to { s -> AgarceCodec(s) },
        "ZhijiaEncoderV0" to { s -> ZhijiaCodec(s) },
        "ZhijiaEncoderV1" to { s -> ZhijiaCodec(s) },
        "ZhijiaEncoderV2" to { s -> ZhijiaCodec(s) },
        "ZhijiaEncoderRemote" to { s -> ZhijiaCodec(s) },
        "ZhimeiEncoderV0" to { s -> ZhimeiCodec(s) },
        "ZhimeiEncoderV1" to { s -> ZhimeiCodec(s) },
        "ZhimeiEncoderV2" to { s -> ZhimeiCodec(s) },
    )

    /** Registers the codec ports of other files (kept separate so each brand file stays small). */
    val all: List<Codec> by lazy { SpecLoader.loaded.specs.mapNotNull { factories[it.className]?.invoke(it) } }

    /** Codecs known from ble_adv but not ported to this app yet. */
    val notPorted: List<CodecSpec> by lazy { SpecLoader.loaded.specs.filter { it.className !in factories } }

    val apps: List<AppSpec> get() = SpecLoader.loaded.apps

    private val byId by lazy { all.associateBy { it.codecId } }

    fun byId(id: String): Codec? = byId[id]

    /** Try every codec on a received advertisement. */
    fun decodeAll(adv: BleAdv): List<Learned> = all.mapNotNull { codec ->
        try {
            codec.decode(adv)?.let { Learned(codec, it.cmd, it.conf) }
        } catch (_: Exception) {
            null
        }
    }

    /** Raw advertising bytes (scan record / pasted hex). Retries without the zero padding of scan records. */
    fun decodeAll(rawAdv: ByteArray): List<Learned> {
        val direct = decodeAll(BleAdv.fromRaw(rawAdv))
        if (direct.isNotEmpty()) return direct
        val trimmed = BleAdv.trimPadding(rawAdv)
        return if (trimmed.size != rawAdv.size && trimmed.isNotEmpty()) decodeAll(BleAdv.fromRaw(trimmed)) else direct
    }
}
