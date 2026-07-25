package com.chiefofstaff.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.chiefofstaff.data.entity.Message
import com.chiefofstaff.data.entity.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert suspend fun insertSession(s: Session): Long
    @Update suspend fun updateSession(s: Session)
    @Query("SELECT * FROM session ORDER BY startedAt DESC") fun sessions(): Flow<List<Session>>
    @Query("SELECT * FROM session WHERE id = :id") suspend fun session(id: Long): Session?

    @Insert suspend fun insertMessage(m: Message): Long
    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun messages(sessionId: Long): Flow<List<Message>>
    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun messagesNow(sessionId: Long): List<Message>
}
