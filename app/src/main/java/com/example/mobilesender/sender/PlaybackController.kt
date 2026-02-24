package com.example.mobilesender.sender

import com.example.mobilesender.TvDevice

interface PlaybackController {
    fun control(device: TvDevice, action: PlaybackAction): Pair<Boolean, String>
}
