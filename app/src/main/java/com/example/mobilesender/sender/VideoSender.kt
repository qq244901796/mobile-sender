package com.example.mobilesender.sender

import com.example.mobilesender.TvDevice

interface VideoSender {
    fun send(device: TvDevice, videoUrl: String): Pair<Boolean, String>
}
