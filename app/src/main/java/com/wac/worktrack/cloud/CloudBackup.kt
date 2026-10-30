package com.wac.worktrack.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.wac.worktrack.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Copies the whole local database to Firestore and back. The phone stays the source of truth;
 * this is a backup for a single user, not live sync.
 *
 * Lite shares the Firebase project with the full WorkTrack app, so it keeps its own document:
 * users/{uid}/apps/lite {chunks: {table: n}, updatedAt}, .../lite/tables/{table}_{i} {json}.
 */
object CloudBackup {
    /**
     * Firestore rejects documents over 1 MiB (UTF-8 bytes, field names included), so rows are
     * packed into chunks by size rather than count. The margin covers the other fields.
     */
    const val MAX_CHUNK_BYTES = 900 * 1024

    /** Firestore allows 500 writes and 10 MiB per batch; stay under both. */
    private const val BATCH_OPS = 450
    private const val BATCH_BYTES = 8 * 1024 * 1024

    /** Firestore waits for the server to confirm a write, which never comes while offline. */
    private const val TIMEOUT_MILLIS = 60_000L

    private fun backupDoc(context: Context) =
        FirebaseFirestore.getInstance().collection("users")
            .document(Auth.user(context)?.uid ?: error("Not signed in"))
            .collection("apps").document("lite")

    private suspend fun <T> online(block: suspend () -> T): T =
        withTimeoutOrNull(TIMEOUT_MILLIS) { block() } ?: error("No connection to the cloud. Check your internet and try again.")

    /** When the cloud copy was last written, or null if this account has no backup yet. */
    suspend fun lastCloudBackup(context: Context): Long? = online {
        backupDoc(context).get().await().getLong("updatedAt")
    }

    /** Returns the number of rows uploaded. */
    suspend fun backup(context: Context): Int = online { backupNow(context) }

    private suspend fun backupNow(context: Context): Int = withContext(Dispatchers.IO) {
        val doc = backupDoc(context)
        val oldChunks = (doc.get().await().get("chunks") as? Map<*, *>).orEmpty()
        val chunks = mutableMapOf<String, Int>()
        var rows = 0
        // Batched writes: a few round trips instead of one per chunk. A batch is committed early
        // before it reaches Firestore's operation or request-size limits.
        val db = FirebaseFirestore.getInstance()
        var batch = db.batch()
        var ops = 0
        var bytes = 0
        suspend fun flushIfFull(nextBytes: Int) {
            if (ops > 0 && (ops >= BATCH_OPS || bytes + nextBytes > BATCH_BYTES)) {
                batch.commit().await()
                batch = db.batch()
                ops = 0
                bytes = 0
            }
        }
        for ((table, all) in Snapshot.dump(context)) {
            rows += all.size
            val parts = chunk(all.map { it.toString() })
            chunks[table] = parts.size
            parts.forEachIndexed { i, json ->
                val size = json.utf8Size()
                flushIfFull(size)
                batch.set(doc.collection("tables").document("${table}_$i"), mapOf("table" to table, "chunk" to i, "json" to json))
                ops++
                bytes += size
            }
            val old = (oldChunks[table] as? Number)?.toInt() ?: 0
            for (i in parts.size until old) {
                flushIfFull(0)
                batch.delete(doc.collection("tables").document("${table}_$i"))
                ops++
            }
        }
        if (ops > 0) batch.commit().await()
        val now = System.currentTimeMillis()
        doc.set(mapOf("chunks" to chunks, "updatedAt" to now, "schema" to Snapshot.FORMAT)).await()
        BackupPrefs(context).lastBackupMillis = now
        rows
    }

    /**
     * Packs JSON rows into JSON arrays of at most [maxBytes] UTF-8 bytes each, keeping row order.
     * Restore just concatenates the arrays.
     */
    fun chunk(rows: List<String>, maxBytes: Int = MAX_CHUNK_BYTES): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder("[")
        var size = 2 // "[" + "]"
        for (row in rows) {
            val rowSize = row.utf8Size()
            require(rowSize + 2 <= maxBytes) { "${rowSize / 1024} KB" }
            // +1 for the "," before every row but the first.
            if (size > 2 && size + rowSize + 1 > maxBytes) {
                out += current.append(']').toString()
                current.setLength(0)
                current.append('[')
                size = 2
            }
            if (size > 2) {
                current.append(',')
                size++
            }
            current.append(row)
            size += rowSize
        }
        if (current.length > 1) out += current.append(']').toString()
        return out
    }

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    /** Replaces all local data with the cloud copy. Returns rows restored, or -1 if there's no backup. */
    suspend fun restore(context: Context): Int = online { restoreNow(context) }.also {
        // Phone and cloud now match, so automatic backups may start.
        if (it >= 0) BackupPrefs(context).lastBackupMillis = System.currentTimeMillis()
    }

    /** What happened right after signing in. */
    sealed interface Start {
        data class Uploaded(val rows: Int) : Start
        data class Restored(val rows: Int) : Start
        /** Both this phone and the cloud have data; the user picks which one wins. */
        data class Conflict(val cloudMillis: Long) : Start
    }

    /**
     * First step after signing in: an empty account gets this phone's data, an empty phone gets
     * the cloud's. When both have data nothing is overwritten until the user chooses.
     */
    suspend fun afterSignIn(context: Context): Start {
        val cloud = lastCloudBackup(context) ?: return Start.Uploaded(backup(context))
        val phoneEmpty = withContext(Dispatchers.IO) {
            Snapshot.dump(context).filterKeys { it != "settings" }.values.all { it.isEmpty() }
        }
        return if (phoneEmpty) Start.Restored(restore(context)) else Start.Conflict(cloud)
    }

    private suspend fun restoreNow(context: Context): Int = withContext(Dispatchers.IO) {
        val doc = backupDoc(context)
        val chunks = doc.get().await().get("chunks") as? Map<*, *> ?: return@withContext -1
        // One query for every chunk instead of one read each.
        val docs = doc.collection("tables").get().await().documents.associateBy { it.id }
        val data = Snapshot.TABLES.associateWith { table ->
            val n = (chunks[table] as? Number)?.toInt() ?: 0
            (0 until n).flatMap { i ->
                val arr = JSONArray(docs["${table}_$i"]?.getString("json") ?: "[]")
                List(arr.length()) { arr.getJSONObject(it) }
            }
        }
        Snapshot.load(context, data)
    }

    /** Automatic backups wait until this phone and the cloud have been matched up once (see [afterSignIn]). */
    fun autoReady(context: Context): Boolean =
        Auth.user(context) != null && BackupPrefs(context).let { it.autoBackup && it.lastBackupMillis > 0 }

    /** Backs up shortly after the user leaves the app, once online, so the cloud is minutes behind at most. */
    fun backupSoon(context: Context) {
        if (!Auth.firebaseReady(context) || !autoReady(context)) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(BackupWorker.SOON, ExistingWorkPolicy.REPLACE, request)
    }

    /** Runs the daily backup while signed in with auto backup on; cancels it otherwise. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!BackupPrefs(context).autoBackup || Auth.user(context) == null) {
            wm.cancelUniqueWork(BackupWorker.NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(BackupWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!CloudBackup.autoReady(applicationContext)) return Result.success()
        return try {
            CloudBackup.backup(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val NAME = "daily-backup"
        const val SOON = "backup-on-leave"
    }
}

/** Backup state kept outside the database, so a restore doesn't overwrite it. */
class BackupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var lastBackupMillis: Long
        get() = prefs.getLong("last", 0L)
        set(value) = prefs.edit().putLong("last", value).apply()

    var autoBackup: Boolean
        get() = prefs.getBoolean("auto", true)
        set(value) = prefs.edit().putBoolean("auto", value).apply()

    /** Chose "Continue without signing in", so the sign-in screen stays away. */
    var offline: Boolean
        get() = prefs.getBoolean("offline", false)
        set(value) = prefs.edit().putBoolean("offline", value).apply()
}
