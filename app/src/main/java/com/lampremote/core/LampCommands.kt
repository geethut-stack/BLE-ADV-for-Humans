package com.lampremote.core

/** Builds the lamp commands for the FanLamp / LampSmart family (see fanlamp.py translators). */
object LampCommands {
    const val MIN_KELVIN = 2000   // full WARM
    const val MAX_KELVIN = 6535   // full COLD

    const val CMD_PAIR = 0x28
    const val CMD_UNPAIR = 0x45

    fun on(light: Int) = EncCmd(if (light == 0) 0x10 else 0x12)
    fun off(light: Int) = EncCmd(if (light == 0) 0x11 else 0x13)
    fun pair() = EncCmd(CMD_PAIR)
    fun unpair() = EncCmd(CMD_UNPAIR)

    /** Lamp-side auto off timer: 0x51 (V1 layout) / 0x41 (V2 layout), minutes in arg0 (+ arg1 high byte on V2). */
    fun timer(codec: Codec, minutes: Int): EncCmd =
        if (codec.isV2) EncCmd(0x41, arg0 = minutes % 256, arg1 = minutes / 256) else EncCmd(0x51, arg0 = minutes % 256)

    /**
     * Cold / warm white command. Same maths as ble_adv's CWW light entity:
     * brightness 1..255, kelvin 2000 (warm)..6535 (cold), [reversed] for wrongly wired lamps.
     */
    fun coldWarm(codec: Codec, brightness255: Int, kelvin: Int, reversed: Boolean): EncCmd {
        val br = brightness255.coerceIn(0, 255) / 255.0
        val k = kelvin.coerceIn(MIN_KELVIN, MAX_KELVIN)
        var ct = 1.0 - ((k - MIN_KELVIN) / (MAX_KELVIN - MIN_KELVIN).toDouble()) // 0.0 cold .. 1.0 warm
        if (reversed) ct = 1.0 - ct
        val cold = minOf(1.0, (1.0 - ct) * 2.0)
        val warm = minOf(1.0, ct * 2.0)
        val c = (255.0 * (br * cold)).toInt()
        val w = (255.0 * (br * warm)).toInt()
        return if (codec.isV2) EncCmd(0x21, arg0 = 0, arg1 = c, arg2 = w) else EncCmd(0x21, param = 0, arg0 = c, arg1 = w)
    }
}
