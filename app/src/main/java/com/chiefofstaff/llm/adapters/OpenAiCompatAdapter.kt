package com.chiefofstaff.llm.adapters

import com.chiefofstaff.data.model.ModelTier
import com.chiefofstaff.llm.LlmProvider
import com.chiefofstaff.llm.LlmRequest
import com.chiefofstaff.llm.LlmResponse
import com.chiefofstaff.llm.LlmUsage
import com.chiefofstaff.llm.ProviderCapabilities
import com.chiefofstaff.llm.ProviderException
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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Adapter for OpenAI-compatible chat-completions endpoints — covers GPT and Grok (§5, §6.5). The
 * system prompt goes in the first message; JSON is requested via response_format json_object where
 * supported. Only the wire format differs from Claude; the router treats them identically.
 */
class OpenAiCompatAdapter(
    private val http: HttpClient,
    private val apiKey: String,
    private val baseUrl: String,
    private val providerName: String,
    private val cheapModel: String,
    private val flagshipModel: String,
    supportsJsonMode: Boolean = true,
    private val contextCeiling: Int = 128_000,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LlmProvider {

    override val capabilities = ProviderCapabilities(
        name = providerName,
        structuredMode = if (supportsJsonMode) StructuredMode.JSON_MODE else StructuredMode.PROMPTED_ONLY,
        supportsToolCalling = true,
        systemPromptStyle = SystemPromptStyle.FIRST_MESSAGE,
        supportsPromptCaching = false,
        contextCeilingTokens = contextCeiling,
        models = mapOf(ModelTier.CHEAP to cheapModel, ModelTier.FLAGSHIP to flagshipModel),
        costPerKTokenPaise = mapOf(ModelTier.CHEAP to 10, ModelTier.FLAGSHIP to 150),
    )

    override suspend fun available(): Boolean = apiKey.isNotBlank()

    override suspend fun complete(request: LlmRequest): LlmResponse {
        val model = capabilities.models.getValue(request.tier)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", request.system) }
                request.messages.forEach { m ->
                    addJsonObject { put("role", m.role); put("content", m.content) }
                }
            }
            if (request.schema != null && capabilities.structuredMode == StructuredMode.JSON_MODE) {
                putJsonObject("response_format") { put("type", "json_object") }
            }
        }

        val resp: HttpResponse = try {
            http.post("$baseUrl/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (t: Throwable) {
            throw ProviderException("$providerName transport failure", retryable = true, cause = t)
        }

        if (resp.status == HttpStatusCode.TooManyRequests) {
            throw ProviderException("$providerName rate limited", retryable = true)
        }
        if (resp.status.value !in 200..299) {
            // Surface the provider's actual error body (e.g. "model_decommissioned") so failures are
            // diagnosable instead of silently falling back to the offline stub.
            val detail = runCatching { resp.bodyAsText() }.getOrDefault("").take(300)
            throw ProviderException("$providerName http ${resp.status.value}: $detail", retryable = resp.status.value >= 500)
        }

        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val text = root["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw ProviderException("$providerName empty completion", retryable = true)
        val usage = root["usage"]?.jsonObject
        return LlmResponse(
            text = text,
            usage = LlmUsage(
                inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            ),
            providerName = providerName,
            model = model,
            stopReason = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finish_reason")?.jsonPrimitive?.content,
        )
    }
}
