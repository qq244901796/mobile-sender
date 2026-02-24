package com.example.mobilesender

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD

class FileShareServer(
    private val context: Context,
    private var videoUri: Uri
) : NanoHTTPD(PORT) {

    fun updateUri(uri: Uri) {
        videoUri = uri
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET || session.uri != "/video") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }

        return try {
            val stream = context.contentResolver.openInputStream(videoUri)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "file not found")
            newChunkedResponse(Response.Status.OK, "video/*", stream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "error")
        }
    }

    companion object {
        const val PORT = 18080
    }
}
