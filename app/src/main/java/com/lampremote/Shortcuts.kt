package com.lampremote

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Builds the launcher shortcuts (pinned to the home screen, and the long-press app icon menu). */
object Shortcuts {
    const val MAX_TIMER_MINUTES = 5

    private fun intent(ctx: Context, action: String, dev: LampDevice) =
        Intent(ctx, ShortcutActivity::class.java).setAction(action).putExtra(ShortcutActivity.EXTRA_UID, dev.uid)

    fun toggle(ctx: Context, dev: LampDevice): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(ctx, "toggle_${dev.uid}")
            .setShortLabel(dev.name.take(12))
            .setLongLabel(ctx.getString(R.string.sc_toggle_long, dev.name))
            .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_shortcut_lamp))
            .setIntent(intent(ctx, ShortcutActivity.ACTION_TOGGLE, dev))
            .build()

    fun timer(ctx: Context, dev: LampDevice, minutes: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(ctx, "timer_${dev.uid}_$minutes")
            .setShortLabel(ctx.getString(R.string.sc_timer_short, minutes))
            .setLongLabel(ctx.getString(R.string.sc_timer_long, dev.name, minutes))
            .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_shortcut_timer))
            .setIntent(intent(ctx, ShortcutActivity.ACTION_TIMER, dev).putExtra(ShortcutActivity.EXTRA_MINUTES, minutes))
            .build()

    /** Long-press menu on the app icon: one lamp -> toggle + 1/3/5 min timers, several lamps -> a toggle each (max 4). */
    fun publishDynamic(ctx: Context) {
        try {
            val devices = Devices.all(ctx)
            val list = when {
                devices.isEmpty() -> emptyList()
                devices.size == 1 -> listOf(toggle(ctx, devices[0]), timer(ctx, devices[0], 1), timer(ctx, devices[0], 3), timer(ctx, devices[0], 5))
                else -> devices.take(4).map { toggle(ctx, it) }
            }
            ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
        } catch (_: Exception) {
        }
    }

    /** Asks the launcher to pin; returns false if the launcher can't pin shortcuts. */
    fun pin(ctx: Context, info: ShortcutInfoCompat): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(ctx) &&
            ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
}
