package com.lampremote.core

/** Encoder command: the lamp "opcode" and its arguments (ble_adv BleAdvEncCmd). */
class EncCmd(
    var cmd: Int,
    var param: Int = 0,
    var arg0: Int = 0,
    var arg1: Int = 0,
    var arg2: Int = 0,
    var arg3: Int = 0,
    var arg4: Int = 0,
)

/** Identity + rolling counters of one paired controller (ble_adv BleAdvConfig). */
class Conf(
    var id: Long = 0,
    var index: Int = 0,
    var txCount: Int = 0,
    /** 32 bit values are stored as their bit pattern. */
    var seed: Int = 0,
    /** FanLamp V2 only: packet carries an AES sign. */
    var withSign: Boolean = true,
    /** FanLamp V2 only: 3 byte prefix. */
    var prefix: IntArray = intArrayOf(0x20, 0x80, 0x00),
    /** SmartElfin only: 4 identity bytes (ble_adv codec_params[0]). */
    var extra: IntArray = IntArray(0),
    var appRestartCount: Int = 1,
) {
    fun copy() = Conf(id, index, txCount, seed, withSign, prefix.copyOf(), extra.copyOf(), appRestartCount)
}

/** One advertising data structure: AD type + payload, plus an optional second structure (ble_adv BleAdvAdvertisement). */
class BleAdv(
    val type: Int, val raw: ByteArray, val adFlag: Int? = null,
    val secondType: Int = 0, val secondRaw: ByteArray? = null,
) {
    companion object {
        /** Android scan records are zero padded: cut the advertising data at the first empty / broken AD structure. */
        fun trimPadding(rawAdv: ByteArray): ByteArray {
            var pos = 0
            while (pos < rawAdv.size) {
                val len = rawAdv[pos].u()
                if (len == 0 || pos + len >= rawAdv.size) break
                pos += len + 1
            }
            return rawAdv.copyOfRange(0, minOf(pos, rawAdv.size))
        }

        /** Parse raw advertising bytes (e.g. a ScanRecord) the way ble_adv does. */
        fun fromRaw(rawAdv: ByteArray): BleAdv {
            var bleType = 0
            var adFlag: Int? = null
            var raw = ByteArray(0)
            var pos = 0
            while (rawAdv.size - pos > 2) {
                val partLen = rawAdv[pos].u()
                if (partLen == 0) { pos += 1; continue }
                if (partLen > rawAdv.size - pos) break
                val partType = rawAdv[pos + 1].u()
                if (partType == 0x01 && partLen == 2) {
                    adFlag = rawAdv[pos + 2].u()
                } else if (partType == 0x03 || partType == 0x05 || partType == 0x07 || partType == 0x16 || partType == 0xFF) {
                    if (bleType == 0) {
                        bleType = partType
                        raw = rawAdv.copyOfRange(pos + 2, minOf(pos + partLen + 1, rawAdv.size))
                    }
                }
                pos += partLen + 1
            }
            if (bleType == 0) raw = rawAdv
            return BleAdv(bleType, raw, adFlag)
        }
    }
}

class Decoded(val cmd: EncCmd, val conf: Conf)

/** A codec that was able to decode a received advertisement. */
class Learned(val codec: Codec, val cmd: EncCmd, val conf: Conf)

private val ANDROID_TYPES = setOf(0x03, 0x05, 0x07, 0x16, 0xFF)

abstract class Codec(val spec: CodecSpec) {
    val codecId: String get() = spec.codecId
    val matchId: String get() = spec.matchId
    val bleType: Int get() = spec.bleType
    val adFlag: Int? get() = spec.adFlag

    /** Human readable brand / app name. */
    open val title: String get() = Brands.titleOf(spec.codecId)
    /** Android's public API can only build UUID lists, service data and manufacturer data advertisements. */
    val androidCanSend: Boolean
        get() = canSend && spec.bleType in ANDROID_TYPES && (spec.secondType == 0 || spec.secondType in ANDROID_TYPES)

    /** FanLamp "V2" command layout (used by the hand written FanLamp timer command). */
    open val isV2: Boolean = false
    /** False for remotes we can only listen to. */
    open val canSend: Boolean get() = spec.sets.values.any { set -> set.any { it.direct } }

    fun translators(setName: String?): List<TransSpec> = spec.sets[setName] ?: spec.sets[spec.defTrans] ?: emptyList()
    val translatorSetNames: List<String> get() = spec.sets.keys.toList()

    /** A new identity for this codec ([params] = match params like ble_adv pairing, default: codec's own). */
    open fun newConf(id: Long, index: Int, params: List<Any?>? = null): Conf = Conf(id, index)

    abstract fun decode(adv: BleAdv): Decoded?

    /** Encodes [cmd] with [conf]; updates the rolling counters like ble_adv does. */
    abstract fun encode(cmd: EncCmd, conf: Conf): List<BleAdv>
}

object Brands {
    private val table = listOf(
        "fanlamp" to "FanLamp Pro", "lampsmart" to "LampSmart Pro", "other_v1" to "LampSmart Pro",
        "remote_v1" to "FanLamp Pro remote", "zhijia" to "Zhi Jia", "zhiguang2" to "Zhi Guang 2", "zhiguang" to "Zhi Guang",
        "zhimei" to "Zhi Mei Deng Kong", "agarce" to "Smart Light (Agarce)", "lelight" to "LE Light", "ruixin" to "RuiXin",
        "rwlight" to "RW.LIGHT", "smartelfin" to "Smart Elfin", "hxlight" to "HxLight", "ledspot" to "LedSpot",
        "mantra" to "Mantra", "dudugu" to "Dudugu", "ewelink" to "eWeLink",
    )

    fun titleOf(codecId: String): String = table.firstOrNull { codecId.startsWith(it.first) }?.second ?: codecId
}
