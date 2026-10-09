package com.example.omnispread.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TastyConfig(
    val clientSecret: String = "",
    val refreshToken: String = "",
    val sandbox: Boolean = false,
    val accountNumber: String = "",
) {
    val isConfigured get() = clientSecret.isNotBlank() && refreshToken.isNotBlank()
}

/**
 * Stores tastytrade OAuth credentials encrypted with an AES-GCM key held in the
 * Android Keystore. Secrets never leave the device except to tastytrade's OAuth endpoint.
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences("tastytrade", Context.MODE_PRIVATE)

    fun load(): TastyConfig = TastyConfig(
        clientSecret = decrypt(prefs.getString(K_SECRET, null)),
        refreshToken = decrypt(prefs.getString(K_REFRESH, null)),
        sandbox = prefs.getBoolean(K_SANDBOX, false),
        accountNumber = prefs.getString(K_ACCOUNT, "") ?: "",
    )

    fun save(cfg: TastyConfig) {
        prefs.edit()
            .putString(K_SECRET, encrypt(cfg.clientSecret.trim()))
            .putString(K_REFRESH, encrypt(cfg.refreshToken.trim()))
            .putBoolean(K_SANDBOX, cfg.sandbox)
            .putString(K_ACCOUNT, cfg.accountNumber)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (_: Exception) { "" }
    }

    private companion object {
        const val ALIAS = "omnispread_tastytrade"
        const val K_SECRET = "client_secret"
        const val K_REFRESH = "refresh_token"
        const val K_SANDBOX = "sandbox"
        const val K_ACCOUNT = "account_number"
    }
}
