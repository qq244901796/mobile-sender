package com.example.mobilesender

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.mobilesender.protocol.ProtocolType

class TvDiscovery(
    context: Context,
    private val onResolved: (TvDevice) -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        stop()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val id = "custom-${resolved.serviceName}-${host}:${resolved.port}"
                        val device = TvDevice(
                            id = id,
                            name = resolved.serviceName ?: "Custom TV",
                            host = host,
                            port = resolved.port,
                            protocol = ProtocolType.CUSTOM,
                            raw = resolved
                        )
                        onResolved(device)
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        discoveryListener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
        }
        discoveryListener = null
    }

    companion object {
        const val SERVICE_TYPE = "_screencast._tcp."
    }
}
