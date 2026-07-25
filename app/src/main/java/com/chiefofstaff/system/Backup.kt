package com.chiefofstaff.system

import android.content.Context
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.core.Clock
import com.chiefofstaff.data.LifeRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * SYS-10 / SYS-11 — nightly encrypted export and full JSON export. One phone holding externalised
 * memory is a single point of failure (§16.8), so the 02:00 batch writes a snapshot every night.
 *
 * The snapshot is written to app-private storage; the encrypted-to-Drive upload (SYS-10) is the one
 * piece intentionally left as a hook here — it needs the OAuth decision (§16.4) that the spec defers
 * to day 7. Reference values stay ciphertext in the export (§6.8): the vault is never decrypted to
 * disk, even for backup.
 */
class Backup(
    private val context: Context,
    private val repo: LifeRepository,
    private val clock: Clock,
    private val json: Json = Json { prettyPrint = true },
) {
    private val dir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    /** Full JSON export (SYS-11). Returns the file written. */
    suspend fun exportJson(): File {
        val captures = repo.captures.snapshot()
        val commitments = repo.commitments.allCommitments()

        val root = buildJsonObject {
            put("exported_at", clock.now().toString())
            put("schema_version", 1)
            put("capture_count", captures.size)
            put("commitment_count", commitments.size)
            put("captures", JsonArray(captures.map { c ->
                buildJsonObject {
                    put("id", c.id); put("source", c.source.name)
                    put("created_at", c.createdAt.toString())
                    put("raw", c.rawContent); put("parsed", c.parsed)
                }
            }))
            put("commitments", JsonArray(commitments.map { c ->
                buildJsonObject {
                    put("id", c.id); put("what", c.what); put("state", c.state.name)
                    put("domain", c.domain.name); put("deferrals", c.deferralCount)
                    put("due_at", c.dueAt?.toString() ?: "")
                }
            }))
        }

        val file = File(dir, "export-${clock.today()}.json")
        file.writeText(json.encodeToString(JsonObject.serializer(), root))
        AppLog.i("backup", "wrote export ${file.name} (${file.length()} bytes)")
        return file
    }

    /** SYS-10 — nightly snapshot. Keeps the last 7 exports; Drive upload is the remaining hook. */
    suspend fun runNightlyBackup() {
        exportJson()
        // Retention: keep a week of local snapshots.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { it.delete() }
        // TODO(§16.4): once the OAuth approach is decided, upload the (already-encrypted) file to Drive.
    }
}
