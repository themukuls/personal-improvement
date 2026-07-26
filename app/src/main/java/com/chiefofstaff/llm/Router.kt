package com.chiefofstaff.llm

import com.chiefofstaff.core.AppLog
import kotlinx.coroutines.delay

/**
 * §6.5 — the Router. Selects a provider by tier, availability, cost ceiling and cache capability,
 * with a secondary on failure (§6.6). Ordered [providers]: primary first, offline stub last.
 * Rate-limit failures back off; retryable transport failures fall through to the next provider.
 */
class Router(private val providers: () -> List<LlmProvider>) {

    data class Selection(val provider: LlmProvider, val response: LlmResponse)

    /**
     * Run [request], preferring cache-capable providers when the request has a stable prefix, then
     * cheapest-for-tier. Falls to the next available provider on a retryable failure.
     */
    suspend fun route(request: LlmRequest): Selection {
        val ordered = providers()
            .sortedWith(
                // Real providers first; the offline stub is a genuine last resort (not "cheapest").
                compareBy<LlmProvider> { it.capabilities.lastResort }
                    .thenByDescending {
                        request.cacheablePrefix != null && it.capabilities.supportsPromptCaching
                    }
                    .thenBy {
                        it.capabilities.costPerKTokenPaise[request.tier] ?: Int.MAX_VALUE
                    }
            )

        var lastError: ProviderException? = null
        for (provider in ordered) {
            if (!provider.available()) continue
            var attempt = 0
            while (attempt < 2) {
                try {
                    val response = provider.complete(request)
                    return Selection(provider, response)
                } catch (e: ProviderException) {
                    lastError = e
                    AppLog.w("router", "${provider.capabilities.name} failed (retryable=${e.retryable})", e)
                    if (!e.retryable) break
                    attempt++
                    delay(backoffMillis(attempt))
                }
            }
        }
        throw lastError ?: ProviderException("no provider available", retryable = false)
    }

    private fun backoffMillis(attempt: Int): Long = (1_000L shl (attempt - 1)).coerceAtMost(8_000L)
}
