package com.chiefofstaff.llm.adapters

import com.chiefofstaff.data.model.ModelTier
import com.chiefofstaff.llm.LlmRequest
import com.chiefofstaff.llm.LlmResponse
import com.chiefofstaff.llm.LlmUsage
import com.chiefofstaff.llm.ProviderCapabilities
import com.chiefofstaff.llm.ProviderException
import com.chiefofstaff.llm.LlmProvider
import com.chiefofstaff.llm.StructuredMode
import com.chiefofstaff.llm.SystemPromptStyle
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Anthropic Messages API adapter. System prompt is a separate field; structured output is coaxed
 * via a prefilled assistant "{" turn plus the schema in the system prompt (PROMPTED strategy),
 * which the [com.chiefofstaff.llm.Validator] then schema-checks and repairs once.
 */
class ClaudeAdapter(
    private val http: HttpClient,
    private val apiKey: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LlmProvider {

    override val capabilities = ProviderCapabilities(
        name = "claude",
        structuredMode = StructuredMode.PROMPTED_ONLY,
        supportsToolCalling = true,
        systemPromptStyle = SystemPromptStyle.SEPARATE_FIELD,
        supportsPromptCaching = true,
        contextCeilingTokens = 200_000,
        models = mapOf(
            ModelTier.CHEAP to "claude-haiku-4-5-20251001",
            ModelTier.FLAGSHIP to "claude-opus-4-8",
        ),
        costPerKTokenPaise = mapOf(ModelTier.CHEAP to 8, ModelTier.FLAGSHIP to 130),
    )

    override suspend fun available(): Boolean = apiKey.isNotBlank()

    override suspend fun complete(request: LlmRequest): LlmResponse {
        val model = capabilities.models.getValue(request.tier)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("system", request.system)
            putJsonArray("messages") {
                request.messages.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == "assistant") "assistant" else "user")
                        put("content", m.content)
                    }
                }
                // Prefill an opening brace so the model emits JSON directly when a schema is set.
                if (request.schema != null) {
                    addJsonObject {
                        put("role", "assistant")
                        put("content", "{")
                    }
                }
            }
        }

        val resp: HttpResponse = try {
            http.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (t: Throwable) {
            throw ProviderException("claude transport failure", retryable = true, cause = t)
        }

        if (resp.status == HttpStatusCode.TooManyRequests) {
            throw ProviderException("claude rate limited", retryable = true)
        }
        if (!resp.status.isSuccessOrOverloaded()) {
            throw ProviderException("claude http ${resp.status.value}", retryable = resp.status.value >= 500)
        }

        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val textParts = root["content"]?.jsonArray.orEmptyArray()
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
        var text = textParts.joinToString("")
        if (request.schema != null && !text.trimStart().startsWith("{")) text = "{$text"

        val usage = root["usage"]?.jsonObject
        return LlmResponse(
            text = text,
            usage = LlmUsage(
                inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            ),
            providerName = capabilities.name,
            model = model,
            stopReason = root["stop_reason"]?.jsonPrimitive?.content,
        )
    }

    private fun HttpStatusCode.isSuccessOrOverloaded() = value in 200..299
    private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())
}
