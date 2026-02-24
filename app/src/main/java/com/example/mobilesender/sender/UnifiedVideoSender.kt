package com.example.mobilesender.sender

import com.example.mobilesender.TvDevice
import com.example.mobilesender.protocol.ProtocolType

class UnifiedVideoSender(
    private val customSender: VideoSender = CustomVideoSender(),
    private val dlnaSender: VideoSender = DlnaVideoSender()
) {
    fun send(device: TvDevice, videoUrl: String): Pair<Boolean, String> {
        return when (device.protocol) {
            ProtocolType.CUSTOM -> customSender.send(device, videoUrl)
            ProtocolType.DLNA -> dlnaSender.send(device, videoUrl)
        }
    }
}
