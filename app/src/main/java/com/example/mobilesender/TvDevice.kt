package com.example.mobilesender

import android.net.nsd.NsdServiceInfo
import com.example.mobilesender.protocol.ProtocolType

data class TvDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: ProtocolType,
    val locationUrl: String? = null,
    val avTransportControlUrl: String? = null,
    val raw: NsdServiceInfo? = null
)
