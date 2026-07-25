package com.chiefofstaff.capture

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.ReviewQueueItem
import com.chiefofstaff.data.model.ReviewReason
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * MEM-02 — the fact extraction pipeline. Deterministic rules run first (§4.1); whatever they can't
 * resolve goes to the cheap-tier EXTRACT_FACTS task. Facts land in typed tables via [FactWriter];
 * low-confidence or unparseable captures go to the review queue (MEM-05) rather than being dropped
 * or guessed — wrong is fine, unfixable is not (P12). The prompt_version that parsed each capture
 * is recorded so the 02:00 job can re-parse with improved prompts (MEM-14).
 */
class FactExtractionPipeline(
    private val repo: LifeRepository,
    private val orchestrator: LlmOrchestrator,
    private val factWriter: FactWriter,
    private val clock: Clock,
) {
    /** Process one capture by id. Safe to call inline after capture or from the batch worker. */
    suspend fun processCapture(captureId: Long) {
        val capture = repo.captures.byId(captureId) ?: return
        if (capture.parsed) return

        val result = orchestrator.run(
            TaskId.EXTRACT_FACTS,
            params = mapOf("raw" to "RAW_CAPTURE\n${capture.rawContent}"),
        )

        when (result) {
            is LlmOrchestrator.TaskResult.Structured -> {
                val facts = runCatching { result.obj["facts"]?.jsonArray }.getOrNull().orEmpty()
                var written = 0
                var lowConfidence = 0
                for (element in facts) {
                    val obj = element.jsonObject
                    if (factWriter.write(obj, sourceCaptureId = captureId)) written++ else lowConfidence++
                }
                repo.captures.markParsed(captureId, result.promptVersion ?: "?", confidence = 1f)
                if (written == 0 && lowConfidence > 0) {
                    enqueueReview(captureId, ReviewReason.LOW_CONFIDENCE, "$lowConfidence low-confidence facts")
                }
                AppLog.i("extract", "capture $captureId → $written facts (+$lowConfidence held)")
            }
            is LlmOrchestrator.TaskResult.Failed -> {
                // Never lose the capture: leave it unparsed and queue it for the nightly re-parse.
                enqueueReview(captureId, ReviewReason.PARSE_FAILED, result.reason)
                AppLog.w("extract", "capture $captureId parse failed: ${result.reason}")
            }
            is LlmOrchestrator.TaskResult.Text -> Unit // extraction is always structured
        }
    }

    /** Drain every unparsed capture (called by the 02:00 batch and after offline queue flush). */
    suspend fun processBacklog() {
        repo.captures.unparsed().forEach { processCapture(it.id) }
    }

    private suspend fun enqueueReview(captureId: Long, reason: ReviewReason, detail: String?) {
        repo.captures.enqueueReview(
            ReviewQueueItem(captureId = captureId, reason = reason, detail = detail, createdAt = clock.now())
        )
    }

    private fun kotlinx.serialization.json.JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> =
        this ?: emptyList()
}
