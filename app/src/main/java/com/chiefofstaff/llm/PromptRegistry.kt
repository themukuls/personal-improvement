package com.chiefofstaff.llm

import android.content.Context
import com.chiefofstaff.core.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * §6.4 Component 3 — the prompt registry. One JSON file per task version lives in app storage
 * (filesDir/prompts), seeded from bundled assets and syncable from an external folder. Prompts
 * are never compiled into the APK: needing a rebuild to fix a phrasing issue means you stop
 * tuning (§6.4). Every capture records the prompt_version that parsed it (§6.4) for A/B on
 * identical historical input (SYS-18).
 */
@Serializable
data class PromptSpec(
    val id: String,                 // e.g. "generate_plan@1"
    val version: String,
    val system: String,
    val userTemplate: String,       // "{{context}}" and task-specific placeholders
    val schema: String? = null,     // JSON schema string, when the task is structured
    val fewShot: List<FewShot> = emptyList(),
    val temperature: Double? = null,
) {
    @Serializable data class FewShot(val input: String, val output: String)
}

class PromptRegistry private constructor(
    private val dir: File,
    private val json: Json,
) {
    private val cache = mutableMapOf<String, PromptSpec>()

    fun get(ref: String): PromptSpec? {
        cache[ref]?.let { return it }
        val file = File(dir, "${ref.replace('@', '_')}.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<PromptSpec>(file.readText()) }
            .onFailure { AppLog.e("prompt", "failed to parse $ref", it) }
            .getOrNull()
            ?.also { cache[ref] = it }
    }

    /** Renders the final user message: few-shot examples, then the template with context spliced. */
    fun renderUser(spec: PromptSpec, context: String, extras: Map<String, String> = emptyMap()): String {
        var body = spec.userTemplate.replace("{{context}}", context)
        extras.forEach { (k, v) -> body = body.replace("{{$k}}", v) }
        return body
    }

    companion object {
        /**
         * Seeds filesDir/prompts from assets/prompts on first run (or when an asset is newer),
         * so the on-disk copy the user can tune always exists.
         */
        fun create(context: Context): PromptRegistry {
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
            val dir = File(context.filesDir, "prompts").apply { mkdirs() }
            runCatching {
                context.assets.list("prompts")?.forEach { name ->
                    val out = File(dir, name)
                    if (!out.exists()) {
                        context.assets.open("prompts/$name").use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            }.onFailure { AppLog.w("prompt", "asset seed skipped", it) }
            return PromptRegistry(dir, json)
        }
    }
}
