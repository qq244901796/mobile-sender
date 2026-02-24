package com.example.mobilesender.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.example.mobilesender.TvDevice
import com.example.mobilesender.protocol.ProtocolType
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URL
import kotlin.concurrent.thread

class DlnaDiscovery(
    private val context: Context,
    private val onFound: (TvDevice) -> Unit
) {

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var socket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        stop()
        running = true

        worker = thread(name = "dlna-discovery") {
            val seenLocations = mutableSetOf<String>()
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifiManager.createMulticastLock("dlna-discovery-lock").apply {
                    setReferenceCounted(false)
                    acquire()
                }

                socket = MulticastSocket().apply {
                    soTimeout = 1000
                    reuseAddress = true
                }

                repeat(3) {
                    sendSearch(socket!!)
                    receiveResponses(socket!!, seenLocations, 2000)
                }
            } catch (_: Exception) {
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        multicastLock?.let { runCatching { if (it.isHeld) it.release() } }
        multicastLock = null
        worker = null
    }

    private fun sendSearch(sock: MulticastSocket) {
        val request = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n")
            append("\r\n")
        }

        val data = request.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(
            data,
            data.size,
            InetAddress.getByName("239.255.255.250"),
            1900
        )
        sock.send(packet)
    }

    private fun receiveResponses(sock: MulticastSocket, seenLocations: MutableSet<String>, windowMs: Long) {
        val start = System.currentTimeMillis()
        val buf = ByteArray(8192)
        while (running && System.currentTimeMillis() - start < windowMs) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                val raw = String(packet.data, 0, packet.length)
                val headers = parseHeaders(raw)
                val st = headers["st"].orEmpty()
                val usn = headers["usn"].orEmpty()
                val location = headers["location"].orEmpty()

                if (location.isBlank()) continue
                if (!st.contains("MediaRenderer", ignoreCase = true) && !usn.contains("MediaRenderer", ignoreCase = true)) {
                    continue
                }
                if (!seenLocations.add(location)) continue

                val desc = DlnaDescriptionParser.fetch(location) ?: continue
                val parsed = runCatching { URL(location) }.getOrNull() ?: continue
                val host = parsed.host ?: continue
                val port = if (parsed.port > 0) parsed.port else parsed.defaultPort.takeIf { it > 0 } ?: 80
                val id = desc.udn ?: "dlna-$host:$port"

                onFound(
                    TvDevice(
                        id = id,
                        name = desc.friendlyName,
                        host = host,
                        port = port,
                        protocol = ProtocolType.DLNA,
                        locationUrl = location,
                        avTransportControlUrl = desc.avTransportControlUrl
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun parseHeaders(response: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        response.replace("\r\n", "\n").split("\n").forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                map[key] = value
            }
        }
        return map
    }
}
