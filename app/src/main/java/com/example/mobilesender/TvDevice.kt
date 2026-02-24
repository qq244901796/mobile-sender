package com.example.mobilesender

import android.net.nsd.NsdServiceInfo

data class TvDevice(
    val name: String,
    val host: String,
    val port: Int,
    val raw: NsdServiceInfo? = null
)
