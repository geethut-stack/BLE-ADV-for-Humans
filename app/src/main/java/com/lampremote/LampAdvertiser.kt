package com.lampremote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.lampremote.core.BleAdv

/**
 * Broadcasts advertisements built by the codecs, one after the other (legacy advertising,
 * ~100 ms interval). Supported AD types: 0x03 (16-bit UUID list), 0x16 (service data), 0xFF (manufacturer data).
 * Singleton so the app, widgets and shortcuts share one queue. Main thread only.
 */
@SuppressLint("MissingPermission") // callers check hasPermission() first
class LampAdvertiser private constructor(context: Context) {

    companion object {
        /** How long each packet is on air. The stock apps use ~500 ms. */
        const val ADV_DURATION_MS = 500L
        const val GAP_MS = 40L

        @Volatile private var instance: LampAdvertiser? = null

        fun get(context: Context): LampAdvertiser =
            instance ?: synchronized(this) {
                instance ?: LampAdvertiser(context.applicationContext).also { instance = it }
            }

        /** BLUETOOTH_ADVERTISE is a runtime permission on Android 12+, a normal one before. */
        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

        private fun uuid16(lo: Byte, hi: Byte): ParcelUuid {
            val v = (lo.toInt() and 0xFF) or ((hi.toInt() and 0xFF) shl 8)
            return ParcelUuid.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", v))
        }

        private fun uuid32(b: ByteArray, i: Int): ParcelUuid {
            var v = 0L
            for (k in 3 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
            return ParcelUuid.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", v))
        }

        private fun uuid128(b: ByteArray, i: Int): ParcelUuid {
            val be = ByteArray(16) { b[i + 15 - it] } // advertised little endian -> big endian
            val bb = java.nio.ByteBuffer.wrap(be)
            return ParcelUuid(java.util.UUID(bb.long, bb.long))
        }

        /** Adds one AD structure to the builder; false when Android's public API can't express it. */
        private fun addStructure(b: AdvertiseData.Builder, type: Int, raw: ByteArray): Boolean {
            when (type) {
                0x03 -> {
                    if (raw.isEmpty() || raw.size % 2 != 0) return false
                    for (i in raw.indices step 2) b.addServiceUuid(uuid16(raw[i], raw[i + 1]))
                }
                0x05 -> {
                    if (raw.isEmpty() || raw.size % 4 != 0) return false
                    for (i in raw.indices step 4) b.addServiceUuid(uuid32(raw, i))
                }
                0x07 -> {
                    if (raw.isEmpty() || raw.size % 16 != 0) return false
                    for (i in raw.indices step 16) b.addServiceUuid(uuid128(raw, i))
                }
                0x16 -> {
                    if (raw.size < 2) return false
                    b.addServiceData(uuid16(raw[0], raw[1]), raw.copyOfRange(2, raw.size))
                }
                0xFF -> {
                    if (raw.size < 2) return false
                    b.addManufacturerData((raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8), raw.copyOfRange(2, raw.size))
                }
                else -> return false
            }
            return true
        }

        /** Maps a codec advertisement to the closest thing Android's public API can build, or null. */
        fun buildData(adv: BleAdv): AdvertiseData? {
            val b = AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
            if (!addStructure(b, adv.type, adv.raw)) return null
            val second = adv.secondRaw
            if (adv.secondType != 0 && second != null && !addStructure(b, adv.secondType, second)) return null
            return b.build()
        }
    }

    private class Item(val adv: BleAdv, val holdMs: Long)

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Item>()
    private val doneCallbacks = ArrayList<(String?) -> Unit>()
    private var busy = false
    private var lastError: String? = null
    private var activeCallback: AdvertiseCallback? = null

    /** Progress / error text for the visible screen (main thread). */
    var onStatus: ((String) -> Unit)? = null

    private val adapter get() = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    /** Queue [advs]; [onDone] gets null on success or an error message once everything has been sent. */
    fun enqueue(advs: List<BleAdv>, msg: String, holdMs: Long = ADV_DURATION_MS, onDone: ((String?) -> Unit)? = null) {
        val adapter = adapter
        val problem = when {
            adapter == null -> appContext.getString(R.string.err_no_bt)
            !adapter.isEnabled -> appContext.getString(R.string.err_bt_off)
            adapter.bluetoothLeAdvertiser == null -> appContext.getString(R.string.err_no_adv)
            else -> null
        }
        if (problem != null) {
            onStatus?.invoke(problem)
            onDone?.invoke(problem)
            return
        }
        if (onDone != null) doneCallbacks.add(onDone)
        advs.forEach { queue.addLast(Item(it, holdMs)) }
        onStatus?.invoke(msg)
        if (!busy) next()
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        queue.clear()
        stopCurrent()
        finishAll()
    }

    private fun finishAll() {
        busy = false
        val err = lastError
        lastError = null
        onStatus?.invoke(err ?: appContext.getString(R.string.st_done))
        val cbs = ArrayList(doneCallbacks)
        doneCallbacks.clear()
        cbs.forEach { it(err) }
    }

    private fun next() {
        val item = queue.removeFirstOrNull()
        if (item == null) {
            finishAll()
            return
        }
        busy = true
        start(item, connectable = item.adv.adFlag != null)
    }

    private fun start(item: Item, connectable: Boolean) {
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            lastError = appContext.getString(R.string.err_adv_unavailable)
            queue.clear()
            finishAll()
            return
        }
        val data = buildData(item.adv)
        if (data == null) {
            lastError = appContext.getString(R.string.err_unsupported_fmt, item.adv.type)
            handler.post { next() }
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // ~100 ms interval
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(connectable)
            .setTimeout(0)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                handler.postDelayed({
                    stopCurrent()
                    handler.postDelayed({ next() }, GAP_MS)
                }, item.holdMs)
            }

            override fun onStartFailure(errorCode: Int) {
                activeCallback = null
                if (connectable && errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                    // Android adds 3 bytes of flags to connectable adverts; retry without them.
                    start(item, connectable = false)
                    return
                }
                lastError = appContext.getString(R.string.err_adv_failed_fmt, errorCode)
                handler.postDelayed({ next() }, GAP_MS)
            }
        }
        activeCallback = cb
        try {
            advertiser.startAdvertising(settings, data, cb)
        } catch (e: Exception) {
            activeCallback = null
            lastError = appContext.getString(R.string.err_generic_fmt, e.message ?: "")
            queue.clear()
            finishAll()
        }
    }

    private fun stopCurrent() {
        val cb = activeCallback ?: return
        activeCallback = null
        try { adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) } catch (_: Exception) {}
    }
}
