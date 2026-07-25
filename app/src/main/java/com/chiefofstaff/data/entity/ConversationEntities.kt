package com.chiefofstaff.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chiefofstaff.data.model.ConversationMode
import java.time.Instant

/**
 * §10 — a conversation thread. The context snapshot is retained so a session is reproducible
 * (a stated rule of the conversation layer), and any facts it emits link back to it (CNV-12).
 */
@Entity(tableName = "session", indices = [Index("startedAt")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    val mode: ConversationMode = ConversationMode.THINK,
    val contextSnapshot: String? = null, // the assembled context that opened the session
    val title: String? = null,
    val tokenBudgetUsed: Int = 0,        // §16.6 per-session ceiling guard
)

@Entity(tableName = "message", indices = [Index("sessionId"), Index("createdAt")])
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,                    // "user" | "assistant"
    val content: String,
    val createdAt: Instant,
    /** Ids of facts this message produced, so a conversation is linked to what it created. */
    val emittedFactRefs: List<String> = emptyList(),
)
