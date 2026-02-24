package com.example.mobilesender

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CastClient {
    fun sendCast(tv: TvDevice, videoUrl: String): Pair<Boolean, String> {
        return try {
            val conn = URL("http://${tv.host}:${tv.port}/cast").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json")

            val payload = JSONObject().put("url", videoUrl).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            }
            (code in 200..299) to "HTTP $code: $body"
        } catch (e: Exception) {
            false to (e.message ?: "send failed")
        }
    }
}
