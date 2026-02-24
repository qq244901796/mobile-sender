package com.example.mobilesender.sender

import com.example.mobilesender.TvDevice
import com.example.mobilesender.discovery.DlnaDescriptionParser
import java.net.HttpURLConnection
import java.net.URL

class DlnaVideoSender : VideoSender {

    override fun send(device: TvDevice, videoUrl: String): Pair<Boolean, String> {
        val controlUrl = resolveControlUrl(device)
            ?: return false to "DLNA 控制地址缺失"

        val setUriOk = callSoap(
            controlUrl = controlUrl,
            action = "SetAVTransportURI",
            body = """
                <u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">
                    <InstanceID>0</InstanceID>
                    <CurrentURI>${escapeXml(videoUrl)}</CurrentURI>
                    <CurrentURIMetaData></CurrentURIMetaData>
                </u:SetAVTransportURI>
            """.trimIndent()
        )
        if (!setUriOk.first) return setUriOk

        return callSoap(
            controlUrl = controlUrl,
            action = "Play",
            body = """
                <u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">
                    <InstanceID>0</InstanceID>
                    <Speed>1</Speed>
                </u:Play>
            """.trimIndent()
        )
    }

    private fun resolveControlUrl(device: TvDevice): String? {
        if (!device.avTransportControlUrl.isNullOrBlank()) return device.avTransportControlUrl
        val location = device.locationUrl ?: return null
        return DlnaDescriptionParser.fetch(location)?.avTransportControlUrl
    }

    private fun callSoap(controlUrl: String, action: String, body: String): Pair<Boolean, String> {
        return try {
            val conn = URL(controlUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")

            val envelope = """
                <?xml version=\"1.0\" encoding=\"utf-8\"?>
                <s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">
                    <s:Body>
                        $body
                    </s:Body>
                </s:Envelope>
            """.trimIndent()

            conn.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                true to "DLNA $action ok"
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                false to "DLNA $action failed HTTP $code $err"
            }
        } catch (e: Exception) {
            false to "DLNA $action error: ${e.message}"
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
