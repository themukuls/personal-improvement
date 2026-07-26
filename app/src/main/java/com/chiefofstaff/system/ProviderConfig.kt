package com.chiefofstaff.system

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Provider API keys, held in the same Keystore-guarded encrypted store as the DB passphrase. Keys
 * are never compiled into the APK and never committed (see .gitignore). With no keys set the app
 * runs entirely on the deterministic paths + offline stub — the rituals still work (P11).
 *
 * §17 open decision #6 (primary/secondary provider) is a config choice here, not a code change:
 * set whichever keys you have and the Router picks among them by tier/cost/availability.
 */
class ProviderConfig(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cos_provider_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

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
}
