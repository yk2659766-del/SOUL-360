package com.soul360.design.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProvider(val label: String) { OPENAI("ChatGPT"), GEMINI("Gemini") }

/**
 * App settings. API keys and the gateway token are encrypted with an Android Keystore AES key
 * and never leave this device except in requests to the provider they belong to.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---- AI render provider ----
    var provider: AiProvider
        get() = runCatching { AiProvider.valueOf(prefs.getString("provider", "OPENAI")!!) }.getOrDefault(AiProvider.OPENAI)
        set(v) { prefs.edit().putString("provider", v.name).apply() }

    var openaiModel: String
        get() = prefs.getString("openaiModel", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_OPENAI_MODEL
        set(v) { prefs.edit().putString("openaiModel", v.trim()).apply() }

    var geminiModel: String
        get() = prefs.getString("geminiModel", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        set(v) { prefs.edit().putString("geminiModel", v.trim()).apply() }

    fun apiKey(p: AiProvider): String? = readSecret(secretName(p))
    fun hasApiKey(p: AiProvider): Boolean = prefs.contains(secretName(p))
    fun saveApiKey(p: AiProvider, key: String) {
        val k = key.trim()
        require(k.length >= 10) { "That doesn't look like a valid API key." }
        saveSecret(secretName(p), k)
    }
    fun removeApiKey(p: AiProvider) = prefs.edit().remove(secretName(p)).apply()
    private fun secretName(p: AiProvider) = if (p == AiProvider.OPENAI) "openaiKey" else "geminiKey"

    // ---- Optional gateway (used for "Make 3D" with Meshy) ----
    val gatewayUrl: String get() = prefs.getString(KEY_URL, "") ?: ""
    val hasToken: Boolean get() = prefs.contains(KEY_TOKEN)

    fun saveGatewayUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) { prefs.edit().remove(KEY_URL).apply(); return }
        prefs.edit().putString(KEY_URL, validate(trimmed)).apply()
    }

    fun saveToken(token: String) {
        val t = token.trim()
        require(t.isNotEmpty()) { "Enter the app token configured on your gateway." }
        saveSecret(KEY_TOKEN, t)
    }
    fun token(): String? = readSecret(KEY_TOKEN)
    fun removeToken() { prefs.edit().remove(KEY_TOKEN).apply() }

    // ---- Encryption helpers ----
    private fun saveSecret(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    private fun readSecret(name: String): String? {
        val packed = prefs.getString(name, null) ?: return null
        return runCatching {
            val parts = packed.split(":")
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    companion object {
        const val DEFAULT_OPENAI_MODEL = "gpt-image-2"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash-image"
        private const val KEY_URL = "gatewayUrl"
        private const val KEY_TOKEN = "gatewayToken"
        private const val ALIAS = "soul360_gateway_token"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        fun validate(raw: String): String {
            val uri = Uri.parse(raw.trim())
            require(uri.scheme.equals("https", ignoreCase = true)) { "The gateway address must start with https://" }
            val host = uri.host
            require(!host.isNullOrEmpty()) { "Enter an origin such as https://your-gateway.example.com" }
            val path = uri.path ?: ""
            require((path.isEmpty() || path == "/") && uri.query == null && uri.fragment == null && uri.userInfo == null) {
                "Enter only the origin, e.g. https://your-gateway.example.com (no path)."
            }
            return if (uri.port > 0) "https://$host:${uri.port}" else "https://$host"
        }
    }
}
