package com.lampremote

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.lampremote.core.Codecs
import com.lampremote.core.LampCommands

/** Controls of one lamp. */
class ControlActivity : BaseActivity() {

    companion object {
        const val EXTRA_UID = "uid"
    }

    private lateinit var dev: LampDevice
    private lateinit var status: TextView
    private val advertiser by lazy { LampAdvertiser.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = Devices.find(this, intent.getStringExtra(EXTRA_UID))
        if (d == null) {
            finish()
            return
        }
        dev = d
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) advertiser.onStatus = { status.text = it }
    }

    override fun onPause() {
        advertiser.onStatus = null
        super.onPause()
    }

    private fun build() {
        val col = scaffold(dev.name, false)

        // ---- power ----------------------------------------------------------------------
        col.addTo(
            card {
                addView(sectionHeader(R.drawable.ic_bulb, dev.summary(this@ControlActivity)).also { })
                val on = bigButton(getString(R.string.btn_on), R.drawable.ic_power, true, 84) { withAdvertise { LampController.on(this@ControlActivity, dev) } }
                val off = bigButton(getString(R.string.btn_off), R.drawable.ic_power, false, 84) { withAdvertise { LampController.off(this@ControlActivity, dev) } }
                addTo(row(on, off, gapDp = 12), 18)
                status = addTo(hintText(getString(R.string.ready), 16f).apply { gravity = Gravity.CENTER }, 14)
            },
            6,
        )

        if (dev.type == LampDevice.TYPE_CWW) {
            col.addView(divider())
            col.addTo(levelsCard(), 0)
        } else if (dev.type == LampDevice.TYPE_RGB) {
            col.addView(divider())
            col.addTo(colorCard(), 0)
        }

        if (dev.secondLight) {
            col.addView(divider())
            col.addTo(
                card {
                    addView(sectionHeader(R.drawable.ic_bulb, getString(R.string.sec_light2)))
                    addTo(
                        row(
                            bigButton(getString(R.string.light2_on), null, true, 64) { withAdvertise { LampController.setLight2(this@ControlActivity, dev, true) } },
                            bigButton(getString(R.string.light2_off), null, false, 64) { withAdvertise { LampController.setLight2(this@ControlActivity, dev, false) } },
                            gapDp = 12,
                        ),
                        16,
                    )
                },
                0,
            )
        }

        // ---- timer ----------------------------------------------------------------------
        val hasTimer = dev.supportsTimer()
        if (hasTimer) {
            col.addView(divider())
            col.addTo(timerCard(), 0)
        }

        // ---- shortcuts ------------------------------------------------------------------
        col.addView(divider())
        col.addTo(
            card {
                addView(sectionHeader(R.drawable.ic_pin, getString(R.string.sec_shortcuts)))
                addTo(bigButton(getString(R.string.pin_toggle), R.drawable.ic_power, false, 60) { pin(Shortcuts.toggle(this@ControlActivity, dev)) }, 16)
                if (hasTimer) {
                    addTo(hintText(getString(R.string.pin_timer_hint), 16f), 18)
                    val timers = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    for (m in 1..Shortcuts.MAX_TIMER_MINUTES) {
                        timers.addView(
                            chipButton("$m") { pin(Shortcuts.timer(this@ControlActivity, dev, m)) },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) },
                        )
                    }
                    addTo(timers, 8)
                }
            },
            0,
        )

        // ---- settings -------------------------------------------------------------------
        col.addView(divider())
        col.addTo(
            card {
                addView(sectionHeader(R.drawable.ic_tune, getString(R.string.sec_settings)))
                addTo(bigButton(getString(R.string.edit_links), R.drawable.ic_edit, false, 60) { showEdit() }, 16)
                val isDefault = Devices.isDefault(this@ControlActivity, dev)
                addTo(
                    bigButton(getString(if (isDefault) R.string.used_widgets else R.string.use_widgets), null, false, 60) {
                        Devices.setDefault(this@ControlActivity, dev)
                        Toast.makeText(this@ControlActivity, getString(R.string.widgets_assigned, dev.name), Toast.LENGTH_SHORT).show()
                        recreate()
                    },
                    12,
                )
            },
            0,
        )
    }

    private fun levelsCard() = card {
        addView(sectionHeader(R.drawable.ic_sun, getString(R.string.sec_light)))

        val brPct0 = (dev.brightness * 100 / 255).coerceIn(1, 100)
        val brLabel = text(getString(R.string.brightness_fmt, brPct0), 19f, true)
        val brSlider = thickSlider(1f, 100f, brPct0.toFloat())
        val ctLabel = text(getString(R.string.ct_fmt, dev.kelvin), 19f, true)
        val ctSlider = thickSlider(LampCommands.MIN_KELVIN.toFloat(), LampCommands.MAX_KELVIN.toFloat(), dev.kelvin.toFloat())

        fun send() = withAdvertise {
            LampController.setLevel(this@ControlActivity, dev, 255 * Math.round(brSlider.value) / 100, Math.round(ctSlider.value))
        }
        val touch = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { send() }
        }
        brSlider.addOnSliderTouchListener(touch)
        ctSlider.addOnSliderTouchListener(touch)
        brSlider.addOnChangeListener { _, v, _ -> brLabel.text = getString(R.string.brightness_fmt, Math.round(v)) }
        ctSlider.addOnChangeListener { _, v, _ -> ctLabel.text = getString(R.string.ct_fmt, Math.round(v)) }

        addTo(brLabel, 20)
        addTo(brSlider, 4)
        addTo(ctLabel, 18)
        addTo(ctSlider, 4)
        addTo(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(getString(R.string.warm), 15f, true, 0xFFE08A00.toInt()), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(getString(R.string.cool), 15f, true, 0xFF3A8DDE.toInt()))
            },
            0,
        )
    }

    private fun colorCard() = card {
        addView(sectionHeader(R.drawable.ic_sun, getString(R.string.sec_color)))
        val colors = intArrayOf(0xFF2020, 0xFF8000, 0xFFE000, 0x00D030, 0x00D0D0, 0x2060FF, 0xA030FF, 0xFFFFFF)
        fun swatch(rgb: Int): LinearLayout = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(
                android.view.View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(0xFF000000.toInt() or rgb)
                        setStroke(dp(3), cLine)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
                },
            )
            setOnClickListener {
                withAdvertise { LampController.setColor(this@ControlActivity, dev, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF) }
            }
        }
        addTo(row(*Array(4) { swatch(colors[it]) }, gapDp = 4), 16)
        addTo(row(*Array(4) { swatch(colors[4 + it]) }, gapDp = 4), 10)

        val brPct0 = (dev.brightness * 100 / 255).coerceIn(1, 100)
        val brLabel = text(getString(R.string.brightness_fmt, brPct0), 19f, true)
        val brSlider = thickSlider(1f, 100f, brPct0.toFloat())
        brSlider.addOnChangeListener { _, v, _ -> brLabel.text = getString(R.string.brightness_fmt, Math.round(v)) }
        brSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                withAdvertise { LampController.setBrightness(this@ControlActivity, dev, 255 * Math.round(slider.value) / 100) }
            }
        })
        addTo(brLabel, 22)
        addTo(brSlider, 4)
    }

    private fun timerCard() = card {
        addView(sectionHeader(R.drawable.ic_timer, getString(R.string.sec_timer)))
        addTo(hintText(getString(R.string.timer_desc), 16f), 10)
        val label = text(getString(R.string.timer_fmt, 5), 22f, true).apply { gravity = Gravity.CENTER }
        val slider = thickSlider(1f, 10f, 5f, discrete = true)
        slider.addOnChangeListener { _, v, _ -> label.text = getString(R.string.timer_fmt, Math.round(v)) }
        addTo(label, 16)
        addTo(slider, 4)
        addTo(
            bigButton(getString(R.string.timer_start), R.drawable.ic_timer, true, 64) {
                withAdvertise { LampController.timer(this@ControlActivity, dev, Math.round(slider.value)) }
            },
            12,
        )
    }

    private fun pin(info: ShortcutInfoCompat) {
        Toast.makeText(
            this,
            getString(if (Shortcuts.pin(this, info)) R.string.pin_confirm else R.string.pin_unsupported),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun showEdit() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }
        val name = EditText(this).apply { setText(dev.name); hint = getString(R.string.name_hint); setSingleLine(); textSize = 19f }
        box.addTo(name, 0)

        val group = RadioGroup(this)
        val rCct = RadioButton(this).apply { id = 1; text = getString(R.string.kind_cct); textSize = 17f }
        val rRgb = RadioButton(this).apply { id = 3; text = getString(R.string.kind_rgb); textSize = 17f }
        val rOnoff = RadioButton(this).apply { id = 2; text = getString(R.string.kind_onoff); textSize = 17f }
        group.addView(rCct)
        group.addView(rRgb)
        group.addView(rOnoff)
        group.check(when (dev.type) { LampDevice.TYPE_CWW -> 1; LampDevice.TYPE_RGB -> 3; else -> 2 })
        box.addTo(group, 12)

        val second = CheckBox(this).apply { text = getString(R.string.has_second); isChecked = dev.secondLight; textSize = 17f }
        val rev = CheckBox(this).apply { text = getString(R.string.reverse_cw); isChecked = dev.reverseCw; textSize = 17f }
        box.addTo(second, 8)
        box.addTo(rev, 0)
        box.addView(divider())
        box.addTo(text(getString(R.string.links_title), 16f, true), 0)

        val checks = ArrayList<Pair<Link, CheckBox>>()
        for (link in dev.links) {
            val cb = CheckBox(this).apply { text = link.describe(this@ControlActivity); isChecked = link.enabled; textSize = 15f }
            checks.add(Pair(link, cb))
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(bigButton(getString(R.string.blink), null, false, 48) { withAdvertise { LampController.blink(this@ControlActivity, link, dev) } }.apply { textSize = 16f })
            box.addTo(r, 10)
            val sets = Codecs.byId(link.codecId)?.translatorSetNames ?: emptyList()
            if (sets.size > 1) {
                val vb = bigButton(getString(R.string.variant_fmt, link.translatorSet ?: getString(R.string.variant_default)), null, false, 44) {}
                vb.textSize = 15f
                vb.setOnClickListener {
                    val names = listOf(getString(R.string.variant_default)) + sets
                    val cur = names.indexOf(link.translatorSet ?: names[0]).coerceAtLeast(0)
                    MaterialAlertDialogBuilder(this).setTitle(R.string.variant_title)
                        .setSingleChoiceItems(names.toTypedArray(), cur) { d, which ->
                            link.translatorSet = if (which == 0) null else names[which]
                            vb.text = getString(R.string.variant_fmt, link.translatorSet ?: names[0])
                            d.dismiss()
                        }.show()
                }
                box.addTo(vb, 6)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_title)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.save) { _, _ ->
                dev.name = name.text.toString().ifBlank { dev.name }
                dev.type = when (group.checkedRadioButtonId) { 1 -> LampDevice.TYPE_CWW; 3 -> LampDevice.TYPE_RGB; else -> LampDevice.TYPE_ONOFF }
                dev.secondLight = second.isChecked
                dev.reverseCw = rev.isChecked
                checks.forEach { (l, c) -> l.enabled = c.isChecked }
                Devices.save(this)
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.delete) { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_q, dev.name))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        Devices.remove(this, dev)
                        finish()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .show()
    }
}
