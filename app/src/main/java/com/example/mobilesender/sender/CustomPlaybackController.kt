package com.example.mobilesender.sender

import com.example.mobilesender.CastClient
import com.example.mobilesender.TvDevice

class CustomPlaybackController : PlaybackController {
    override fun control(device: TvDevice, action: PlaybackAction): Pair<Boolean, String> {
        return CastClient.sendControl(device, action.wire)
    }
}
