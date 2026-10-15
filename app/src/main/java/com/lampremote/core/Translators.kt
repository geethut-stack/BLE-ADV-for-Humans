package com.lampremote.core

/** Attribute names / values shared with the generated translator tables (ble_adv codecs/const.py). */
object A {
    const val ON = "on"; const val BR = "br"; const val SUB_TYPE = "sub_type"; const val EFFECT = "effect"
    const val COLD = "cold"; const val WARM = "warm"; const val CT = "ct"; const val CT_REV = "ctr"
    const val RED = "r"; const val GREEN = "g"; const val BLUE = "b"
    const val RED_F = "rf"; const val GREEN_F = "gf"; const val BLUE_F = "bf"
    const val CMD = "cmd"; const val CMD_PAIR = "pair"; const val CMD_UNPAIR = "unpair"; const val CMD_TIMER = "timer"
    const val TIME = "s"; const val STEP = "step"
    const val LIGHT = "light"; const val DEVICE = "device"
    const val TYPE_ONOFF = "onoff"; const val TYPE_CWW = "cww"; const val TYPE_RGB = "rgb"
}

/** A change of an entity: which attributes changed + the full attribute map (ble_adv BleAdvEntAttr). */
class EntAttr(val chg: Set<String>, val attrs: Map<String, Any?>, val baseType: String, val index: Int)

object Translators {
    private fun num(v: Any?): Double? = when (v) {
        is Boolean -> if (v) 1.0 else 0.0
        is Number -> v.toDouble()
        else -> null
    }

    /** Python style equality (1 == 1.0 == True). */
    fun pyEq(a: Any?, b: Any?): Boolean {
        val x = num(a); val y = num(b)
        return if (x != null && y != null) x == y else a == b
    }

    fun matches(t: TransSpec, e: EntAttr): Boolean {
        if (!t.direct || t.entBase != e.baseType || t.entIndex != e.index) return false
        if (t.actions.none { it in e.chg }) return false
        if (!t.entEqs.all { (k, v) -> pyEq(e.attrs[k], v) }) return false
        if (!t.entMins.all { (k, v) -> val x = num(e.attrs[k]); x != null && x >= (num(v) ?: return false) }) return false
        return t.entMaxs.all { (k, v) -> val x = num(e.attrs[k]); x != null && x <= (num(v) ?: return false) }
    }

    private fun setField(c: EncCmd, name: String, v: Int) {
        when (name) {
            "param" -> c.param = v; "arg0" -> c.arg0 = v; "arg1" -> c.arg1 = v
            "arg2" -> c.arg2 = v; "arg3" -> c.arg3 = v; "arg4" -> c.arg4 = v
            else -> error("unknown enc field $name")
        }
    }

    fun toEnc(t: TransSpec, e: EntAttr): EncCmd {
        val c = EncCmd(t.cmd)
        for ((k, v) in t.encEqs) setField(c, k, (v as Number).toInt())
        for (cp in t.copies) setField(c, cp.attrEnc, (cp.factor * (num(e.attrs[cp.attrEnt]) ?: 0.0)).toInt())
        t.scopy?.let { sc ->
            var v = (sc.factor * (num(e.attrs[sc.attrEnt]) ?: 0.0)).toInt()
            for (d in sc.dests) {
                setField(c, d, v % sc.modulo)
                v /= sc.modulo
            }
        }
        when (t.cls) {
            "rw.TransRGB" -> {
                val r = num(e.attrs[A.RED]) ?: 0.0; val g = num(e.attrs[A.GREEN]) ?: 0.0; val b = num(e.attrs[A.BLUE]) ?: 0.0
                c.arg0 = ((15.0 * r).toInt() and 0x0F) or ((15.0 * g).toInt() shl 4)
                c.arg1 = (15.0 * b).toInt()
            }
            "hxlight.TransRGB" -> {
                val hue = rgbToHue(num(e.attrs[A.RED]) ?: 0.0, num(e.attrs[A.GREEN]) ?: 0.0, num(e.attrs[A.BLUE]) ?: 0.0)
                val x = (2.0 / 3.0 - hue)
                c.arg0 = Math.rint(((x % 1.0 + 1.0) % 1.0) * 255).toInt()
            }
            "agarce.AgarceFanTrans" -> {
                val a = e.attrs
                var a0 = (if (a[A.ON] == true) 0x80 else 0) or (num(a["speed"])?.toInt() ?: 0)
                if (a["dir"] != true) a0 = a0 or 0x10
                if (a["preset"] == "breeze") a0 = a0 or 0x20
                c.arg0 = a0
                c.arg1 = if (a["osc"] == true) 1 else 0
                var a2 = 0
                if ("speed" in e.chg) a2 = a2 or 0x01
                if ("dir" in e.chg) a2 = a2 or 0x02
                if ("preset" in e.chg) a2 = a2 or 0x04
                if (A.ON in e.chg) a2 = a2 or 0x08
                if ("osc" in e.chg) a2 = a2 or 0x10
                c.arg2 = a2
            }
        }
        return c
    }

    /** colorsys.rgb_to_hsv, hue only. */
    private fun rgbToHue(r: Double, g: Double, b: Double): Double {
        val maxc = maxOf(r, g, b); val minc = minOf(r, g, b)
        if (minc == maxc) return 0.0
        val d = maxc - minc
        val rc = (maxc - r) / d; val gc = (maxc - g) / d; val bc = (maxc - b) / d
        val h = when {
            r == maxc -> bc - gc
            g == maxc -> 2.0 + rc - bc
            else -> 4.0 + gc - rc
        }
        return ((h / 6.0) % 1.0 + 1.0) % 1.0
    }


    /** Every matching translator emits a command, in table order (ble_adv ent_to_enc). */
    fun entToEnc(codec: Codec, setName: String?, e: EntAttr): List<EncCmd> =
        codec.translators(setName).filter { matches(it, e) }.map { toEnc(it, e) }

    /** Which light sub types / fan speed counts a codec can drive with this translator set. */
    fun lightTypes(codec: Codec, setName: String?, index: Int = 0): Set<String> {
        val out = LinkedHashSet<String>()
        for (t in codec.translators(setName)) {
            if (!t.direct || t.entBase != A.LIGHT || t.entIndex != index) continue
            out.add((t.entEqs[A.SUB_TYPE] as? String) ?: A.TYPE_ONOFF)
        }
        return out
    }

    fun hasDirect(codec: Codec, setName: String?, base: String, index: Int, cmdAttr: String? = null): Boolean =
        codec.translators(setName).any { t ->
            t.direct && t.entBase == base && t.entIndex == index && (cmdAttr == null || t.entEqs[A.CMD] == cmdAttr)
        }
}

/**
 * Light entity logic (same rules as ble_adv light.py) for ON/OFF, cold-warm white and RGB lights.
 * [force] = treat the requested attributes as changed even when the remembered value is equal,
 * which is what a remote needs because the real lamp state is unknown.
 */
class LightLogic(
    val subType: String, val index: Int = 0, val minBrPct: Int = 3, val reverseCw: Boolean = false,
) {
    var isOn = false
    var brightness = 255      // 1..255
    var kelvin = DEFAULT_MAX_KELVIN
    var rgb = intArrayOf(255, 255, 255)

    companion object {
        const val DEFAULT_MIN_KELVIN = 2000
        const val DEFAULT_MAX_KELVIN = 6535
    }

    private fun pct(v: Double, min: Double = 0.0, max: Double = 1.0) = minOf(max, maxOf(min, v))

    private fun getCt(): Double = 1.0 - ((kelvin - DEFAULT_MIN_KELVIN) / (DEFAULT_MAX_KELVIN - DEFAULT_MIN_KELVIN).toDouble())

    fun attrs(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m[A.ON] = isOn
        m[A.SUB_TYPE] = subType
        if (subType == A.TYPE_ONOFF) return m
        val br = brightness / 255.0
        m[A.BR] = br
        m[A.EFFECT] = null
        if (subType == A.TYPE_CWW) {
            val ct = if (reverseCw) 1.0 - getCt() else getCt()
            val cold = minOf(1.0, (1.0 - ct) * 2.0)
            val warm = minOf(1.0, ct * 2.0)
            m[A.CT] = ct; m[A.CT_REV] = 1.0 - ct; m[A.WARM] = br * warm; m[A.COLD] = br * cold
        } else if (subType == A.TYPE_RGB) {
            val r = rgb[0] / 255.0; val g = rgb[1] / 255.0; val b = rgb[2] / 255.0
            m[A.RED] = r; m[A.GREEN] = g; m[A.BLUE] = b
            m[A.RED_F] = r * br; m[A.GREEN_F] = g * br; m[A.BLUE_F] = b * br
        }
        return m
    }

    private fun result(chg: Set<String>) = EntAttr(chg, attrs(), A.LIGHT, index)

    fun turnOff(): EntAttr { isOn = false; return result(setOf(A.ON)) }

    fun turnOn(): EntAttr { isOn = true; return result(setOf(A.ON)) }

    /** Brightness 1..255 (the minimum brightness setting is applied). */
    fun setBrightness(b: Int, withOn: Boolean): EntAttr {
        val minB = 255.0 * minBrPct / 100.0
        brightness = maxOf(minB, b.toDouble()).toInt().coerceIn(1, 255)
        isOn = true
        val chg = LinkedHashSet<String>()
        if (withOn) chg.add(A.ON)
        chg.add(A.BR)
        if (subType == A.TYPE_CWW) { chg.add(A.WARM); chg.add(A.COLD) }
        if (subType == A.TYPE_RGB) { chg.add(A.RED_F); chg.add(A.GREEN_F); chg.add(A.BLUE_F) }
        return result(chg)
    }

    fun setKelvin(k: Int, withOn: Boolean): EntAttr {
        kelvin = k.coerceIn(DEFAULT_MIN_KELVIN, DEFAULT_MAX_KELVIN)
        isOn = true
        val chg = linkedSetOf(A.CT, A.CT_REV, A.WARM, A.COLD)
        if (withOn) chg.add(A.ON)
        return result(chg)
    }

    /** Brightness and colour temperature together (both slider values known). */
    fun setCww(b: Int, k: Int, withOn: Boolean): EntAttr {
        val minB = 255.0 * minBrPct / 100.0
        brightness = maxOf(minB, b.toDouble()).toInt().coerceIn(1, 255)
        kelvin = k.coerceIn(DEFAULT_MIN_KELVIN, DEFAULT_MAX_KELVIN)
        isOn = true
        val chg = linkedSetOf(A.BR, A.WARM, A.COLD, A.CT, A.CT_REV)
        if (withOn) chg.add(A.ON)
        return result(chg)
    }

    fun setRgb(r: Int, g: Int, b: Int, withOn: Boolean): EntAttr {
        rgb = intArrayOf(r, g, b)
        isOn = true
        val chg = linkedSetOf(A.RED, A.GREEN, A.BLUE, A.RED_F, A.GREEN_F, A.BLUE_F)
        if (withOn) chg.add(A.ON)
        return result(chg)
    }
}

/** Device level commands (pair / unpair / timer). */
object DeviceCmds {
    private fun cmd(c: String, extra: Map<String, Any?> = emptyMap()) =
        EntAttr(setOf(A.CMD), mapOf<String, Any?>(A.CMD to c) + extra, A.DEVICE, 0)

    fun pair() = cmd(A.CMD_PAIR)
    fun unpair() = cmd(A.CMD_UNPAIR)
    fun timer(seconds: Int) = EntAttr(setOf(A.CMD, A.TIME), mapOf(A.CMD to A.CMD_TIMER, A.TIME to seconds.toDouble()), A.DEVICE, 0)
}
