package com.lampremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/** Receives the widget button taps and controls the lamp the widgets are assigned to. */
class LampActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ON = "com.lampremote.ACTION_ON"
        const val ACTION_OFF = "com.lampremote.ACTION_OFF"
        const val ACTION_TIMER = "com.lampremote.ACTION_TIMER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action != ACTION_ON && intent.action != ACTION_OFF && intent.action != ACTION_TIMER) return
        if (!LampAdvertiser.hasPermission(app)) {
            Toast.makeText(app, app.getString(R.string.err_open_app), Toast.LENGTH_LONG).show()
            return
        }
        val dev = Devices.default(app)
        if (dev == null) {
            Toast.makeText(app, app.getString(R.string.err_no_lamp), Toast.LENGTH_LONG).show()
            return
        }

        val minutes = TimerWidgetProvider.minutes(app)
        val label = when (intent.action) {
            ACTION_ON -> app.getString(R.string.toast_on_fmt, dev.name)
            ACTION_OFF -> app.getString(R.string.toast_off_fmt, dev.name)
            else -> app.getString(R.string.toast_timer_fmt, dev.name, minutes)
        }

        // Keep the receiver alive until the advertising is finished.
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val finish: (String?) -> Unit = { err ->
            if (finished.compareAndSet(false, true)) {
                Toast.makeText(app, err ?: label, Toast.LENGTH_SHORT).show()
                pending.finish()
            }
        }
        handler.postDelayed({ finish(null) }, 9000) // safety net, receivers must finish within ~10 s

        when (intent.action) {
            ACTION_ON -> LampController.on(app, dev, finish)
            ACTION_OFF -> LampController.off(app, dev, finish)
            else -> LampController.timer(app, dev, minutes, finish)
        }
    }
}
