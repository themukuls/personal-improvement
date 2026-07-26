package com.chiefofstaff.system

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chiefofstaff.core.AppLog

/**
 * Provider API keys, held in the same Keystore-guarded encrypted store as the DB passphrase. Keys
 * are never compiled into the APK and never committed (see .gitignore). With no keys set the app
 * runs entirely on the deterministic paths + offline stub — the rituals still work (P11).
 *
 * §17 open decision #6 (primary/secondary provider) is a config choice here, not a code change:
 * set whichever keys you have and the Router picks among them by tier/cost/availability.
 */
class ProviderConfig(context: Context) {
    private val prefs = openEncrypted(context)

    var claudeKey: String
        get() = prefs.getString("claude_api_key", "").orEmpty()
        set(v) { prefs.edit().putString("claude_api_key", v).apply() }

    var openAiKey: String
        get() = prefs.getString("openai_api_key", "").orEmpty()
        set(v) { prefs.edit().putString("openai_api_key", v).apply() }

    var grokKey: String
        get() = prefs.getString("grok_api_key", "").orEmpty()
        set(v) { prefs.edit().putString("grok_api_key", v).apply() }

    /** Groq (groq.com) — an OpenAI-compatible endpoint serving fast open models (Llama etc.). */
    var groqKey: String
        get() = prefs.getString("groq_api_key", "").orEmpty()
        set(v) { prefs.edit().putString("groq_api_key", v).apply() }

    private companion object {
        private const val FILE = "cos_provider_keys"

        /**
         * Open the Keystore-encrypted key store, tolerant of a bad state. The encrypted file is
         * excluded from Auto Backup, but if it ever can't be decrypted (a restored file whose
         * Keystore key didn't come with it, or an invalidated key), we wipe and recreate rather than
         * crash — the user just re-enters their API keys.
         */
        fun openEncrypted(context: Context): SharedPreferences {
            fun build(): SharedPreferences = EncryptedSharedPreferences.create(
                context,
                FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return try {
                build()
            } catch (e: Exception) {
                AppLog.w("provider", "encrypted key store unreadable; resetting it", e)
                context.deleteSharedPreferences(FILE)
                build()
            }
        }
    }
}
