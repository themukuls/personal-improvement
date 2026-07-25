package com.chiefofstaff.capture

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.data.entity.Observation
import com.chiefofstaff.data.model.CaptureSource
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.TaskId
import kotlinx.serialization.json.jsonPrimitive

/**
 * CAP-08 / DOM-03 — meal capture. One word (and, when wired to the camera, a photo) becomes a
 * rough calorie estimate via the cheap-tier ESTIMATE_MEAL task, recorded as an Observation. The
 * raw capture is stored first (P5), so even if the estimate fails nothing is lost; the photo path
 * is an optional hook for the camera surface.
 */
class MealEstimator(
    private val repo: LifeRepository,
    private val clock: Clock,
    private val orchestrator: LlmOrchestrator,
) {
    suspend fun estimate(description: String, photoPath: String? = null): Double? {
        val source = if (photoPath != null) CaptureSource.PHOTO else CaptureSource.TEXT
        repo.capture(source, "MEAL $description", photoPath)

        val result = orchestrator.run(TaskId.ESTIMATE_MEAL, params = mapOf("meal" to description))
        if (result !is LlmOrchestrator.TaskResult.Structured) {
            AppLog.i("meal", "estimate unavailable; capture retained")
            return null
        }
        val kcal = result.obj["calories"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        repo.graph.insertObservation(
            Observation(metric = "calories", value = kcal, unit = "kcal",
                observedAt = clock.now(), source = "meal_estimate", createdAt = clock.now())
        )
        return kcal
    }
}
