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

/**
 * AI gateway settings. The URL is stored in plain preferences; the app token is
 * encrypted with an Android Keystore AES key. Provider API keys never go in the app.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val gatewayUrl: String get() = prefs.getString(KEY_URL, "") ?: ""
    val hasToken: Boolean get() = prefs.contains(KEY_TOKEN)
    val isAiConfigured: Boolean get() = gatewayUrl.isNotEmpty() && hasToken

    /** Validates and stores an HTTPS origin. Empty input clears it. */
    fun saveGatewayUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) { prefs.edit().remove(KEY_URL).apply(); return }
        prefs.edit().putString(KEY_URL, validate(trimmed)).apply()
    }

    fun saveToken(token: String) {
        val t = token.trim()
        require(t.isNotEmpty()) { "Enter the app token configured on your gateway." }
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = cipher.doFinal(t.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(KEY_TOKEN, packed).apply()
    }

    fun token(): String? {
        val packed = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            val (iv, data) = packed.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
    }

    fun removeToken() { prefs.edit().remove(KEY_TOKEN).apply() }

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
        private const val KEY_URL = "gatewayUrl"
        private const val KEY_TOKEN = "gatewayToken"
        private const val ALIAS = "soul360_gateway_token"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        /** Returns a normalised https origin (no path, query or credentials) or throws. */
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
