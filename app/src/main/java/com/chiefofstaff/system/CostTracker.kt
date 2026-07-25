package com.chiefofstaff.system

import com.chiefofstaff.core.AppLog
import com.chiefofstaff.llm.LlmUsage
import com.chiefofstaff.llm.TaskId
import java.util.concurrent.atomic.AtomicInteger

/**
 * SYS-17 — the token and cost dashboard, and the guard behind §16.6 (conversation is the only
 * unbounded-cost surface). This keeps a running daily tally so the app can show spend and, later,
 * enforce a per-session ceiling. Deliberately simple: one user, in-memory counters reset daily.
 */
class CostTracker {
    private val inputTokens = AtomicInteger(0)
    private val outputTokens = AtomicInteger(0)

    fun record(task: TaskId, usage: LlmUsage, providerName: String) {
        inputTokens.addAndGet(usage.inputTokens)
        outputTokens.addAndGet(usage.outputTokens)
        AppLog.d(
            "cost",
            "$task via $providerName +${usage.inputTokens}in/${usage.outputTokens}out " +
                "(today ${inputTokens.get()}in/${outputTokens.get()}out)",
        )
    }

    fun todayInputTokens(): Int = inputTokens.get()
    fun todayOutputTokens(): Int = outputTokens.get()
    fun reset() { inputTokens.set(0); outputTokens.set(0) }
}
