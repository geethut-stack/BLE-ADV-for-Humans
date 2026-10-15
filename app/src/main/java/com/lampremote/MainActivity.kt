package com.lampremote

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout

/** List of lamps (home screen). */
class MainActivity : BaseActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = scaffold(getString(R.string.app_name), true)
        col.addTo(hintText(getString(R.string.app_tagline), 17f), 0)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addTo(list, 16)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun addLamp() = startActivity(Intent(this, AddDeviceActivity::class.java))

    private fun refresh() {
        list.removeAllViews()
        val devices = Devices.all(this)
        if (devices.isEmpty()) {
            list.addTo(
                card {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(24), dp(36), dp(24), dp(28))
                    addView(iconBadge(R.drawable.ic_bulb, 88))
                    addTo(text(getString(R.string.no_lamps_title), 26f, true).apply { gravity = Gravity.CENTER }, 20)
                    addTo(hintText(getString(R.string.no_lamps_body), 17f).apply { gravity = Gravity.CENTER }, 10)
                },
                8,
            )
        } else {
            list.addTo(text(getString(R.string.my_lamps), 22f, true), 0)
            for (d in devices) list.addTo(lampCard(d), 14)
        }
        list.addView(divider())
        list.addTo(bigButton(getString(R.string.add_lamp), R.drawable.ic_add, true, 68) { addLamp() }, 0)
        Shortcuts.publishDynamic(this)
    }

    private fun lampCard(d: LampDevice) = card({
        startActivity(Intent(this, ControlActivity::class.java).putExtra(ControlActivity.EXTRA_UID, d.uid))
    }) {
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(iconBadge(R.drawable.ic_bulb, 56))
        val names = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        names.addView(text(d.name, 21f, true))
        names.addView(hintText(d.summary(this@MainActivity), 15f).apply { setPadding(0, dp(2), 0, 0) })
        if (Devices.isDefault(this@MainActivity, d)) {
            names.addView(text("★ " + getString(R.string.badge_widgets), 14f, true, cPrimary).apply { setPadding(0, dp(6), 0, 0) })
        }
        top.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(icon(R.drawable.ic_chevron, 28, cTextDim))
        addView(top)
    }
}
