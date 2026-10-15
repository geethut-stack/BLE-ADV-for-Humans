package com.lampremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lampremote.core.A
import com.lampremote.core.AppSpec
import com.lampremote.core.Codecs
import com.lampremote.core.Translators
import com.lampremote.core.Learned
import com.lampremote.core.hexToBytes
import kotlin.random.Random

/** Add a lamp: copy a remote (listen), paste a captured packet, or pair a brand new identity. */
class AddDeviceActivity : BaseActivity() {

    private class Cand(val link: Link, var note: String, var checked: Boolean)

    private val cands = LinkedHashMap<String, Cand>()
    private lateinit var candBox: LinearLayout
    private lateinit var statusCard: View
    private lateinit var status: TextView
    private lateinit var listenBtn: MaterialButton
    private lateinit var nameEdit: EditText
    private lateinit var kindGroup: RadioGroup
    private var scanCb: ScanCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = scaffold(getString(R.string.add_title), false)

        col.addTo(hintText(getString(R.string.add_intro), 17f), 4)

        // status banner (hidden until there is something to say)
        status = text("", 17f, true, cOnContainer)
        statusCard = card { addView(status) }.apply {
            visibility = View.GONE
            setCardBackgroundColor(cContainer)
        }
        col.addTo(statusCard, 16)

        // ---- 1. listen -------------------------------------------------------------
        col.addView(divider())
        col.addTo(
            card {
                addView(sectionHeader(0, getString(R.string.m1_title), numberBadge(1)))
                addTo(hintText(getString(R.string.m1_desc), 16f), 12)
                listenBtn = addTo(bigButton(getString(R.string.start_listening), R.drawable.ic_hearing, true, 60) { toggleListen() }, 16)
            },
            0,
        )

        // ---- 2. paste --------------------------------------------------------------
        col.addView(labelDivider(getString(R.string.or_label)))
        col.addTo(
            card {
                addView(sectionHeader(0, getString(R.string.m2_title), numberBadge(2)))
                addTo(hintText(getString(R.string.m2_desc), 16f), 12)
                val hex = EditText(context).apply { hint = getString(R.string.hex_hint); minLines = 3; textSize = 16f }
                addTo(hex, 12)
                addTo(bigButton(getString(R.string.decode), R.drawable.ic_doc, false, 60) { decodeHex(hex.text.toString()) }, 12)
            },
            0,
        )

        // ---- 3. pair ---------------------------------------------------------------
        col.addView(labelDivider(getString(R.string.or_label)))
        val pairApps = Codecs.apps.filter { app -> app.entries.any { e -> Codecs.byId(e.first)?.let { c -> c.androidCanSend && Translators.hasDirect(c, null, A.DEVICE, 0, A.CMD_PAIR) } == true } }
        var appIdx = pairApps.indexOfFirst { it.name.startsWith("Lamp Smart") }.coerceAtLeast(0)
        val appBtn = bigButton(if (pairApps.isEmpty()) "-" else getString(R.string.pair_app_label, pairApps[appIdx].name), null, false, 56) {}
        appBtn.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.pair_app_title)
                .setSingleChoiceItems(pairApps.map { it.name }.toTypedArray(), appIdx) { d, which ->
                    appIdx = which
                    appBtn.text = getString(R.string.pair_app_label, pairApps[which].name)
                    d.dismiss()
                }.show()
        }
        col.addTo(
            card {
                addView(sectionHeader(0, getString(R.string.m3_title), numberBadge(3)))
                addTo(hintText(getString(R.string.m3_desc), 16f), 12)
                addTo(appBtn, 12)
                addTo(bigButton(getString(R.string.pair), R.drawable.ic_bluetooth, true, 60) { if (pairApps.isNotEmpty()) withAdvertise { doPair(pairApps[appIdx]) } }, 12)
            },
            0,
        )

        // ---- found + save ----------------------------------------------------------
        col.addView(divider())
        candBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addTo(candBox, 0)

        nameEdit = EditText(this).apply { setText(getString(R.string.default_name)); hint = getString(R.string.name_hint); setSingleLine(); textSize = 19f }
        kindGroup = RadioGroup(this)
        kindGroup.addView(RadioButton(this).apply { id = 1; text = getString(R.string.kind_cct); textSize = 17f })
        kindGroup.addView(RadioButton(this).apply { id = 3; text = getString(R.string.kind_rgb); textSize = 17f })
        kindGroup.addView(RadioButton(this).apply { id = 2; text = getString(R.string.kind_onoff); textSize = 17f })
        kindGroup.check(1)
        col.addTo(
            card {
                addView(sectionHeader(R.drawable.ic_check, getString(R.string.save_title)))
                addTo(nameEdit, 12)
                addTo(kindGroup, 8)
                addTo(bigButton(getString(R.string.save_lamp), R.drawable.ic_check, true, 64) { save() }, 16)
            },
            8,
        )
    }

    override fun onPause() {
        stopScan()
        super.onPause()
    }

    private fun say(msg: String) {
        status.text = msg
        statusCard.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- listen

    @SuppressLint("MissingPermission")
    private fun toggleListen() {
        if (scanCb != null) {
            stopScan()
            return
        }
        withScan {
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val scanner = adapter?.bluetoothLeScanner
            if (adapter == null || !adapter.isEnabled || scanner == null) {
                say(getString(R.string.bt_turn_on))
                return@withScan
            }
            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    result.scanRecord?.bytes?.let { heard(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    say(getString(R.string.scan_failed_fmt, errorCode))
                    scanCb = null
                    listenBtn.text = getString(R.string.start_listening)
                }
            }
            scanCb = cb
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
            listenBtn.text = getString(R.string.stop_listening)
            say(getString(R.string.listening))
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val cb = scanCb ?: return
        scanCb = null
        try {
            (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: Exception) {
        }
        if (::listenBtn.isInitialized) listenBtn.text = getString(R.string.start_listening)
    }

    private fun heard(raw: ByteArray) {
        val found = Codecs.decodeAll(raw)
        if (found.isNotEmpty()) addLearned(found, getString(R.string.how_heard))
    }

    // ---------------------------------------------------------------- paste

    private fun decodeHex(text: String) {
        val bytes = hexToBytes(text)
        if (bytes == null) {
            say(getString(R.string.bad_hex))
            return
        }
        val found = Codecs.decodeAll(bytes)
        if (found.isEmpty()) {
            say(getString(R.string.none_recognised))
        } else {
            addLearned(found, getString(R.string.how_pasted))
            say(getString(R.string.recognised_fmt, found.size))
        }
    }

    // ---------------------------------------------------------------- pair

    private fun doPair(app: AppSpec) {
        val id = Random.nextLong(0xFF, 0xFFF6)
        val links = app.entries.mapNotNull { (codecId, params) ->
            val codec = Codecs.byId(codecId) ?: return@mapNotNull null
            if (!codec.androidCanSend) return@mapNotNull null
            val conf = codec.newConf(id, 1, params)
            Link(codecId, id, 1, 0, 0, conf.withSign, conf.prefix, true, conf.extra)
        }
        say(getString(R.string.pairing_sending))
        LampController.pair(this, links) { err ->
            if (err != null) {
                say(err)
            } else {
                links.forEach { add(Cand(it, getString(R.string.how_pairing), true)) }
                render()
                say(getString(R.string.pairing_sent))
            }
        }
    }

    // ---------------------------------------------------------------- candidates

    private fun addLearned(found: List<Learned>, how: String) {
        var changed = false
        for (l in found) {
            val link = Link(l.codec.codecId, l.conf.id, l.conf.index, l.conf.txCount, l.conf.seed, l.conf.withSign, l.conf.prefix, true, l.conf.extra, l.conf.appRestartCount)
            val note = getString(R.string.note_fmt, how, cmdName(l.cmd.cmd)) + if (!l.codec.androidCanSend) " — " + getString(if (l.codec.canSend) R.string.android_no else R.string.listen_only) else ""
            val existing = cands[link.key()]
            if (existing == null) {
                cands[link.key()] = Cand(link, note, l.codec.androidCanSend)
                changed = true
            } else {
                existing.link.txCount = link.txCount // keep counters in step with what the real controller sent
                existing.link.seed = link.seed
                if (existing.note != note) {
                    existing.note = note
                    changed = true
                }
            }
        }
        if (changed) render()
    }

    private fun add(c: Cand) {
        cands[c.link.key()] = c
    }

    private fun render() {
        candBox.removeAllViews()
        if (cands.isEmpty()) return
        candBox.addTo(text(getString(R.string.found), 22f, true), 0)
        for (c in cands.values) {
            val canSend = Codecs.byId(c.link.codecId)?.androidCanSend == true
            candBox.addTo(
                card {
                    val cb = CheckBox(context).apply {
                        text = c.link.describe(this@AddDeviceActivity) + "\n" + c.note
                        textSize = 16f
                        isChecked = c.checked && canSend
                        isEnabled = canSend
                        setOnCheckedChangeListener { _, v -> c.checked = v }
                    }
                    addView(cb)
                    if (canSend) addTo(bigButton(getString(R.string.blink), R.drawable.ic_bulb, false, 52) { withAdvertise { LampController.blink(this@AddDeviceActivity, c.link, null) } }, 8)
                },
                12,
            )
        }
        candBox.addView(divider())
    }

    private fun cmdName(cmd: Int): String = when (cmd) {
        0x10 -> getString(R.string.cmd_on)
        0x11 -> getString(R.string.cmd_off)
        0x12 -> getString(R.string.cmd_on2)
        0x13 -> getString(R.string.cmd_off2)
        0x21 -> getString(R.string.cmd_color)
        0x28 -> getString(R.string.cmd_pair)
        0x45 -> getString(R.string.cmd_unpair)
        0x41, 0x51 -> getString(R.string.cmd_timer)
        else -> getString(R.string.cmd_other_fmt, cmd)
    }

    // ---------------------------------------------------------------- save

    private fun save() {
        val selected = cands.values.filter { it.checked && Codecs.byId(it.link.codecId)?.androidCanSend == true }
        if (selected.isEmpty()) {
            say(getString(R.string.select_one))
            return
        }
        stopScan()
        val dev = LampDevice(Devices.newUid(), nameEdit.text.toString().ifBlank { getString(R.string.default_name) })
        dev.type = when (kindGroup.checkedRadioButtonId) { 1 -> LampDevice.TYPE_CWW; 3 -> LampDevice.TYPE_RGB; else -> LampDevice.TYPE_ONOFF }
        selected.forEach { dev.links.add(it.link) }
        Devices.add(this, dev)
        finish()
    }
}
