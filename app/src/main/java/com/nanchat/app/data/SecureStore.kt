package com.nanchat.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the Nano seed and chat session token in an encrypted on-device file. */
class SecureStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nanchat_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var seed: String?
        get() = prefs.getString(KEY_SEED, null)
        set(value) = prefs.edit().putString(KEY_SEED, value).apply()

    var chatToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var chatTokenExpiresAt: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRES, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRES, value).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_SEED = "seed"
        private const val KEY_TOKEN = "chat_token"
        private const val KEY_TOKEN_EXPIRES = "chat_token_expires_at"
    }
}
