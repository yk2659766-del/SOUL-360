package com.soul360.design.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the SOUL 360 AI gateway in backend/. The gateway holds the OpenAI / Meshy keys;
 * the app only sends its app token. Stopping polling does NOT cancel provider charges.
 */
class GatewayClient(private val baseUrl: String, private val token: String) {

    enum class Kind(val path: String) {
        RENDER("render"),   // room photo -> photorealistic image (PNG)
        MODEL("model"),     // furniture image -> 3D model (GLB)
    }

    class GatewayException(message: String) : IOException(message)

    private fun open(path: String, method: String): HttpURLConnection {
        val conn = URL("$baseUrl/$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        return conn
    }

    private fun HttpURLConnection.readOrThrow(): ByteArray {
        val code = responseCode
        if (code !in 200..299) {
            val body = errorStream?.use { it.readBytes().decodeToString().take(400) } ?: ""
            val msg = runCatching { JSONObject(body).getString("error") }.getOrDefault(body)
            throw GatewayException("Gateway error $code: $msg")
        }
        return inputStream.use { it.readBytes() }
    }

    suspend fun start(kind: Kind, png: ByteArray, prompt: String?): String = withContext(Dispatchers.IO) {
        val conn = open("v1/${kind.path}", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        val body = JSONObject().put("image_base64", Base64.encodeToString(png, Base64.NO_WRAP))
        if (prompt != null) body.put("prompt", prompt)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        JSONObject(conn.readOrThrow().decodeToString()).getString("job_id")
    }

    data class Status(val status: String, val progress: Int?, val error: String?)

    suspend fun status(jobId: String): Status = withContext(Dispatchers.IO) {
        val o = JSONObject(open("v1/jobs/$jobId", "GET").readOrThrow().decodeToString())
        Status(o.getString("status"), if (o.has("progress") && !o.isNull("progress")) o.getInt("progress") else null,
            if (o.isNull("error")) null else o.optString("error"))
    }

    suspend fun result(jobId: String): ByteArray = withContext(Dispatchers.IO) {
        open("v1/jobs/$jobId/result", "GET").readOrThrow()
    }

    /** Uploads, polls every 3 s, and returns the result bytes. */
    suspend fun run(kind: Kind, png: ByteArray, prompt: String?, onUpdate: (String) -> Unit): ByteArray {
        onUpdate("Uploading…")
        val id = start(kind, png, prompt)
        val deadline = System.currentTimeMillis() + 20 * 60 * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(3000)
            val s = status(id)
            when (s.status) {
                "succeeded" -> { onUpdate("Downloading result…"); return result(id) }
                "failed" -> throw GatewayException(s.error ?: "The provider reported a failure.")
                "queued" -> onUpdate("Queued…")
                else -> onUpdate(if (s.progress != null) "Generating… ${s.progress}%" else "Generating…")
            }
        }
        throw GatewayException("The job took too long. Check your provider dashboard before retrying.")
    }
}
