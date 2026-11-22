package com.obdreader.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.obdreader.app.MainActivity.Companion.BLE_SCAN_MS
import com.obdreader.app.MainActivity.Companion.DEFAULT_HOST
import com.obdreader.app.MainActivity.Companion.DEFAULT_PORT
import com.obdreader.app.MainActivity.Companion.PREF_HOST
import com.obdreader.app.MainActivity.Companion.PREF_LAST_ADDRESS
import com.obdreader.app.MainActivity.Companion.PREF_LAST_NAME
import com.obdreader.app.MainActivity.Companion.PREF_LAST_TYPE
import com.obdreader.app.MainActivity.Companion.PREF_PORT
import com.obdreader.app.MainActivity.Companion.TYPE_BLE
import com.obdreader.app.MainActivity.Companion.TYPE_CLASSIC
import com.obdreader.app.MainActivity.Companion.TYPE_WIFI
import com.obdreader.app.MainActivity.Found
import com.obdreader.app.obd.BleTransport
import com.obdreader.app.obd.BluetoothTransport
import com.obdreader.app.obd.WifiTransport
import com.obdreader.app.session.LogRecorder
import java.util.Locale
import kotlinx.coroutines.delay

// Choosing and connecting an adapter.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Choosing an adapter ----------

internal fun MainActivity.showConnectSheet() {
    val sheet = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.sheet_connect, null)
    view.applyTitleFonts()

    fun bind(id: Int, icon: Int, title: Int, subtitle: Int, onPick: () -> Unit) {
        val card = view.findViewById<View>(id)
        card.findViewById<ImageView>(R.id.optionIcon).setImageResource(icon)
        card.findViewById<TextView>(R.id.optionTitle).setText(title)
        card.findViewById<TextView>(R.id.optionSubtitle).setText(subtitle)
        card.setOnClickListener {
            sheet.dismiss()
            onPick()
        }
    }
    val lastType = prefs.getString(PREF_LAST_TYPE, null)
    val lastName = prefs.getString(PREF_LAST_NAME, null)
    if (lastType != null && lastName != null) {
        val (icon, typeLabel) = when (lastType) {
            TYPE_WIFI -> R.drawable.ic_wifi to getString(R.string.opt_wifi_title)
            else -> R.drawable.ic_bluetooth to getString(R.string.opt_bluetooth_title)
        }
        val card = view.findViewById<View>(R.id.optionLast)
        view.findViewById<View>(R.id.lastLabel).visibility = View.VISIBLE
        view.findViewById<View>(R.id.otherLabel).visibility = View.VISIBLE
        card.visibility = View.VISIBLE
        card.findViewById<ImageView>(R.id.optionIcon).setImageResource(icon)
        card.findViewById<TextView>(R.id.optionTitle).text = getString(R.string.reconnect_to, lastName)
        card.findViewById<TextView>(R.id.optionSubtitle).text = typeLabel
        card.setOnClickListener {
            sheet.dismiss()
            reconnectLast()
        }
    }
    bind(R.id.optionBluetooth, R.drawable.ic_bluetooth, R.string.opt_bluetooth_title, R.string.opt_bluetooth_sub) { connectBluetooth() }
    bind(R.id.optionWifi, R.drawable.ic_wifi, R.string.opt_wifi_title, R.string.opt_wifi_sub) { connectWifi() }

    sheet.setContentView(view)
    sheet.show()
}
internal fun MainActivity.withPermissions(permissions: Array<String>, action: () -> Unit) {
    val missing = permissions.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }
    if (missing.isEmpty()) {
        action()
    } else {
        afterPermissions = action
        permissionLauncher.launch(missing.toTypedArray())
    }
}
@SuppressLint("MissingPermission")
internal fun MainActivity.withBluetoothOn(action: (BluetoothAdapter) -> Unit) {
    val adapter = bluetoothAdapter ?: return toast("This phone has no Bluetooth")
    if (adapter.isEnabled) {
        action(adapter)
    } else {
        afterBluetoothOn = action
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}
internal fun MainActivity.rememberLast(type: String, address: String?, name: String?) {
    prefs.edit()
        .putString(PREF_LAST_TYPE, type)
        .putString(PREF_LAST_ADDRESS, address)
        .putString(PREF_LAST_NAME, name ?: address ?: "adapter")
        .apply()
}
@SuppressLint("MissingPermission")
internal fun MainActivity.reconnectLast() {
    val type = prefs.getString(PREF_LAST_TYPE, null) ?: return
    if (type == TYPE_WIFI) {
        val host = prefs.getString(PREF_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        startSession(WifiTransport(applicationContext, host, prefs.getInt(PREF_PORT, DEFAULT_PORT)))
        return
    }
    val address = prefs.getString(PREF_LAST_ADDRESS, null) ?: return
    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }
    withPermissions(perms) {
        withBluetoothOn { adapter ->
            val device = try {
                adapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                toast("The saved adapter address is invalid. Choose it again.")
                return@withBluetoothOn
            }
            startSession(if (type == TYPE_BLE) BleTransport(applicationContext, device) else BluetoothTransport(device, adapter))
        }
    }
}
/**
 * One Bluetooth option for every adapter: the permissions both kinds need, then a single
 * list of paired and nearby adapters, whichever kind of Bluetooth they use.
 */
internal fun MainActivity.connectBluetooth() {
    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    withPermissions(perms) { withBluetoothOn { showBluetoothPicker(it) } }
}
/**
 * Records a drive. The first time, asks whether to save the route too: location goes only into
 * this drive's file (for a map in other software and the outline on the drive card).
 */
internal fun MainActivity.startRecording() {
    if (com.obdreader.app.session.LocationFeed.permitted(this)) { beginRecording(withLocation = true); return }
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.route_title)
        .setMessage(R.string.route_message)
        .setPositiveButton(R.string.route_yes) { _, _ ->
            routePermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        .setNegativeButton(R.string.route_no) { _, _ -> beginRecording(withLocation = false) }
        .show()
}
internal fun MainActivity.beginRecording(withLocation: Boolean) {
    LogRecorder.start(this, withLocation)
    val r = LogRecorder.active.value
    if (withLocation && r?.withLocation == false) toast("Location is off on this phone, so the route isn't being saved")
}
internal fun MainActivity.connectWifi() {
    val form = layoutInflater.inflate(R.layout.dialog_wifi, null)
    val hostInput = form.findViewById<TextInputEditText>(R.id.hostInput)
    val portInput = form.findViewById<TextInputEditText>(R.id.portInput)
    val portLayout = form.findViewById<TextInputLayout>(R.id.portLayout)
    hostInput.setText(prefs.getString(PREF_HOST, DEFAULT_HOST))
    portInput.setText(prefs.getInt(PREF_PORT, DEFAULT_PORT).toString())

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Wi-Fi adapter")
        .setMessage("Join the adapter's Wi-Fi network first (often WiFi_OBDII or V-LINK). If Android says it has no internet, choose to stay connected.")
        .setView(form)
        .setPositiveButton("Connect", null)
        .setNeutralButton("Wi-Fi settings") { _, _ -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        .setNegativeButton("Cancel", null)
        .show()

    // Validate before closing the dialog.
    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
        val host = hostInput.text?.toString()?.trim().orEmpty()
        val port = portInput.text?.toString()?.trim()?.toIntOrNull()
        if (host.isEmpty()) {
            form.findViewById<TextInputLayout>(R.id.hostLayout).error = "Enter the adapter's IP address"
            return@setOnClickListener
        }
        if (port == null || port !in 1..65535) {
            portLayout.error = "Enter a port between 1 and 65535"
            return@setOnClickListener
        }
        prefs.edit().putString(PREF_HOST, host).putInt(PREF_PORT, port).apply()
        dialog.dismiss()
        startSession(WifiTransport(applicationContext, host, port)) { rememberLast(TYPE_WIFI, null, "$host:$port") }
    }
}
/**
 * Lists paired adapters straight away, then adds nearby ones as they're found: Bluetooth LE
 * adapters (the "iPhone-compatible" kind) from an LE scan, and unpaired Bluetooth Classic
 * adapters from a normal search. Tapping one connects the right way for its kind, pairing a
 * Classic adapter first if it needs it. People only ever see names.
 */
@SuppressLint("MissingPermission")
internal fun MainActivity.showBluetoothPicker(adapter: BluetoothAdapter) {
    val found = mutableListOf<Found>()
    val labels = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
    var searching = true

    fun relabel() {
        val seen = mutableListOf<String>()
        labels.setNotifyOnChange(false)
        labels.clear()
        found.forEach { labels.add(uniqueLabel(it.name, seen)) }
        labels.notifyDataSetChanged()
    }
    fun add(device: BluetoothDevice, rawName: String?, ble: Boolean, paired: Boolean) {
        val name = rawName?.trim()?.takeIf { it.isNotEmpty() } ?: return // nameless devices are never adapters
        val existing = found.firstOrNull { it.device.address == device.address }
        if (existing != null) return
        val item = Found(device, name, ble, paired)
        // Likely adapters first, then paired devices, then everything else in the order found.
        val index = when {
            looksLikeObd(name) -> found.indexOfFirst { !looksLikeObd(it.name) }.let { if (it < 0) found.size else it }
            paired -> found.indexOfFirst { !looksLikeObd(it.name) && !it.paired }.let { if (it < 0) found.size else it }
            else -> found.size
        }
        found.add(index, item)
        relabel()
    }

    // Paired devices: an LE-only device talks over LE, everything else over Classic.
    adapter.bondedDevices.orEmpty().forEach { d ->
        add(d, safeName(d), ble = d.type == BluetoothDevice.DEVICE_TYPE_LE, paired = true)
    }

    // Nearby Bluetooth LE adapters.
    val scanner = adapter.bluetoothLeScanner
    val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = result.scanRecord?.deviceName ?: safeName(d)
            runOnUiThread { add(d, name, ble = true, paired = false) }
        }
    }
    // Nearby unpaired Bluetooth Classic adapters.
    val classicReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_FOUND) return
            val d = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            if (d.type == BluetoothDevice.DEVICE_TYPE_LE) return // the LE scan covers these
            val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: safeName(d)
            add(d, name, ble = false, paired = d.bondState == BluetoothDevice.BOND_BONDED)
        }
    }

    fun stopSearching() {
        if (!searching) return
        searching = false
        try { scanner?.stopScan(leCallback) } catch (ignored: Exception) {}
        try { adapter.cancelDiscovery() } catch (ignored: Exception) {}
        try { unregisterReceiver(classicReceiver) } catch (ignored: Exception) {}
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.searching_adapters))
        .setAdapter(labels) { _, which ->
            stopSearching()
            connectFound(adapter, found[which])
        }
        .setNegativeButton("Cancel", null)
        .setOnDismissListener { stopSearching() }
        .show()

    try {
        ContextCompat.registerReceiver(this, classicReceiver, android.content.IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        adapter.startDiscovery()
        scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), leCallback)
    } catch (e: SecurityException) {
        stopSearching()
        if (found.isEmpty()) { dialog.dismiss(); toast("Bluetooth permission was refused"); return }
    }

    lifecycleScope.launch {
        delay(BLE_SCAN_MS)
        stopSearching()
        if (dialog.isShowing) {
            dialog.setTitle(if (found.isEmpty()) getString(R.string.no_adapters_found) else getString(R.string.choose_adapter))
        }
    }
}
/** Connects to a device from the Bluetooth list, pairing a Classic adapter first if it isn't paired yet. */
@SuppressLint("MissingPermission")
internal fun MainActivity.connectFound(adapter: BluetoothAdapter, f: Found) {
    val d = f.device
    if (f.ble) {
        startSession(BleTransport(applicationContext, d)) { rememberLast(TYPE_BLE, d.address, f.name) }
        return
    }
    if (d.bondState == BluetoothDevice.BOND_BONDED) {
        startSession(BluetoothTransport(d, adapter)) { rememberLast(TYPE_CLASSIC, d.address, f.name) }
        return
    }
    // Not paired: Android asks for the PIN (usually 1234 or 0000), then the app connects.
    val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            if (dev?.address != d.address) return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                BluetoothDevice.BOND_BONDED -> {
                    try { unregisterReceiver(this) } catch (ignored: Exception) {}
                    startSession(BluetoothTransport(d, adapter)) { rememberLast(TYPE_CLASSIC, d.address, f.name) }
                }
                BluetoothDevice.BOND_NONE -> {
                    try { unregisterReceiver(this) } catch (ignored: Exception) {}
                    toast("Pairing with ${f.name} didn't finish. The PIN is usually 1234 or 0000.")
                }
            }
        }
    }
    ContextCompat.registerReceiver(this, receiver, android.content.IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    if (!d.createBond()) {
        try { unregisterReceiver(receiver) } catch (ignored: Exception) {}
        // Some adapters refuse to pair from an app; a direct connection may still work.
        startSession(BluetoothTransport(d, adapter)) { rememberLast(TYPE_CLASSIC, d.address, f.name) }
    } else {
        toast("Pairing with ${f.name}. If asked for a PIN, try 1234 or 0000.")
    }
}
/** The device's name, numbered if another one in the list has the same name ("OBDII", "OBDII (2)"). */
internal fun MainActivity.uniqueLabel(name: String, seen: MutableList<String>): String {
    val n = seen.count { it == name }
    seen.add(name)
    return if (n == 0) name else "$name (${n + 1})"
}
@SuppressLint("MissingPermission")
internal fun MainActivity.safeName(device: BluetoothDevice): String? =
    try { device.name } catch (e: SecurityException) { null }
internal fun MainActivity.looksLikeObd(name: String?): Boolean {
    val n = name?.uppercase(Locale.US) ?: return false
    return listOf("OBD", "ELM", "VLINK", "V-LINK", "VGATE", "ICAR", "KONNWEI", "VEEPEAK", "CAN").any { n.contains(it) }
}
