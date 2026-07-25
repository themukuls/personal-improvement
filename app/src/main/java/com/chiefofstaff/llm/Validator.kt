package com.chiefofstaff.llm

import com.chiefofstaff.core.AppLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * §6.5 — the Validator. Schema-checks every structured response, attempts exactly one repair, then
 * signals the caller to queue the capture for the 02:00 re-parse (§6.6). An invalid response never
 * crashes a ritual — [ValidationResult.NeedsReparse] is a normal, handled outcome.
 *
 * The schema format is intentionally light: a JSON object whose "required" array lists top-level
 * keys that must be present. Full JSON-Schema is overkill for ~18 fixed tasks and one user (§6.1).
 */
class Validator(private val json: Json = Json { ignoreUnknownKeys = true }) {

    sealed interface ValidationResult {
        data class Valid(val obj: JsonObject) : ValidationResult
        data class NeedsReparse(val reason: String) : ValidationResult
    }

    /** First-pass validation of a raw model response against [schemaJson] (may be null = free text). */
    fun validate(raw: String, schemaJson: String?): ValidationResult {
        if (schemaJson == null) {
            // Free-text task; wrap so callers have a uniform type.
            return ValidationResult.Valid(JsonObject(emptyMap()))
        }
        val obj = parse(raw) ?: return ValidationResult.NeedsReparse("not valid JSON")
        val missing = requiredKeys(schemaJson).filterNot { obj.containsKey(it) }
        return if (missing.isEmpty()) ValidationResult.Valid(obj)
        else ValidationResult.NeedsReparse("missing keys: $missing")
    }

    /** Builds the one-shot repair instruction appended as a follow-up user turn (§6.6). */
    fun repairInstruction(reason: String, schemaJson: String): String =
        "Your previous response was not valid for the required schema ($reason). " +
            "Return ONLY a single JSON object matching this schema, no prose:\n$schemaJson"

    private fun parse(raw: String): JsonObject? = runCatching {
        val trimmed = raw.trim().let { t ->
            // Tolerate a leading/trailing code fence the model sometimes adds.
            t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        json.parseToJsonElement(trimmed).jsonObject
    }.onFailure { AppLog.d("validator", "parse failed: ${it.message}") }.getOrNull()

    private fun requiredKeys(schemaJson: String): List<String> = runCatching {
        val schema = json.parseToJsonElement(schemaJson).jsonObject
        (schema["required"] as? kotlinx.serialization.json.JsonArray)
            ?.map { it.toString().trim('"') } ?: emptyList()
    }.getOrDefault(emptyList())
}
