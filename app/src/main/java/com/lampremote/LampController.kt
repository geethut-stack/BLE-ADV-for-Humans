package com.lampremote

import android.content.Context
import com.lampremote.core.A
import com.lampremote.core.BleAdv
import com.lampremote.core.Codecs
import com.lampremote.core.DeviceCmds
import com.lampremote.core.EncCmd
import com.lampremote.core.EntAttr
import com.lampremote.core.FanLampCodec
import com.lampremote.core.LampCommands
import com.lampremote.core.LightLogic
import com.lampremote.core.Translators

/** Turns user actions into entity changes, runs them through each link's codec tables and queues the advertisements. */
object LampController {

    sealed class Op {
        class On(val light: Int = 0) : Op()
        class Off(val light: Int = 0) : Op()
        class Cww(val brightness255: Int, val kelvin: Int, val withOn: Boolean) : Op()
        class Brightness(val brightness255: Int, val withOn: Boolean) : Op()
        class Rgb(val r: Int, val g: Int, val b: Int, val withOn: Boolean) : Op()
        class Timer(val minutes: Int) : Op()
        object Pair : Op()
        object Unpair : Op()
    }

    /** Entity change for [op]; updates the remembered state of [dev] like Home Assistant would. */
    private fun entFor(op: Op, dev: LampDevice?): EntAttr? {
        fun withLight(index: Int, f: (LightLogic) -> EntAttr): EntAttr {
            val l = dev?.light(index) ?: LightLogic(A.TYPE_ONOFF, index)
            val e = f(l)
            dev?.saveLight(index, l)
            return e
        }
        return when (op) {
            is Op.On -> withLight(op.light) { it.turnOn() }
            is Op.Off -> withLight(op.light) { it.turnOff() }
            is Op.Cww -> withLight(0) { it.setCww(op.brightness255, op.kelvin, op.withOn) }
            is Op.Brightness -> withLight(0) { it.setBrightness(op.brightness255, op.withOn) }
            is Op.Rgb -> withLight(0) { it.setRgb(op.r, op.g, op.b, op.withOn) }
            Op.Pair -> DeviceCmds.pair()
            Op.Unpair -> DeviceCmds.unpair()
            is Op.Timer -> null
        }
    }

    /**
     * Encode every op for every usable link (op-major, so the lamp sees ON before the colour change)
     * and queue them. Rolling counters / seeds are written back to the links.
     */
    fun dispatch(
        ctx: Context, links: List<Link>, dev: LampDevice?, ops: List<Op>, label: String,
        holdMs: Long = LampAdvertiser.ADV_DURATION_MS, force: Boolean = false, onDone: ((String?) -> Unit)? = null,
    ) {
        val advs = ArrayList<BleAdv>()
        var anyLink = false
        for (op in ops) {
            val ent = entFor(op, dev)
            for (link in links) {
                if (!link.enabled && !force) continue
                val codec = Codecs.byId(link.codecId) ?: continue
                if (!codec.androidCanSend) continue
                anyLink = true
                val cmds: List<EncCmd> = when {
                    op is Op.Timer && codec is FanLampCodec -> listOf(LampCommands.timer(codec, op.minutes))
                    op is Op.Timer -> Translators.entToEnc(codec, link.translatorSet, DeviceCmds.timer(op.minutes * 60))
                    ent != null -> Translators.entToEnc(codec, link.translatorSet, ent)
                    else -> emptyList()
                }
                for (cmd in cmds) {
                    val conf = link.toConf()
                    advs += codec.encode(cmd, conf)
                    link.apply(conf)
                }
            }
        }
        if (dev != null) Devices.save(ctx)
        if (advs.isEmpty()) {
            val msg = ctx.getString(if (anyLink) R.string.err_unsupported_action else R.string.err_nothing)
            LampAdvertiser.get(ctx).onStatus?.invoke(msg)
            onDone?.invoke(msg)
            return
        }
        LampAdvertiser.get(ctx).enqueue(advs, label, holdMs, onDone)
    }

    /** Send [ops] to a device and update its assumed timer state. */
    fun send(ctx: Context, dev: LampDevice, ops: List<Op>, label: String, onDone: ((String?) -> Unit)? = null) {
        for (op in ops) {
            when (op) {
                is Op.On -> if (op.light == 0) dev.offAt = 0L
                is Op.Off -> if (op.light == 0) dev.offAt = 0L
                is Op.Timer -> dev.offAt = System.currentTimeMillis() + op.minutes * 60_000L
                else -> {}
            }
        }
        dispatch(ctx, dev.links, dev, ops, label, LampAdvertiser.ADV_DURATION_MS, false, onDone)
    }

    fun on(ctx: Context, dev: LampDevice, onDone: ((String?) -> Unit)? = null) =
        send(ctx, dev, listOf(Op.On(0)), ctx.getString(R.string.st_on_fmt, dev.name), onDone)

    fun off(ctx: Context, dev: LampDevice, onDone: ((String?) -> Unit)? = null) =
        send(ctx, dev, listOf(Op.Off(0)), ctx.getString(R.string.st_off_fmt, dev.name), onDone)

    /** ON if the lamp is believed to be off, else OFF. Returns the state that was sent. */
    fun toggle(ctx: Context, dev: LampDevice, onDone: ((String?) -> Unit)? = null): Boolean {
        val turnOn = !dev.assumedOn()
        if (turnOn) on(ctx, dev, onDone) else off(ctx, dev, onDone)
        return turnOn
    }

    fun setLight2(ctx: Context, dev: LampDevice, on: Boolean) =
        send(ctx, dev, listOf(if (on) Op.On(1) else Op.Off(1)), ctx.getString(if (on) R.string.st_l2_on_fmt else R.string.st_l2_off_fmt, dev.name))

    /** Brightness (1..255) and kelvin for cold / warm lamps; switches the lamp on first when it is believed to be off. */
    fun setLevel(ctx: Context, dev: LampDevice, brightness255: Int, kelvin: Int) {
        val br = maxOf(brightness255, (255 * dev.minBrPct) / 100).coerceIn(1, 255)
        send(ctx, dev, listOf(Op.Cww(br, kelvin, !dev.assumedOn())), ctx.getString(R.string.st_level_fmt, dev.name, br * 100 / 255, kelvin))
    }

    fun setBrightness(ctx: Context, dev: LampDevice, brightness255: Int) {
        val br = maxOf(brightness255, (255 * dev.minBrPct) / 100).coerceIn(1, 255)
        send(ctx, dev, listOf(Op.Brightness(br, !dev.assumedOn())), ctx.getString(R.string.st_bright_fmt, dev.name, br * 100 / 255))
    }

    fun setColor(ctx: Context, dev: LampDevice, r: Int, g: Int, b: Int) =
        send(ctx, dev, listOf(Op.Rgb(r, g, b, !dev.assumedOn())), ctx.getString(R.string.st_color_fmt, dev.name))

    fun timer(ctx: Context, dev: LampDevice, minutes: Int, onDone: ((String?) -> Unit)? = null) {
        val m = minutes.coerceIn(1, 10)
        send(ctx, dev, listOf(Op.Timer(m)), ctx.getString(R.string.st_timer_fmt, dev.name, m), onDone)
    }

    /** Blink (ON, OFF, ON, OFF, 1 s each) through a single link to find out whether it controls the lamp. */
    fun blink(ctx: Context, link: Link, dev: LampDevice?, onDone: ((String?) -> Unit)? = null) =
        dispatch(ctx, listOf(link), null, listOf(Op.On(0), Op.Off(0), Op.On(0), Op.Off(0)), ctx.getString(R.string.st_blink_fmt, link.codecId), 1000L, true, onDone).also {
            if (dev != null) Devices.save(ctx)
        }

    /** Send the pair command through every link (as ble_adv's pairing step does). */
    fun pair(ctx: Context, links: List<Link>, onDone: ((String?) -> Unit)? = null) =
        dispatch(ctx, links, null, listOf(Op.Pair), ctx.getString(R.string.st_pairing), 600L, true, onDone)
}
