package com.chiefofstaff.llm

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import kotlinx.serialization.json.JsonObject

/**
 * Ties the four components (§6) into one call: assemble context (§6.3) → render prompt (§6.4) →
 * route to a provider (§6.5) → validate + one repair (§6.5). Redaction (§6.8) runs on the
 * assembled context before it ever reaches a provider. Token/cost is accumulated for the daily
 * dashboard (SYS-17).
 *
 * Every result carries the prompt_version used, so a capture can record it (§6.4) and the nightly
 * job can re-run the same input against a newer prompt (SYS-18).
 */
class LlmOrchestrator(
    private val assembler: ContextAssembler,
    private val prompts: PromptRegistry,
    private val router: Router,
    private val validator: Validator,
    private val clock: Clock,
    private val onUsage: (TaskId, LlmUsage, providerName: String) -> Unit = { _, _, _ -> },
) {
    sealed interface TaskResult {
        val promptVersion: String?
        data class Structured(val obj: JsonObject, val rawText: String, override val promptVersion: String?) : TaskResult
        data class Text(val text: String, override val promptVersion: String?) : TaskResult
        data class Failed(val reason: String, override val promptVersion: String?) : TaskResult
    }

    suspend fun run(
        taskId: TaskId,
        params: Map<String, Any?> = emptyMap(),
        extraUserContent: String? = null,
    ): TaskResult {
        val task = Tasks[taskId]
        val spec = prompts.get(task.promptRef)
            ?: return TaskResult.Failed("no prompt for ${task.promptRef}", null)

        val assembled = assembler.assemble(task, params)
        val safeContext = Redaction.redact(assembled.text)          // §6.8 privacy boundary

        val schema = task.schemaRef?.let { spec.schema }
        val userContent = buildString {
            append(prompts.renderUser(spec, safeContext))
            extraUserContent?.let { append("\n\n").append(it) }
        }

        val messages = buildList {
            spec.fewShot.forEach {
                add(LlmMessage("user", it.input))
                add(LlmMessage("assistant", it.output))
            }
            add(LlmMessage("user", userContent))
        }

        val request = LlmRequest(
            system = spec.system,
            messages = messages,
            tier = task.tier,
            temperature = spec.temperature ?: task.temperature,
            maxTokens = task.maxOutputTokens,
            schema = schema,
            cacheablePrefix = if (task.cache == CachePolicy.PREFIX) spec.system else null,
        )

        val selection = try {
            router.route(request)
        } catch (e: ProviderException) {
            AppLog.w("llm", "routing failed for $taskId", e)
            return TaskResult.Failed("all providers unavailable", spec.version)
        }
        onUsage(taskId, selection.response.usage, selection.provider.capabilities.name)

        // Free-text task (conversation): no schema, return text directly.
        if (schema == null) {
            return TaskResult.Text(selection.response.text, spec.version)
        }

        when (val v = validator.validate(selection.response.text, schema)) {
            is Validator.ValidationResult.Valid ->
                return TaskResult.Structured(v.obj, selection.response.text, spec.version)

            is Validator.ValidationResult.NeedsReparse -> {
                if (!task.retry.repairOnce) return TaskResult.Failed(v.reason, spec.version)
                // One repair attempt (§6.5).
                val repairMessages = messages + listOf(
                    LlmMessage("assistant", selection.response.text),
                    LlmMessage("user", validator.repairInstruction(v.reason, schema)),
                )
                val repaired = try {
                    router.route(request.copy(messages = repairMessages))
                } catch (e: ProviderException) {
                    return TaskResult.Failed("repair routing failed", spec.version)
                }
                onUsage(taskId, repaired.response.usage, repaired.provider.capabilities.name)
                return when (val v2 = validator.validate(repaired.response.text, schema)) {
                    is Validator.ValidationResult.Valid ->
                        TaskResult.Structured(v2.obj, repaired.response.text, spec.version)
                    is Validator.ValidationResult.NeedsReparse ->
                        // Give up gracefully; caller queues the capture for the 02:00 re-parse.
                        TaskResult.Failed("needs reparse: ${v2.reason}", spec.version)
                }
            }
        }
    }
}
