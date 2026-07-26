package com.chiefofstaff.llm

import com.chiefofstaff.data.model.ModelTier

/**
 * §6.2 Component 1 — a Task is a declared unit of LLM work. ~18 exist. Each names a model tier,
 * a context recipe, a prompt reference, an output schema, and its retry/cache/token policy.
 * Adding capability = declaring a Task, never writing an agent loop (§6.1).
 */
enum class TaskId {
    EXTRACT_FACTS, RESOLVE_ENTITY, GENERATE_PLAN, MORNING_BRIEF, EVENING_PARSE,
    MATCH_COMMITMENTS, WEEKLY_AUDIT, ANTICIPATE, DECISION_SUPPORT, CONVERSE,
    ANSWER_QUESTION, SUMMARISE_MEETING, ESTIMATE_MEAL, DETECT_CONFLICT,
    PROPOSE_REDUCTION, DRAFT_MESSAGE, EXPLAIN_TREND, BOOTSTRAP_INTERVIEW, VOICE_COMMAND
}

data class RetryPolicy(val maxAttempts: Int = 2, val repairOnce: Boolean = true)
enum class CachePolicy { NONE, PREFIX }

/**
 * A context recipe: ordered blocks, each with its own hard token budget (§6.3). The assembler
 * fills each block from a [ContextProvider], truncating oldest-first when a block overflows.
 */
data class RecipeBlock(val providerKey: String, val budgetTokens: Int)

data class TaskDef(
    val id: TaskId,
    val tier: ModelTier,
    val recipe: List<RecipeBlock>,
    val promptRef: String,          // key into the PromptRegistry (task@version)
    val schemaRef: String?,         // null = free-text output (conversation)
    val temperature: Double,
    val maxOutputTokens: Int,
    val retry: RetryPolicy = RetryPolicy(),
    val cache: CachePolicy = CachePolicy.NONE,
)

/**
 * The static registry of Tasks. This is configuration, not compiled logic — the recipes and
 * budgets here are exactly the levers §6.3 says determine output quality more than model choice.
 */
object Tasks {
    private fun b(key: String, budget: Int) = RecipeBlock(key, budget)

    val all: Map<TaskId, TaskDef> = listOf(
        TaskDef(
            id = TaskId.EXTRACT_FACTS,
            tier = ModelTier.CHEAP,
            recipe = listOf(b("raw_capture", 400), b("active_domains", 80), b("known_people", 120)),
            promptRef = "extract_facts@1",
            schemaRef = "facts",
            temperature = 0.0,
            maxOutputTokens = 600,
        ),
        TaskDef(
            id = TaskId.RESOLVE_ENTITY,
            tier = ModelTier.CHEAP,
            recipe = listOf(b("entity_mention", 60), b("candidate_entities", 200)),
            promptRef = "resolve_entity@1",
            schemaRef = "entity_match",
            temperature = 0.0,
            maxOutputTokens = 120,
        ),
        // The plan recipe is the one spelled out verbatim in §6.3.
        TaskDef(
            id = TaskId.GENERATE_PLAN,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(
                b("today_calendar", 400),
                b("open_commitments", 800),
                b("active_rules", 200),
                b("current_state", 100),
                b("recent_observations", 300),
                b("yesterday_verdicts", 200),
                b("prediction_accuracy", 100),
                b("active_goals", 200),
            ),
            promptRef = "generate_plan@1",
            schemaRef = "plan",
            temperature = 0.3,
            maxOutputTokens = 900,
            cache = CachePolicy.PREFIX,
        ),
        TaskDef(
            id = TaskId.MORNING_BRIEF,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(
                b("today_calendar", 300), b("today_plan", 400), b("one_health_note", 100),
                b("top_anticipation", 150), b("current_state", 80),
            ),
            promptRef = "morning_brief@1",
            schemaRef = "brief",
            temperature = 0.4,
            maxOutputTokens = 400,
            cache = CachePolicy.PREFIX,
        ),
        TaskDef(
            id = TaskId.EVENING_PARSE,
            tier = ModelTier.CHEAP,
            recipe = listOf(b("raw_capture", 500), b("due_commitments", 500)),
            promptRef = "evening_parse@1",
            schemaRef = "verdicts",
            temperature = 0.1,
            maxOutputTokens = 500,
        ),
        TaskDef(
            id = TaskId.MATCH_COMMITMENTS,
            tier = ModelTier.CHEAP,
            recipe = listOf(b("free_recap", 400), b("due_commitments", 500)),
            promptRef = "match_commitments@1",
            schemaRef = "matches",
            temperature = 0.0,
            maxOutputTokens = 400,
        ),
        TaskDef(
            id = TaskId.WEEKLY_AUDIT,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(
                b("week_planned_vs_actual", 600), b("week_trends", 300),
                b("prediction_accuracy", 150), b("active_goals", 200),
            ),
            promptRef = "weekly_audit@1",
            schemaRef = "audit",
            temperature = 0.4,
            maxOutputTokens = 700,
        ),
        TaskDef(
            id = TaskId.ANTICIPATE,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(
                b("horizon_events", 400), b("expiring_references", 200), b("waiting_decay", 200),
                b("overload_forecast", 200), b("neglect_scan", 200), b("financial_calendar", 200),
            ),
            promptRef = "anticipate@1",
            schemaRef = "anticipation",
            temperature = 0.5,
            maxOutputTokens = 400,
        ),
        TaskDef(
            id = TaskId.DECISION_SUPPORT,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(b("decision_question", 200), b("past_analogues", 400), b("active_rules", 200), b("active_goals", 200)),
            promptRef = "decision_support@1",
            schemaRef = "decision",
            temperature = 0.5,
            maxOutputTokens = 700,
        ),
        TaskDef(
            id = TaskId.CONVERSE,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(b("session_history", 1200), b("relevant_facts", 1000), b("active_rules", 200), b("recent_decisions", 300)),
            promptRef = "converse@2",
            schemaRef = null,               // free text; a conversation is not a schema
            temperature = 0.7,
            maxOutputTokens = 800,
            cache = CachePolicy.PREFIX,
        ),
        TaskDef(
            id = TaskId.ANSWER_QUESTION,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(b("question", 200), b("retrieved_captures", 1200), b("relevant_facts", 600)),
            promptRef = "answer_question@1",
            schemaRef = "answer",
            temperature = 0.3,
            maxOutputTokens = 600,
        ),
        TaskDef(
            id = TaskId.SUMMARISE_MEETING,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(b("meeting_transcript", 2000), b("known_people", 200)),
            promptRef = "summarise_meeting@1",
            schemaRef = "meeting_summary",
            temperature = 0.3,
            maxOutputTokens = 700,
        ),
        TaskDef(
            id = TaskId.ESTIMATE_MEAL,
            tier = ModelTier.CHEAP,
            recipe = listOf(b("meal_note", 100)),
            promptRef = "estimate_meal@1",
            schemaRef = "meal_estimate",
            temperature = 0.2,
            maxOutputTokens = 200,
        ),
        TaskDef(
            id = TaskId.DETECT_CONFLICT,
            tier = ModelTier.CHEAP,
            recipe = listOf(b("today_calendar", 400), b("open_commitments", 600), b("active_rules", 200)),
            promptRef = "detect_conflict@1",
            schemaRef = "conflicts",
            temperature = 0.0,
            maxOutputTokens = 300,
        ),
        TaskDef(
            id = TaskId.PROPOSE_REDUCTION,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(b("stale_goals", 400), b("dormant_commitments", 400), b("quiet_domains", 200)),
            promptRef = "propose_reduction@1",
            schemaRef = "reduction",
            temperature = 0.5,
            maxOutputTokens = 500,
        ),
        TaskDef(
            id = TaskId.DRAFT_MESSAGE,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(b("draft_request", 300), b("relevant_context", 800), b("voice_samples", 400)),
            promptRef = "draft_message@1",
            schemaRef = "draft",
            temperature = 0.7,
            maxOutputTokens = 600,
        ),
        TaskDef(
            id = TaskId.EXPLAIN_TREND,
            tier = ModelTier.CHEAP,
            recipe = listOf(b("trend_series", 400), b("related_context", 300)),
            promptRef = "explain_trend@1",
            schemaRef = "trend_explanation",
            temperature = 0.4,
            maxOutputTokens = 300,
        ),
        TaskDef(
            id = TaskId.BOOTSTRAP_INTERVIEW,
            tier = ModelTier.FLAGSHIP,
            recipe = listOf(b("interview_sitting", 200), b("collected_so_far", 800)),
            promptRef = "bootstrap_interview@1",
            schemaRef = "interview_turn",
            temperature = 0.6,
            maxOutputTokens = 500,
        ),
        // Voice command interpreter: turns a spoken sentence into one structured action. Cheap tier,
        // no context recipe — the utterance + current time come in as the user content.
        TaskDef(
            id = TaskId.VOICE_COMMAND,
            tier = ModelTier.FLAGSHIP,   // correctness matters more than cost; the cheap tier was unreliable
            recipe = emptyList(),
            promptRef = "voice_command@2",
            schemaRef = "command",
            temperature = 0.0,
            maxOutputTokens = 220,
        ),
    ).associateBy { it.id }

    operator fun get(id: TaskId): TaskDef = all.getValue(id)
}
