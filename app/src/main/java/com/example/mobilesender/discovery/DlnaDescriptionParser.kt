package com.example.mobilesender.discovery

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class DlnaDeviceInfo(
    val udn: String?,
    val friendlyName: String,
    val avTransportControlUrl: String?
)

object DlnaDescriptionParser {

    fun fetch(locationUrl: String): DlnaDeviceInfo? {
        return runCatching {
            val conn = URL(locationUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.inputStream.use { input ->
                parseXml(locationUrl, input)
            }
        }.getOrNull()
    }

    private fun parseXml(locationUrl: String, input: InputStream): DlnaDeviceInfo {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        var event = parser.eventType
        var friendlyName: String? = null
        var udn: String? = null
        var inService = false
        var serviceType: String? = null
        var controlUrl: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "service" -> {
                            inService = true
                            serviceType = null
                            controlUrl = null
                        }

                        "friendlyName" -> {
                            if (friendlyName == null) {
                                friendlyName = parser.nextText().trim()
                            }
                        }

                        "UDN" -> {
                            if (udn == null) {
                                udn = parser.nextText().trim()
                            }
                        }

                        "serviceType" -> {
                            if (inService) {
                                serviceType = parser.nextText().trim()
                            }
                        }

                        "controlURL" -> {
                            if (inService) {
                                controlUrl = parser.nextText().trim()
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "service") {
                        inService = false
                        if (serviceType?.contains("AVTransport", ignoreCase = true) == true) {
                            return DlnaDeviceInfo(
                                udn = udn,
                                friendlyName = friendlyName ?: "DLNA TV",
                                avTransportControlUrl = resolveUrl(locationUrl, controlUrl)
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }

        return DlnaDeviceInfo(
            udn = udn,
            friendlyName = friendlyName ?: "DLNA TV",
            avTransportControlUrl = null
        )
    }

    private fun resolveUrl(baseLocation: String, controlUrl: String?): String? {
        val raw = controlUrl?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        return runCatching {
            val base = URL(baseLocation)
            URI(base.protocol, null, base.host, base.port, raw, null, null).toString()
        }.getOrNull()
    }
}
