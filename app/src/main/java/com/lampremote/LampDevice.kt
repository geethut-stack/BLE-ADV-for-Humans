package com.lampremote

import android.content.Context
import com.lampremote.core.A
import com.lampremote.core.Codecs
import com.lampremote.core.Conf
import com.lampremote.core.FanLampCodec
import com.lampremote.core.LightLogic
import com.lampremote.core.Translators
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One way of talking to a lamp: a codec + the identity the lamp was paired with. */
class Link(
    var codecId: String,
    var id: Long,
    var index: Int,
    var txCount: Int = 0,
    var seed: Int = 0,
    var withSign: Boolean = true,
    var prefix: IntArray = intArrayOf(0x20, 0x80, 0x00),
    var enabled: Boolean = true,
    var extra: IntArray = IntArray(0),
    var appRestart: Int = 1,
    /** Translator set (variant) of the codec, null = the codec's default. */
    var translatorSet: String? = null,
) {
    fun toConf() = Conf(id, index, txCount, seed, withSign, prefix.copyOf(), extra.copyOf(), appRestart)

    /** Store the rolling counters back after an encode. */
    fun apply(c: Conf) {
        txCount = c.txCount
        seed = c.seed
        appRestart = c.appRestartCount
    }

    fun describe(ctx: Context): String {
        val codec = Codecs.byId(codecId)
        val title = codec?.title ?: codecId
        val tail = when {
            codec == null -> ""
            !codec.canSend -> " (" + ctx.getString(R.string.listen_only) + ")"
            !codec.androidCanSend -> " (" + ctx.getString(R.string.android_no) + ")"
            else -> ""
        }
        return "$title · $codecId · id 0x%X · #%d$tail".format(id, index)
    }

    fun key() = "$codecId|$id|$index|$withSign|${prefix.joinToString(",")}|${extra.joinToString(",")}"
}

class LampDevice(val uid: String, var name: String) {
    companion object {
        const val TYPE_ONOFF = "onoff"
        const val TYPE_CWW = "cww"
        const val TYPE_RGB = "rgb"
    }

    var type: String = TYPE_CWW
    var secondLight = false
    var reverseCw = false
    var minBrPct = 3
    val links = ArrayList<Link>()

    // assumed state (the lamp never reports it)
    var on = false
    var on2 = false
    var brightness = 255
    var kelvin = 4000
    var r = 255
    var g = 255
    var b = 255
    var offAt = 0L

    /** Light entity logic (index 0 = main light with [type], index 1 = second light, on/off only). */
    fun light(index: Int): LightLogic {
        val l = LightLogic(if (index == 0) type else A.TYPE_ONOFF, index, minBrPct, reverseCw)
        l.isOn = if (index == 0) on else on2
        l.brightness = brightness
        l.kelvin = kelvin
        l.rgb = intArrayOf(r, g, b)
        return l
    }

    fun saveLight(index: Int, l: LightLogic) {
        if (index == 0) { on = l.isOn; brightness = l.brightness; kelvin = l.kelvin; r = l.rgb[0]; g = l.rgb[1]; b = l.rgb[2] } else on2 = l.isOn
    }

    /** Links that are enabled and can really be transmitted from Android. */
    fun usableLinks(): List<Link> = links.filter { it.enabled && Codecs.byId(it.codecId)?.androidCanSend == true }

    fun supportsTimer(): Boolean = usableLinks().any {
        val c = Codecs.byId(it.codecId)
        c != null && (c is FanLampCodec || Translators.hasDirect(c, it.translatorSet, A.DEVICE, 0, A.CMD_TIMER))
    }

    /** Best guess: the last ON/OFF we sent, or "off" once a timer we started has run out. */
    fun assumedOn(): Boolean {
        if (offAt > 0L && System.currentTimeMillis() >= offAt) return false
        return on
    }

    fun summary(ctx: Context): String {
        val kinds = links.filter { it.enabled }.joinToString(", ") { Codecs.byId(it.codecId)?.title ?: it.codecId }
            .ifEmpty { ctx.getString(R.string.no_link) }
        val kind = ctx.getString(when (type) { TYPE_CWW -> R.string.kind_cct; TYPE_RGB -> R.string.kind_rgb; else -> R.string.kind_onoff })
        return "$kind · $kinds"
    }
}

/** Persistent list of lamps (JSON in SharedPreferences). Main thread only. */
object Devices {
    private const val PREFS = "lamp_devices"
    private const val KEY = "json"
    private const val KEY_DEFAULT = "default_uid"
    private var cache: MutableList<LampDevice>? = null

    fun all(ctx: Context): List<LampDevice> = load(ctx)

    fun find(ctx: Context, uid: String?): LampDevice? = load(ctx).firstOrNull { it.uid == uid }

    fun default(ctx: Context): LampDevice? {
        val uid = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEFAULT, null)
        return find(ctx, uid) ?: load(ctx).firstOrNull()
    }

    fun setDefault(ctx: Context, dev: LampDevice) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DEFAULT, dev.uid).apply()
    }

    fun isDefault(ctx: Context, dev: LampDevice) = default(ctx)?.uid == dev.uid

    fun add(ctx: Context, dev: LampDevice) {
        load(ctx).add(dev)
        save(ctx)
    }

    fun remove(ctx: Context, dev: LampDevice) {
        load(ctx).removeAll { it.uid == dev.uid }
        save(ctx)
    }

    fun newUid() = UUID.randomUUID().toString().substring(0, 8)

    // ---------------------------------------------------------------- storage

    private fun load(ctx: Context): MutableList<LampDevice> {
        cache?.let { return it }
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = ArrayList<LampDevice>()
        val json = prefs.getString(KEY, null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) list.add(fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
            }
        }
        cache = list
        if (json == null) save(ctx)
        return list
    }

    fun save(ctx: Context) {
        val arr = JSONArray()
        (cache ?: return).forEach { arr.put(toJson(it)) }
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(d: LampDevice) = JSONObject().apply {
        put("uid", d.uid); put("name", d.name); put("type", d.type)
        put("second", d.secondLight); put("rev", d.reverseCw); put("minBr", d.minBrPct)
        put("on", d.on); put("on2", d.on2); put("br", d.brightness); put("k", d.kelvin); put("offAt", d.offAt)
        put("r", d.r); put("g", d.g); put("b", d.b)
        put(
            "links",
            JSONArray().also { arr ->
                d.links.forEach { l ->
                    arr.put(
                        JSONObject().apply {
                            put("codec", l.codecId); put("id", l.id); put("index", l.index)
                            put("tx", l.txCount); put("seed", l.seed); put("sign", l.withSign)
                            put("prefix", JSONArray(l.prefix.toList())); put("enabled", l.enabled)
                            put("extra", JSONArray(l.extra.toList())); put("ar", l.appRestart); put("ts", l.translatorSet ?: "")
                        },
                    )
                }
            },
        )
    }

    private fun fromJson(o: JSONObject): LampDevice {
        val d = LampDevice(o.getString("uid"), o.optString("name", "Lamp"))
        d.type = o.optString("type", LampDevice.TYPE_CWW)
        d.secondLight = o.optBoolean("second", false)
        d.reverseCw = o.optBoolean("rev", false)
        d.minBrPct = o.optInt("minBr", 3)
        d.on = o.optBoolean("on", false); d.on2 = o.optBoolean("on2", false)
        d.brightness = o.optInt("br", 255); d.kelvin = o.optInt("k", 4000); d.offAt = o.optLong("offAt", 0L)
        d.r = o.optInt("r", 255); d.g = o.optInt("g", 255); d.b = o.optInt("b", 255)
        val arr = o.optJSONArray("links") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val l = arr.getJSONObject(i)
            val p = l.optJSONArray("prefix")
            val prefix = if (p != null && p.length() == 3) intArrayOf(p.getInt(0), p.getInt(1), p.getInt(2)) else intArrayOf(0x20, 0x80, 0)
            val ex = l.optJSONArray("extra")
            val extra = if (ex != null) IntArray(ex.length()) { ex.getInt(it) } else IntArray(0)
            d.links.add(
                Link(
                    l.getString("codec"), l.getLong("id"), l.getInt("index"), l.optInt("tx", 0), l.optInt("seed", 0),
                    l.optBoolean("sign", true), prefix, l.optBoolean("enabled", true),
                    extra, l.optInt("ar", 1), l.optString("ts", "").ifEmpty { null },
                ),
            )
        }
        return d
    }
}
