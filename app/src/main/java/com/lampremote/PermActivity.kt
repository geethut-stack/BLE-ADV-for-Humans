package com.lampremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Base activity: run a block once the needed Bluetooth runtime permissions are granted. */
abstract class PermActivity : AppCompatActivity() {

    private var pendingBlock: (() -> Unit)? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val block = pendingBlock
            pendingBlock = null
            if (result.values.all { it }) block?.invoke()
            else Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_LONG).show()
        }

    protected fun withPermissions(perms: Array<String>, block: () -> Unit) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            block()
        } else {
            pendingBlock = block
            permLauncher.launch(missing.toTypedArray())
        }
    }

    /** Needed to transmit (Android 12+). */
    protected fun withAdvertise(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) withPermissions(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE), block) else block()
    }

    /** Needed to listen (BLE scan: Android 12+ scan permission, older versions need location). */
    protected fun withScan(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) withPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN), block)
        else withPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), block)
    }
}
