package com.chiefofstaff.llm

/**
 * §6.3 Component 2 — the context assembler. Each Task names a recipe of ordered blocks with
 * individual token budgets; each block is rendered by a [ContextProvider] running SQL and
 * emitting compact key-value lines (never JSON — its punctuation wastes ~30% of the budget).
 *
 * Rules enforced here:
 *  - structured queries first; keyword retrieval only for free-form history,
 *  - rank by recency and salience, not similarity,
 *  - hard per-block budget, oldest content truncated first.
 */
interface ContextProvider {
    /** Stable key referenced by [RecipeBlock.providerKey]. */
    val key: String
    /** Render at most [budgetTokens] worth of compact context, or "" if nothing is relevant. */
    suspend fun render(params: Map<String, Any?>, budgetTokens: Int): String
}

/** Rough token estimate. Deterministic and cheap; ~4 chars/token for English key-value text. */
fun estimateTokens(text: String): Int = (text.length + 3) / 4

/** Truncate a block to its budget, dropping oldest lines first (last lines assumed most recent). */
fun clampToBudget(lines: List<String>, budgetTokens: Int): String {
    if (lines.isEmpty()) return ""
    val kept = ArrayDeque<String>()
    var used = 0
    for (line in lines.asReversed()) {          // newest first
        val cost = estimateTokens(line) + 1
        if (used + cost > budgetTokens && kept.isNotEmpty()) break
        kept.addFirst(line)
        used += cost
    }
    return kept.joinToString("\n")
}

class ContextAssembler(providers: List<ContextProvider>) {
    private val byKey = providers.associateBy { it.key }

    /**
     * Assemble the full context string for a task. Blocks are concatenated in recipe order under
     * short ALL-CAPS headers so the model can attend to them; missing/empty blocks are omitted.
     */
    suspend fun assemble(task: TaskDef, params: Map<String, Any?>): AssembledContext {
        val sb = StringBuilder()
        var totalBudget = 0
        for (block in task.recipe) {
            totalBudget += block.budgetTokens
            val provider = byKey[block.providerKey] ?: continue
            val body = provider.render(params, block.budgetTokens).trim()
            if (body.isEmpty()) continue
            sb.append(block.providerKey.uppercase()).append('\n')
            sb.append(body).append("\n\n")
        }
        val text = sb.toString().trimEnd()
        return AssembledContext(text = text, estimatedTokens = estimateTokens(text), budgetTokens = totalBudget)
    }
}

data class AssembledContext(val text: String, val estimatedTokens: Int, val budgetTokens: Int)
