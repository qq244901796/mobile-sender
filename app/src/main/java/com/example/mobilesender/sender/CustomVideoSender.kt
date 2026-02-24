package com.example.mobilesender.sender

import com.example.mobilesender.CastClient
import com.example.mobilesender.TvDevice

class CustomVideoSender : VideoSender {
    override fun send(device: TvDevice, videoUrl: String): Pair<Boolean, String> {
        return CastClient.sendCast(device, videoUrl)
    }
}
