package com.chiefofstaff.data.model

/** §7.1 — where a capture came from. Capture preference order (P1): sensor > notification > photo > voice > typing > form. */
enum class CaptureSource { VOICE, TEXT, CALENDAR, NOTIFICATION, SENSOR, PHOTO, EMAIL, SHARE }

/** Life domains (§12.10) — a capability, activated per DOM-05. */
enum class Domain {
    HEALTH, WORK, MONEY, PEOPLE, HOME, LEARNING, TRAVEL, ADMIN, PROJECTS, NONE
}

/** Commitment lifecycle states (§7.4). Nothing exits without a verdict (P4). */
enum class CommitmentState {
    CAPTURED, SCHEDULED, DUE, ASKED,
    DONE, SKIPPED, DEFERRED, ESCALATED, DROPPED, RECOMMITTED, AUTO_ARCHIVED
}

/** The four verdicts the Close card stack can emit (§9.2). */
enum class Verdict { DONE, SKIPPED, DEFERRED, DROPPED }

/** Energy cost hint used by energy-aware scheduling (DIR-07). */
enum class EnergyCost { LOW, MEDIUM, HIGH }

/** Rule enforcement (§7.2). The referee treats hard rules as planning constraints (DIR-03). */
enum class Enforcement { HARD, SOFT }

/** Operating mode (§7.3) — affects notification budget and plan shape. */
enum class Mode { NORMAL, DEEP_WORK, TRAVEL, RECOVERY, SICK, WEEKEND }

/** WaitingOn / general open-loop state. */
enum class LoopState { OPEN, CHASED, RESOLVED, DROPPED }

/** Review queue reason (§7.3). */
enum class ReviewReason { LOW_CONFIDENCE, AMBIGUOUS, CONFLICTING, PARSE_FAILED }

/** Fact kinds emitted by extraction / conversation, used to route into typed tables. */
enum class FactKind {
    COMMITMENT, WAITING_ON, EVENT, INTERACTION, OBSERVATION, PERSON,
    DECISION, RULE, REFERENCE, GOAL, PROJECT, VALUE, NOTE
}

/** Model tiers (§4.1 two-tier routing). */
enum class ModelTier { CHEAP, FLAGSHIP }

/** Conversation modes (§10.1). */
enum class ConversationMode { RECALL, PLAN, THINK, CHALLENGE, DECIDE, DRAFT, DEBRIEF }

/** Anticipation scan kinds (§11). */
enum class AnticipationKind {
    HORIZON, EXPIRY, STREAK_BREAK, OVERLOAD, NEGLECT, TREND_INFLECTION,
    CONFLICT, WAITING_DECAY, FINANCIAL, DECISION_REVIEW, OCCASION
}
