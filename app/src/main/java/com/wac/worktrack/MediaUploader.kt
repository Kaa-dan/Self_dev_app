package com.wac.worktrack

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/** Minimal multipart/form-data uploader (no third-party deps) for the learning server. */
object MediaUploader {

    /** Uploads [uri] as a "file" part plus a "device" field. Returns true on HTTP 2xx. */
    fun upload(context: Context, serverUrl: String, uri: Uri, fileName: String, device: String): Boolean {
        val boundary = "----worktrack${System.currentTimeMillis()}"
        val url = URL(serverUrl.trimEnd('/') + "/upload")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            conn.outputStream.use { out ->
                // device field
                out.write(("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"device\"\r\n\r\n" +
                    "$device\r\n").toByteArray())
                // file header
                out.write(("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n").toByteArray())
                // file bytes
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.copyTo(out)
                } ?: return false
                out.write("\r\n--$boundary--\r\n".toByteArray())
                out.flush()
            }
            return conn.responseCode in 200..299
        } catch (_: Exception) {
            return false
        } finally {
            conn.disconnect()
        }
    }
}
