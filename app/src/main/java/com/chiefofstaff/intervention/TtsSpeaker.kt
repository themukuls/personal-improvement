package com.chiefofstaff.intervention

import android.content.Context
import android.speech.tts.TextToSpeech
import com.chiefofstaff.core.AppLog
import java.util.Locale

/**
 * INT-01 — the spoken brief. Android TTS (§5). Kept deliberately thin: the morning brief must speak
 * even when everything remote is down, so this depends on nothing but the platform engine.
 */
class TtsSpeaker(context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) engine?.language = Locale.getDefault()
            else AppLog.w("tts", "engine init failed ($status)")
        }
    }

    suspend fun awaitReady(timeoutMs: Long = 3_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!ready && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
        }
        return ready
    }

    fun speak(text: String) {
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cos-brief")
    }

    fun shutdown() { engine?.shutdown(); engine = null }
}
