package com.obdreader.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic (SPP / RFCOMM) link to an ELM327 adapter.
 * Permission checks are done by the caller before this class is used.
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(
    private val device: BluetoothDevice,
    private val adapter: BluetoothAdapter?,
) : StreamTransport() {

    @Volatile
    private var socket: BluetoothSocket? = null

    override val name: String = try {
        device.name ?: device.address
    } catch (e: SecurityException) {
        device.address
    }

    override fun open() {
        try {
            adapter?.cancelDiscovery()
        } catch (ignored: SecurityException) {
            // Needs BLUETOOTH_SCAN on Android 12+; discovery is rarely running, so ignore.
        }

        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (first: IOException) {
            // Many cheap ELM327 clones only accept a raw channel-1 socket.
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                (method.invoke(device, 1) as BluetoothSocket).also { it.connect() }
            } catch (second: Exception) {
                throw IOException("Couldn't open a Bluetooth link to $name. Is the adapter plugged in and the ignition on?", first)
            }
        }
        socket = s
        input = s.inputStream
        output = s.outputStream
    }

    override fun close() {
        val s = socket
        socket = null
        input = null
        output = null
        try {
            s?.close()
        } catch (ignored: IOException) {
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
