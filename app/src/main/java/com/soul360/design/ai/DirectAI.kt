package com.soul360.design.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** An image sent to the AI: bytes + MIME type (image/jpeg or image/png). */
class AiImage(val bytes: ByteArray, val mime: String)

class AiException(message: String) : IOException(message)

/**
 * Calls ChatGPT (OpenAI GPT Image) or Gemini image editing directly with the user's own API key.
 * The first image is the composed room; the rest are reference images of the furniture pieces.
 */
object DirectAI {

    suspend fun openAiEdit(apiKey: String, model: String, images: List<AiImage>, prompt: String): ByteArray =
        withContext(Dispatchers.IO) {
            val boundary = "----soul360" + UUID.randomUUID().toString().replace("-", "")
            val body = ByteArrayOutputStream()
            fun field(name: String, value: String) {
                body.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", model)
            field("prompt", prompt)
            field("n", "1")
            images.forEachIndexed { i, img ->
                val ext = if (img.mime == "image/png") "png" else "jpg"
                body.write(("--$boundary\r\nContent-Disposition: form-data; name=\"image[]\"; filename=\"image$i.$ext\"\r\n" +
                    "Content-Type: ${img.mime}\r\n\r\n").toByteArray())
                body.write(img.bytes)
                body.write("\r\n".toByteArray())
            }
            body.write("--$boundary--\r\n".toByteArray())

            val conn = (URL("https://api.openai.com/v1/images/edits").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val text = readResponse(conn, "ChatGPT")
            val data = JSONObject(text).optJSONArray("data")
            val b64 = data?.optJSONObject(0)?.optString("b64_json").orEmpty()
            if (b64.isEmpty()) throw AiException("ChatGPT returned no image.")
            Base64.decode(b64, Base64.DEFAULT)
        }

    suspend fun geminiEdit(apiKey: String, model: String, images: List<AiImage>, prompt: String): ByteArray =
        withContext(Dispatchers.IO) {
            val parts = JSONArray()
            images.forEach { img ->
                parts.put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", img.mime)
                    .put("data", Base64.encodeToString(img.bytes, Base64.NO_WRAP))))
            }
            parts.put(JSONObject().put("text", prompt))
            val req = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(req.toString().toByteArray()) }
            val o = JSONObject(readResponse(conn, "Gemini"))
            val candidates = o.optJSONArray("candidates")
            val texts = StringBuilder()
            if (candidates != null) {
                for (c in 0 until candidates.length()) {
                    val ps = candidates.optJSONObject(c)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until ps.length()) {
                        val p = ps.optJSONObject(i) ?: continue
                        val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data")
                        val d = inline?.optString("data").orEmpty()
                        if (d.isNotEmpty()) return@withContext Base64.decode(d, Base64.DEFAULT)
                        p.optString("text").takeIf { it.isNotBlank() }?.let { texts.append(it).append(' ') }
                    }
                }
            }
            val block = o.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            val finish = candidates?.optJSONObject(0)?.optString("finishReason").orEmpty()
            throw AiException(buildString {
                append("Gemini returned no image")
                if (block.isNotEmpty()) append(" (blocked: $block)")
                else if (finish.isNotEmpty() && finish != "STOP") append(" ($finish)")
                if (texts.isNotBlank()) append(": ").append(texts.toString().trim().take(300))
                append(".")
            })
        }

    private fun readResponse(conn: HttpURLConnection, who: String): String {
        val code = conn.responseCode
        if (code in 200..299) return conn.inputStream.use { it.readBytes().decodeToString() }
        val err = conn.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
        val msg = runCatching {
            val e = JSONObject(err).opt("error")
            if (e is JSONObject) e.optString("message") else e?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: err.take(300)
        val hint = when (code) {
            401, 403 -> " Check your $who API key in Settings."
            429 -> " You may be out of credits or sending too fast."
            404 -> " Check the model name in Settings."
            else -> ""
        }
        throw AiException("$who error $code: $msg$hint")
    }
}
