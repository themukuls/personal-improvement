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
import com.chiefofstaff.data.entity.Occasion
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
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Capture::class, CaptureFts::class, ReviewQueueItem::class,
        Commitment::class, WaitingOn::class,
        ValueStatement::class, Goal::class, Project::class, RuleEntity::class,
        Person::class, Interaction::class, Observation::class, Decision::class,
        ReferenceItem::class, EventEntity::class, Note::class, Occasion::class,
        DayState::class, ModeState::class, Prediction::class,
        AnticipationItem::class, NotificationLog::class,
        Session::class, Message::class,
    ],
    version = 2,
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
         * Builds the on-device database. Room stores it as a plain SQLite file in the app's private
         * internal storage (`/data/data/<pkg>/databases/cos.db`), which is sandboxed to this app by
         * the Android filesystem — other apps cannot read it. Single user, single device: local
         * storage is the whole persistence story (no server, no sync). The nightly JSON export
         * (SYS-10/11) remains the backup against device loss.
         */
        fun build(context: Context): CoSDatabase =
            Room.databaseBuilder(context, CoSDatabase::class.java, "cos.db")
                .addCallback(FtsSyncCallback)
                // Pre-1.0: the schema still moves as domains come online. Rather than carry a
                // migration per shape change before there are real users, we rebuild on a version
                // bump — the nightly JSON export (SYS-10/11) is the backup against data loss, and
                // real migrations land once the schema settles for release.
                .fallbackToDestructiveMigration()
                .build()

        /**
         * Keeps the external-content FTS table [CaptureFts] in sync with [Capture] via SQLite
         * triggers, the standard pattern for contentless/external-content FTS4. Captures are
         * immutable (P5) so only the AFTER INSERT trigger fires in practice, but the delete/update
         * triggers are declared for correctness. Room creates the virtual table before onCreate
         * runs, so the triggers can reference it here.
         */
        private object FtsSyncCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS capture_fts_ai AFTER INSERT ON capture BEGIN " +
                        "INSERT INTO capture_fts(docid, rawContent) VALUES (new.rowid, new.rawContent); END;"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS capture_fts_ad AFTER DELETE ON capture BEGIN " +
                        "INSERT INTO capture_fts(capture_fts, docid, rawContent) VALUES('delete', old.rowid, old.rawContent); END;"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS capture_fts_au AFTER UPDATE ON capture BEGIN " +
                        "INSERT INTO capture_fts(capture_fts, docid, rawContent) VALUES('delete', old.rowid, old.rawContent); " +
                        "INSERT INTO capture_fts(docid, rawContent) VALUES (new.rowid, new.rawContent); END;"
                )
            }
        }
    }
}
