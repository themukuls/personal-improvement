package com.chiefofstaff.llm

import com.chiefofstaff.data.model.ModelTier

/** How a provider accepts a required output schema (§6.5). */
enum class StructuredMode { NATIVE_SCHEMA, JSON_MODE, PROMPTED_ONLY }

/** Where the system prompt goes on the wire (§6.5). */
enum class SystemPromptStyle { SEPARATE_FIELD, FIRST_MESSAGE }

/**
 * Capability flags a provider advertises. The abstraction is at capability level, not HTTP level
 * (§6.5): the assembler and router read these flags rather than knowing any provider's wire format.
 */
data class ProviderCapabilities(
    val name: String,
    val structuredMode: StructuredMode,
    val supportsToolCalling: Boolean,
    val systemPromptStyle: SystemPromptStyle,
    val supportsPromptCaching: Boolean,
    val contextCeilingTokens: Int,
    /** Which tiers this provider can serve, mapped to concrete model ids. */
    val models: Map<ModelTier, String>,
    /** Rough output-token cost in paise (₹/100) for the cost-ceiling comparison. */
    val costPerKTokenPaise: Map<ModelTier, Int>,
    /**
     * True only for the offline stub: it must be tried *after* every real provider, never chosen for
     * being "free". The router sorts last-resort providers to the very end regardless of cost.
     */
    val lastResort: Boolean = false,
)

data class LlmMessage(val role: String, val content: String)

data class LlmRequest(
    val system: String,
    val messages: List<LlmMessage>,
    val tier: ModelTier,
    val temperature: Double,
    val maxTokens: Int,
    /** JSON schema the response must satisfy, or null for free text (conversation). */
    val schema: String? = null,
    /** A stable prefix the router can hint for prompt caching (§6.5). */
    val cacheablePrefix: String? = null,
)

data class LlmUsage(val inputTokens: Int, val outputTokens: Int)

data class LlmResponse(
    val text: String,
    val usage: LlmUsage,
    val providerName: String,
    val model: String,
    val stopReason: String? = null,
)

/** Thrown for transport/provider failures so the router can fall to a secondary (§6.6). */
class ProviderException(message: String, val retryable: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * A capability-level provider. Concrete adapters (Claude, OpenAI-compatible, on-device stub)
 * translate [LlmRequest] to their wire format and normalise the response back.
 */
interface LlmProvider {
    val capabilities: ProviderCapabilities
    /** True if the provider is currently reachable and within rate limits. */
    suspend fun available(): Boolean
    suspend fun complete(request: LlmRequest): LlmResponse
}
