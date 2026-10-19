package com.obdreader.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bluetooth Low Energy ELM327 adapter (the "works with iPhone" kind).
 *
 * These adapters emulate a serial port with one GATT characteristic that we
 * write commands to and one that notifies us with replies. Vendors use
 * different UUIDs, so we look for any service that has both.
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
) : ObdTransport {

    override val name: String = try {
        device.name ?: device.address
    } catch (e: SecurityException) {
        device.address
    }

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var failure: String? = null
    @Volatile private var closed = false

    // Recreated for each connection attempt (see open()).
    @Volatile private var servicesReady = CountDownLatch(1)
    @Volatile private var descriptorWritten = CountDownLatch(1)
    private val writeAck = Semaphore(0)

    private val lock = ReentrantLock()
    private val dataArrived = lock.newCondition()
    private val rx = StringBuilder()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                if (failure == null) failure = "Bluetooth LE link to $name dropped (status $status)"
                wakeEveryone()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            servicesReady.countDown()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            descriptorWritten.countDown()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeAck.release()
        }

        @Deprecated("Used on Android 12 and below")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) c.value?.let { onData(it) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onData(value)
        }
    }

    private fun onData(bytes: ByteArray) = lock.withLock {
        for (b in bytes) rx.append((b.toInt() and 0xFF).toChar())
        dataArrived.signalAll()
    }

    private fun wakeEveryone() {
        servicesReady.countDown()
        descriptorWritten.countDown()
        writeAck.release()
        lock.withLock { dataArrived.signalAll() }
    }

    override fun open() {
        // Android's BLE stack often fails the first connection with a generic
        // error (status 133), especially right after a scan. Retry once.
        var attempt = 0
        while (true) {
            attempt++
            failure = null
            servicesReady = CountDownLatch(1)
            descriptorWritten = CountDownLatch(1)
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("Couldn't start a Bluetooth LE connection")
            if (servicesReady.await(15, TimeUnit.SECONDS) && failure == null) break

            val msg = failure ?: "Timed out connecting to $name over Bluetooth LE"
            releaseGatt()
            if (closed) throw IOException("Disconnected")
            if (attempt >= MAX_ATTEMPTS) {
                failure = msg
                throw IOException(msg)
            }
            Thread.sleep(RETRY_DELAY_MS)
        }

        val g = gatt ?: throw IOException(failure ?: "Disconnected")

        // Faster round-trips: noticeably quicker live data on STN-based adapters like the Veepeak BLE+.
        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

        val (notifyChar, wChar) = pickCharacteristics(g.services) ?: run {
            close()
            throw IOException("$name doesn't look like an OBD2 adapter (no serial service found). Try the Bluetooth Classic option.")
        }
        writeChar = wChar

        g.setCharacteristicNotification(notifyChar, true)
        notifyChar.getDescriptor(CCCD)?.let { cccd ->
            val value = if (notifyChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
            writeDescriptor(g, cccd, value)
            descriptorWritten.await(5, TimeUnit.SECONDS)
        }
        failure?.let { close(); throw IOException(it) }
    }

    override fun exchange(command: String, timeoutMs: Long): String {
        val g = gatt ?: throw IOException("Not connected")
        val c = writeChar ?: throw IOException("Not connected")
        failure?.let { throw IOException(it) }

        lock.withLock { rx.setLength(0) }
        writeAck.drainPermits()

        val bytes = (command + "\r").toByteArray(Charsets.US_ASCII)
        for (chunk in bytes.toList().chunked(MAX_CHUNK)) {
            if (!writeChunk(g, c, chunk.toByteArray())) throw IOException("Bluetooth LE write failed")
            if (!writeAck.tryAcquire(3, TimeUnit.SECONDS)) throw AdapterTimeoutException("Write to '$command' not acknowledged")
            failure?.let { throw IOException(it) }
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        lock.withLock {
            while (true) {
                failure?.let { throw IOException(it) }
                val idx = rx.indexOf(">")
                if (idx >= 0) {
                    val out = rx.substring(0, idx)
                    rx.delete(0, idx + 1)
                    return out
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) throw AdapterTimeoutException("No reply to '$command'")
                dataArrived.await(remaining, TimeUnit.MILLISECONDS)
            }
        }
    }

    override fun close() {
        closed = true
        if (failure == null) failure = "Disconnected"
        releaseGatt()
        wakeEveryone()
    }

    private fun releaseGatt() {
        val g = gatt
        gatt = null
        writeChar = null
        try {
            g?.disconnect()
            g?.close()
        } catch (ignored: SecurityException) {
        }
    }

    private fun writeChunk(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                c.writeType = type
                c.value = bytes
                g.writeCharacteristic(c)
            }
        }
    }

    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value)
        } else {
            @Suppress("DEPRECATION")
            run {
                d.value = value
                g.writeDescriptor(d)
            }
        }
    }

    private fun pickCharacteristics(
        services: List<BluetoothGattService>,
    ): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        fun canNotify(c: BluetoothGattCharacteristic) =
            (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

        fun canWrite(c: BluetoothGattCharacteristic) =
            (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0

        val candidates = services
            .filterNot { it.uuid in IGNORED_SERVICES }
            .sortedBy { s -> PREFERRED_SERVICES.indexOf(s.uuid).let { if (it < 0) Int.MAX_VALUE else it } }

        for (service in candidates) {
            val n = service.characteristics.firstOrNull { canNotify(it) }
            val w = service.characteristics.firstOrNull { canWrite(it) }
            if (n != null && w != null) return n to w
        }
        return null
    }

    companion object {
        private const val MAX_CHUNK = 20 // default BLE MTU payload
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 600L

        private fun uuid16(short: String): UUID = UUID.fromString("0000$short-0000-1000-8000-00805F9B34FB")

        private val CCCD = uuid16("2902")

        /** Serial-emulation services used by common BLE OBD2 adapters. */
        private val PREFERRED_SERVICES = listOf(
            uuid16("FFF0"),
            uuid16("FFE0"),
            uuid16("18F0"),
            UUID.fromString("E7810A71-73AE-499D-8C15-FAA9AEF0C3F2"),
        )

        /** Standard services that are never the data channel. */
        private val IGNORED_SERVICES = setOf(uuid16("1800"), uuid16("1801"), uuid16("180A"), uuid16("180F"))
    }
}
