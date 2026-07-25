package com.chiefofstaff.capture

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.model.CaptureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The single entry point for every capture surface (CAP-01..04, CAP-18). Its one job is the P1/P5
 * invariant: the raw input is stored immutably and indexed *first*, and only then do we attempt
 * extraction. If extraction can't run (offline), the capture simply stays unparsed and the 02:00
 * batch drains it later (SYS-13) — nothing the user said is ever lost.
 */
class CaptureManager(
    private val repo: LifeRepository,
    private val pipeline: FactExtractionPipeline,
    private val scope: CoroutineScope,
    private val isOnline: () -> Boolean,
) {
    suspend fun captureText(text: String): Long = store(CaptureSource.TEXT, text)
    suspend fun captureVoice(transcript: String, audioPath: String? = null): Long =
        store(CaptureSource.VOICE, transcript, audioPath)
    suspend fun captureShared(text: String): Long = store(CaptureSource.SHARE, text)
    suspend fun captureNotification(text: String): Long = store(CaptureSource.NOTIFICATION, text)

    private suspend fun store(source: CaptureSource, raw: String, media: String? = null): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return -1
        val id = repo.capture(source, trimmed, media)
        AppLog.i("capture", "stored #$id from $source (${trimmed.length} chars)")
        // Fire-and-forget extraction; leave it for the batch if we're offline.
        if (isOnline()) {
            scope.launch { runCatching { pipeline.processCapture(id) } }
        }
        return id
    }
}
