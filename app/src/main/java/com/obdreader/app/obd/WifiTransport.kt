package com.obdreader.app.obd

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Wi-Fi ELM327 adapter over TCP (typically 192.168.0.10:35000).
 *
 * The adapter's hotspot has no internet, so Android often keeps routing
 * traffic over mobile data. To avoid that, the socket is opened through the
 * Wi-Fi network's own socket factory.
 */
class WifiTransport(
    private val context: Context,
    private val host: String,
    private val port: Int,
) : StreamTransport() {

    @Volatile
    private var socket: Socket? = null

    override val name: String = "Wi-Fi adapter ($host:$port)"

    override fun open() {
        val s = wifiNetwork()?.socketFactory?.createSocket() ?: Socket()
        socket = s // so close() (Disconnect) can abort a slow connect
        try {
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.tcpNoDelay = true
            s.keepAlive = true
        } catch (e: IOException) {
            try { s.close() } catch (ignored: IOException) {}
            throw IOException(
                "Couldn't reach $host:$port. Join the adapter's Wi-Fi network first, and check the address in its manual.",
                e,
            )
        }
        if (socket !== s) { // Disconnect was tapped while connecting
            try { s.close() } catch (ignored: IOException) {}
            throw IOException("Disconnected")
        }
        input = s.getInputStream()
        output = s.getOutputStream()
    }

    @Suppress("DEPRECATION")
    private fun wifiNetwork(): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return try {
            cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (ignored: SecurityException) {
            null
        }
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
        private const val CONNECT_TIMEOUT_MS = 6000
    }
}
