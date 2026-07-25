package com.chiefofstaff.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chiefofstaff.data.dao.CaptureDao
import com.chiefofstaff.data.dao.CommitmentDao
import com.chiefofstaff.data.dao.ConversationDao
import com.chiefofstaff.data.dao.GraphDao
import com.chiefofstaff.data.dao.StateDao
import com.chiefofstaff.data.entity.AnticipationItem
import com.chiefofstaff.data.entity.Capture
import com.chiefofstaff.data.entity.CaptureFts
import com.chiefofstaff.data.entity.Commitment
import com.chiefofstaff.data.entity.DayState
import com.chiefofstaff.data.entity.Decision
import com.chiefofstaff.data.entity.EventEntity
import com.chiefofstaff.data.entity.Goal
import com.chiefofstaff.data.entity.Interaction
import com.chiefofstaff.data.entity.Message
import com.chiefofstaff.data.entity.ModeState
import com.chiefofstaff.data.entity.Note
import com.chiefofstaff.data.entity.NotificationLog
import com.chiefofstaff.data.entity.Observation
import com.chiefofstaff.data.entity.Person
import com.chiefofstaff.data.entity.Prediction
import com.chiefofstaff.data.entity.Project
import com.chiefofstaff.data.entity.ReferenceItem
import com.chiefofstaff.data.entity.ReviewQueueItem
import com.chiefofstaff.data.entity.RuleEntity
import com.chiefofstaff.data.entity.Session
import com.chiefofstaff.data.entity.ValueStatement
import com.chiefofstaff.data.entity.WaitingOn
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Capture::class, CaptureFts::class, ReviewQueueItem::class,
        Commitment::class, WaitingOn::class,
        ValueStatement::class, Goal::class, Project::class, RuleEntity::class,
        Person::class, Interaction::class, Observation::class, Decision::class,
        ReferenceItem::class, EventEntity::class, Note::class,
        DayState::class, ModeState::class, Prediction::class,
        AnticipationItem::class, NotificationLog::class,
        Session::class, Message::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CoSDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun graphDao(): GraphDao
    abstract fun stateDao(): StateDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        /**
         * Builds the encrypted database. The SQLCipher [SupportOpenHelperFactory] transparently
         * encrypts every page at rest with the Keystore-guarded passphrase (SYS-09).
         */
        fun build(context: Context): CoSDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKey.getOrCreate(context)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, CoSDatabase::class.java, "cos.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}
