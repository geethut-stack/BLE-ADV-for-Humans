package com.lampremote

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible trampoline behind the launcher shortcuts: sends the signal, shows a toast, closes.
 *  - ACTION_TOGGLE: ON if the lamp is believed to be off, otherwise OFF
 *  - ACTION_TIMER (+ EXTRA_MINUTES): lamp switches itself off after N minutes
 * EXTRA_UID selects the lamp (falls back to the default lamp).
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        const val ACTION_TOGGLE = "com.lampremote.SHORTCUT_TOGGLE"
        const val ACTION_TIMER = "com.lampremote.SHORTCUT_TIMER"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_UID = "uid"
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) run() else {
                Toast.makeText(applicationContext, R.string.perm_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LampAdvertiser.hasPermission(this)) run()
        else permissionLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
    }

    private fun run() {
        val app = applicationContext
        val dev = Devices.find(app, intent.getStringExtra(EXTRA_UID)) ?: Devices.default(app)
        if (dev == null) {
            Toast.makeText(app, app.getString(R.string.err_no_lamp), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val done: (String?, String) -> Unit = { err, okText ->
            Toast.makeText(app, err ?: okText, Toast.LENGTH_SHORT).show()
            finish()
        }
        when (intent.action) {
            ACTION_TOGGLE -> {
                LampController.toggle(app, dev) { done(it, app.getString(if (dev.assumedOn()) R.string.toast_on_fmt else R.string.toast_off_fmt, dev.name)) }
            }
            ACTION_TIMER -> {
                val m = intent.getIntExtra(EXTRA_MINUTES, 1).coerceIn(1, 10)
                LampController.timer(app, dev, m) { done(it, app.getString(R.string.toast_timer_fmt, dev.name, m)) }
            }
            else -> finish()
        }
    }
}
