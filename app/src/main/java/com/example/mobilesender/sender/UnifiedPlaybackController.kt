package com.example.mobilesender.sender

import com.example.mobilesender.TvDevice
import com.example.mobilesender.protocol.ProtocolType

class UnifiedPlaybackController(
    private val custom: PlaybackController = CustomPlaybackController(),
    private val dlna: PlaybackController = DlnaPlaybackController()
) {
    fun control(device: TvDevice, action: PlaybackAction): Pair<Boolean, String> {
        return when (device.protocol) {
            ProtocolType.CUSTOM -> custom.control(device, action)
            ProtocolType.DLNA -> dlna.control(device, action)
        }
    }
}
