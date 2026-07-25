package com.chiefofstaff.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.chiefofstaff.core.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CAP-01 hold-to-talk, on-device path. The spec's STT is hybrid (§5, §16.7): on-device recognition
 * for short commands, an API for long, accented, unstructured 90-second rambles where on-device
 * degrades badly. This controller owns the on-device leg; the long-form leg records audio and hands
 * the file to a transcription task in the batch. Retained audio means a transcription failure is
 * retryable, never a lost thought.
 */
class SpeechCaptureController(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data object Listening : State
        data class Partial(val text: String) : State
        data class Final(val text: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** CNV-13 — whether we're in a continuous, hands-free session (auto-restart after each utterance). */
    private val _continuous = MutableStateFlow(false)
    val continuous: StateFlow<Boolean> = _continuous

    private var recognizer: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Single hold-to-talk capture (CAP-01). */
    fun start() = startInternal()

    /**
     * CNV-13 — begin a continuous hands-free session: after each recognised utterance the recogniser
     * restarts itself, so the user can dictate several thoughts in a row without touching the button.
     * Recoverable errors (a pause, no match) simply restart; a hard failure ends the session.
     */
    fun startContinuous() {
        _continuous.value = true
        startInternal()
    }

    private fun startInternal() {
        if (!available()) {
            _state.value = State.Error("On-device recognition unavailable")
            return
        }
        recognizer?.run { runCatching { destroy() } }
        val r = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = State.Listening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {
                partial.firstResult()?.let { _state.value = State.Partial(it) }
            }
            override fun onResults(results: Bundle?) {
                val text = results.firstResult().orEmpty()
                _state.value = if (text.isBlank()) State.Idle else State.Final(text)
                if (_continuous.value) restartSoon()
            }
            override fun onError(error: Int) {
                AppLog.w("speech", "recognizer error $error")
                // In a session, a pause or no-match is expected — keep the session alive.
                val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (_continuous.value && recoverable) restartSoon()
                else _state.value = State.Error("Didn't catch that")
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { r.startListening(intent) }
            .onFailure { _state.value = State.Error("Could not start listening") }
    }

    /** Recreate the recogniser for the next utterance in a continuous session. */
    private fun restartSoon() {
        if (!_continuous.value) return
        recognizer?.run { runCatching { destroy() } }
        recognizer = null
        startInternal()
    }

    fun stop() {
        _continuous.value = false
        recognizer?.run { stopListening(); destroy() }
        recognizer = null
        if (_state.value is State.Listening) _state.value = State.Idle
    }

    fun reset() { _state.value = State.Idle }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
