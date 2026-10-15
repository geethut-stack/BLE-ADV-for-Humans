package com.lampremote.core

import java.net.URLDecoder

/** Command translator (ble_adv Trans): entity change -> encoder command. Generated from the Python tables. */
class SCopy(val attrEnt: String, val dests: List<String>, val factor: Double, val modulo: Int)
class Copy(val attrEnt: String, val attrEnc: String, val factor: Double)

class TransSpec(
    val entBase: String, val entIndex: Int, val actions: List<String>,
    val entEqs: Map<String, Any?>, val entMins: Map<String, Any?>, val entMaxs: Map<String, Any?>,
    val cmd: Int, val encEqs: Map<String, Any?>,
    val copies: List<Copy>, val scopy: SCopy?, val direct: Boolean,
    /** Python translator subclass with special maths ("rw.TransRGB", ...), empty for the plain one. */
    val cls: String = "",
)

/** Static description of one codec (ble_adv BleAdvCodec fields), generated from the Python codecs. */
class CodecSpec(
    val codecId: String, val matchId: String, val className: String,
    val bleType: Int, val adFlag: Int?, val len: Int, val headerStartPos: Int,
    val txStep: Int, val txMax: Int, val seedMax: Long, val secondType: Int, val defTrans: String,
    val header: ByteArray, val prefix: ByteArray, val footer: ByteArray, val secondRaw: ByteArray?,
    val matchParams: List<Any?>, val attrs: Map<String, Any?>,
    val sets: Map<String, List<TransSpec>>,
) {
    fun attrInt(name: String, def: Int = 0): Int = (attrs[name] as? Number)?.toInt() ?: def
    fun attrBool(name: String, def: Boolean = false): Boolean = attrs[name] as? Boolean ?: def
    fun attrBytes(name: String): ByteArray = attrs[name] as? ByteArray ?: ByteArray(0)
}

/** A phone app and the codecs (with their params) its pairing uses (ble_adv PHONE_APPS). */
class AppSpec(val name: String, val entries: List<Pair<String, List<Any?>>>)

object SpecLoader {
    private fun dq(s: String) = URLDecoder.decode(s, "UTF-8")

    /** Decode a typed value written by gen_specs.py. */
    fun typed(s: String): Any? {
        if (s.length < 2) return null
        val body = s.substring(2)
        return when (s[0]) {
            'n' -> null
            'b' -> body == "1"
            'i' -> body.toLong()
            'f' -> body.toDouble()
            's' -> dq(body)
            'x' -> ByteArray(body.length / 2) { body.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            'l' -> if (body.isEmpty()) emptyList<Any?>() else body.split(",").map { typed(dq(it)) }
            else -> null
        }
    }

    private fun typedMap(s: String): Map<String, Any?> {
        if (s.isEmpty()) return emptyMap()
        val m = LinkedHashMap<String, Any?>()
        for (kv in s.split(";")) {
            val i = kv.indexOf('=')
            m[dq(kv.substring(0, i))] = typed(dq(kv.substring(i + 1)))
        }
        return m
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    class Result(val specs: List<CodecSpec>, val apps: List<AppSpec>)

    fun parse(text: String): Result {
        val trans = HashMap<Int, TransSpec>()
        val sets = HashMap<String, LinkedHashMap<String, List<TransSpec>>>()
        val pending = ArrayList<List<String>>()
        val apps = ArrayList<AppSpec>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val f = line.split("|")
            when (f[0]) {
                "TRANS" -> {
                    val copies = if (f[12].isEmpty()) emptyList() else f[12].split(";").map {
                        val p = it.split(",")
                        Copy(dq(p[0]), dq(p[1]), p[2].toDouble())
                    }
                    val scopy = if (f[13].isEmpty()) null else f[13].split(",").let {
                        SCopy(dq(it[0]), it[1].split("+").map { d -> dq(d) }, it[2].toDouble(), it[3].toInt())
                    }
                    trans[f[1].toInt()] = TransSpec(
                        f[2], f[3].toInt(), if (f[4].isEmpty()) emptyList() else f[4].split(",").map { dq(it) },
                        typedMap(f[5]), typedMap(f[6]), typedMap(f[7]), f[8].toInt(), typedMap(f[9]),
                        copies, scopy, f[14] == "1", f.getOrElse(16) { "" },
                    )
                }
                "TSET" -> sets.getOrPut(f[1]) { LinkedHashMap() }[dq(f[2])] =
                    if (f[3].isEmpty()) emptyList() else f[3].split(",").map { trans.getValue(it.toInt()) }
                "CODEC" -> pending.add(f)
                "APP" -> apps.add(
                    AppSpec(
                        dq(f[1]),
                        f[2].split(";").map { e ->
                            val p = e.split(",", limit = 2)
                            @Suppress("UNCHECKED_CAST")
                            Pair(dq(p[0]), (typed(dq(p[1])) as? List<Any?>) ?: emptyList())
                        },
                    ),
                )
            }
        }
        val specs = pending.map { f ->
            @Suppress("UNCHECKED_CAST")
            CodecSpec(
                f[1], f[2], f[3], f[4].toInt(), f[5].toInt().takeIf { it >= 0 }, f[6].toInt(), f[7].toInt(),
                f[8].toInt(), f[9].toInt(), f[10].toLong(), f[11].toInt(), f[12],
                hex(f[13]), hex(f[14]), hex(f[15]), if (f[16].isEmpty()) null else hex(f[16]),
                (typed(dq(f[17])) as? List<Any?>) ?: emptyList(), typedMap(f[18]),
                sets[f[1]] ?: LinkedHashMap(),
            )
        }
        return Result(specs, apps)
    }

    val loaded: Result by lazy {
        val stream = SpecLoader::class.java.classLoader!!.getResourceAsStream("codec_specs.txt")
            ?: error("codec_specs.txt missing")
        parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
}
