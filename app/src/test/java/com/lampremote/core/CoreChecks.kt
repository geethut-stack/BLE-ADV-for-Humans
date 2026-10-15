package com.lampremote.core

/** Shared checks (used by the JUnit test and by a plain main runner). Returns the list of failures. */
object CoreChecks {
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun u32(v: Int) = v.toLong() and 0xFFFFFFFFL

    fun run(): List<String> {
        val fails = ArrayList<String>()

        // ---------------------------------------------------------------- packets: encode, decode, scanner pipeline
        for ((i, v) in GoldenVectors.encode.withIndex()) {
            val tag = "#$i ${v.codec} cmd=0x%02X".format(v.cmd)
            val codec = Codecs.byId(v.codec)
            if (codec == null) { fails += "$tag: codec not registered"; continue }
            val conf = codec.newConf(v.id, v.index, v.params)
            conf.txCount = v.tx0
            conf.seed = v.seed0.toInt()
            val cmd = EncCmd(v.cmd, v.param, v.a0, v.a1, v.a2, v.a3, v.a4)
            val adv = codec.encode(cmd, conf)[0]
            if (adv.type != v.type) fails += "$tag: type ${adv.type} != ${v.type}"
            if (adv.adFlag != v.flag) fails += "$tag: flag ${adv.adFlag} != ${v.flag}"
            if (adv.raw.toHex() != v.raw) fails += "$tag: raw\n   got ${adv.raw.toHex()}\n   exp ${v.raw}"
            if (adv.secondType != v.secondType || adv.secondRaw?.toHex() != v.secondRaw) fails += "$tag: second adv"
            if (conf.txCount != v.txAfter) fails += "$tag: tx ${conf.txCount} != ${v.txAfter}"
            if (u32(conf.seed) != v.seedAfter) fails += "$tag: seed ${u32(conf.seed)} != ${v.seedAfter}"

            val d = v.dec
            val dec = codec.decode(BleAdv(v.type, unhex(v.raw), v.flag, v.secondType, v.secondRaw?.let { unhex(it) }))
            if (d == null) { if (dec != null) fails += "$tag: decoded but python did not"; continue }
            if (dec == null) { fails += "$tag: decode failed"; continue }
            val c = dec.cmd; val f = dec.conf
            val got = listOf(c.cmd, c.param, c.arg0, c.arg1, c.arg2, c.arg3, c.arg4, f.id, f.index, f.txCount, u32(f.seed))
            val exp = listOf(d.cmd, d.param, d.a0, d.a1, d.a2, d.a3, d.a4, d.id, d.index, d.tx, d.seed)
            if (got.map { it.toString() } != exp.map { it.toString() }) fails += "$tag: decode $got != $exp"
            if (codec.isV2 && (f.withSign != d.withSign || f.prefix.map { it.toLong() } != d.prefix)) fails += "$tag: v2 params"
            if (codec.spec.className == "SmartElfinEncoder" && f.extra.map { it.toLong() } != d.extra) fails += "$tag: extra params"

            // scanner pipeline: flags + AD structures (+ zero padding) -> fromRaw -> decodeAll
            val full = ArrayList<Byte>()
            if (adv.type != 0) {
                if (v.flag != null) { full += 0x02; full += 0x01; full += v.flag.toByte() }
                full += (adv.raw.size + 1).toByte(); full += adv.type.toByte()
                full += adv.raw.toList()
                val second = adv.secondRaw
                if (second != null) { full += (second.size + 1).toByte(); full += adv.secondType.toByte(); full += second.toList() }
                repeat(5) { full += 0 } // scan records are zero padded
            } else {
                full += adv.raw.toList() // raw codecs: the whole advertisement is the payload
            }
            val learned = Codecs.decodeAll(full.toByteArray())
            if (learned.none { it.codec.codecId == v.codec && it.conf.id == d.id && it.conf.index == d.index }) {
                fails += "$tag: not found by decodeAll (${learned.map { it.codec.codecId }})"
            }
        }

        // ---------------------------------------------------------------- translators: entity change -> command bytes
        for ((i, tv) in GoldenVectors.translators.withIndex()) {
            val tag = "T#$i ${tv.codec}/${tv.set} ${tv.base}_${tv.index} ${tv.chg}"
            val codec = Codecs.byId(tv.codec)
            if (codec == null) { fails += "$tag: codec not registered"; continue }
            val got = Translators.entToEnc(codec, tv.set, EntAttr(tv.chg.toSet(), tv.attrs, tv.base, tv.index))
                .map { listOf(it.cmd, it.param, it.arg0, it.arg1, it.arg2, it.arg3, it.arg4) }
            val exp = tv.expected.map { it.toList() }
            if (got != exp) fails += "$tag: $got != $exp"
        }

        // ---------------------------------------------------------------- light logic == ble_adv CWW maths (via translators)
        val ls = Codecs.byId("lampsmart_pro_v1")!!
        val ls2 = Codecs.byId("lampsmart_pro_v2")!!
        for (cw in GoldenVectors.coldWarm) {
            for (codec in listOf(ls, ls2)) {
                val light = LightLogic(A.TYPE_CWW, minBrPct = 0, reverseCw = cw.reversed)
                val cmds = Translators.entToEnc(codec, "default", light.setCww(cw.br, cw.kelvin, false)).filter { it.cmd == 0x21 }
                val c = cmds.firstOrNull()
                val gotCold = if (codec.isV2) c?.arg1 else c?.arg0
                val gotWarm = if (codec.isV2) c?.arg2 else c?.arg1
                if (gotCold != cw.cold || gotWarm != cw.warm) fails += "light cww ${codec.codecId} ${cw.br}/${cw.kelvin}/${cw.reversed}: $gotCold,$gotWarm != ${cw.cold},${cw.warm}"
            }
        }

        // ---------------------------------------------------------------- pairing table: every app resolves to real codecs
        for (app in Codecs.apps) {
            val codecs = app.entries.map { Codecs.byId(it.first) }
            if (codecs.any { it == null }) { fails += "app ${app.name}: unknown codec in ${app.entries.map { it.first }}"; continue }
            if (codecs.none { Translators.hasDirect(it!!, null, A.DEVICE, 0, A.CMD_PAIR) }) fails += "app ${app.name}: no pair command"
            for ((cid, params) in app.entries) {
                val codec = Codecs.byId(cid)!!
                val conf = codec.newConf(0x1234, 1, params)
                val cmds = Translators.entToEnc(codec, null, DeviceCmds.pair())
                if (cmds.isEmpty()) continue
                try { codec.encode(cmds[0], conf) } catch (e: Exception) { fails += "app ${app.name}: pairing encode $cid failed: $e" }
            }
        }

        // ---------------------------------------------------------------- helpers + the user's real capture
        if (hexToBytes("02 01 1A, 0x1b")?.toHex() != "02011a1b") fails += "hexToBytes"
        if (hexToBytes("zz") != null) fails += "hexToBytes invalid"
        val real = hexToBytes("02 01 02 1b 03 77f8b65f2b5e00fc3151d00b920824cb1bfc3cecf41ad4002405")!!
        val l = Codecs.decodeAll(real)
        if (l.none { it.codec.codecId == "lampsmart_pro_v1" && it.conf.id == 0x40508DL && it.cmd.cmd == 0x10 }) fails += "real capture V1 not recognised"
        return fails
    }
}
