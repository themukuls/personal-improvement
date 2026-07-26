package com.chiefofstaff.llm.adapters

import com.chiefofstaff.data.model.ModelTier
import com.chiefofstaff.llm.LlmProvider
import com.chiefofstaff.llm.LlmRequest
import com.chiefofstaff.llm.LlmResponse
import com.chiefofstaff.llm.LlmUsage
import com.chiefofstaff.llm.ProviderCapabilities
import com.chiefofstaff.llm.StructuredMode
import com.chiefofstaff.llm.SystemPromptStyle

/**
 * A local, no-network provider. It never produces intelligence — it returns a minimal valid shape
 * so the app runs end-to-end with no API key configured, and so integration tests are deterministic.
 * In production it is only ever the *last* provider the router tries; the deterministic fallback
 * pipeline (§6.6, [com.chiefofstaff.llm.Fallback]) is what actually keeps rituals alive offline.
 */
class OfflineStubProvider : LlmProvider {
    override val capabilities = ProviderCapabilities(
        name = "offline-stub",
        structuredMode = StructuredMode.PROMPTED_ONLY,
        supportsToolCalling = false,
        systemPromptStyle = SystemPromptStyle.FIRST_MESSAGE,
        supportsPromptCaching = false,
        contextCeilingTokens = 8_000,
        models = mapOf(ModelTier.CHEAP to "stub", ModelTier.FLAGSHIP to "stub"),
        costPerKTokenPaise = mapOf(ModelTier.CHEAP to 0, ModelTier.FLAGSHIP to 0),
        lastResort = true,   // only ever used after every real provider is unavailable/failing
    )

    override suspend fun available(): Boolean = true

    override suspend fun complete(request: LlmRequest): LlmResponse {
        val text = if (request.schema != null) "{\"_offline\":true,\"items\":[]}" else
            "I'm offline right now, so I can't reason over your context — but nothing you captured is lost."
        return LlmResponse(text, LlmUsage(0, 0), capabilities.name, "stub", "offline")
    }
}
