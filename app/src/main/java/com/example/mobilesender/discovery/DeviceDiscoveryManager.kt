package com.example.mobilesender.discovery

import android.content.Context
import com.example.mobilesender.TvDevice
import com.example.mobilesender.TvDiscovery

class DeviceDiscoveryManager(
    context: Context,
    private val onFound: (TvDevice) -> Unit
) {
    private val customDiscovery = TvDiscovery(context) { onFound(it) }
    private val dlnaDiscovery = DlnaDiscovery(context) { onFound(it) }

    fun start() {
        customDiscovery.start()
        dlnaDiscovery.start()
    }

    fun stop() {
        customDiscovery.stop()
        dlnaDiscovery.stop()
    }
}
