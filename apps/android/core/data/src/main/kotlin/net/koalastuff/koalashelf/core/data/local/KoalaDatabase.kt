package net.koalastuff.koalashelf.core.data.local

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity(tableName = "cached_books")
data class CachedBookEntity(
    @PrimaryKey
    val compositeKey: String, // "$accountKey|$itemId"
    val accountKey: String,
    val libraryId: String,
    val itemId: String,
    val title: String,
    val author: String,
    val itemJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "media_progress")
data class MediaProgressEntity(
    @PrimaryKey
    val compositeKey: String, // "$userId|$libraryItemId"
    val userId: String,
    val progressId: String,
    val libraryItemId: String,
    val duration: Double,
    val progress: Double,
    val currentTime: Double,
    val isFinished: Boolean,
    val hideFromContinueListening: Boolean,
    val lastUpdate: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val isLocalOnly: Boolean = false,
)

@Serializable
data class DownloadedTrackManifest(
    val index: Int,
    val startOffset: Double,
    val duration: Double,
    val mimeType: String,
    val fileName: String,
)

@Entity(tableName = "downloaded_books")
data class DownloadedBookEntity(
    @PrimaryKey
    val itemId: String,
    val title: String,
    val author: String,
    val itemJson: String,
    val tracksJson: String, // List<DownloadedTrackManifest>
    val sizeBytes: Long,
    val hasCover: Boolean,
    val downloadedAt: Long,
    val ownersCsv: String, // Comma-separated userIds who downloaded/own this copy on disk
)

@Entity(tableName = "queued_items")
data class QueuedItemEntity(
    @PrimaryKey
    val compositeKey: String, // "$userId|$itemId"
    val userId: String,
    val itemId: String,
    val position: Int,
    val itemJson: String,
)

@Entity(tableName = "local_sessions")
data class LocalSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val userId: String,
    val libraryItemId: String,
    val payloadJson: String,
    val updatedAt: Long,
)

@Dao
interface KoalaDao {
    // --- Cached Books ---
    @Query("SELECT * FROM cached_books WHERE accountKey = :accountKey AND libraryId = :libraryId ORDER BY title ASC")
    suspend fun getCachedBooks(accountKey: String, libraryId: String): List<CachedBookEntity>

    @Query("SELECT * FROM cached_books WHERE compositeKey = :compositeKey LIMIT 1")
    suspend fun getCachedBook(compositeKey: String): CachedBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedBooks(books: List<CachedBookEntity>)

    @Query("DELETE FROM cached_books WHERE accountKey = :accountKey")
    suspend fun clearCachedBooksForAccount(accountKey: String)

    // --- Media Progress ---
    @Query("SELECT * FROM media_progress WHERE userId = :userId")
    fun observeProgressForUser(userId: String): Flow<List<MediaProgressEntity>>

    @Query("SELECT * FROM media_progress WHERE userId = :userId")
    suspend fun getProgressForUser(userId: String): List<MediaProgressEntity>

    @Query("SELECT * FROM media_progress WHERE compositeKey = :compositeKey LIMIT 1")
    suspend fun getProgress(compositeKey: String): MediaProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entry: MediaProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllProgress(entries: List<MediaProgressEntity>)

    @Query("DELETE FROM media_progress WHERE compositeKey = :compositeKey")
    suspend fun deleteProgress(compositeKey: String)

    @Query("DELETE FROM media_progress WHERE userId = :userId")
    suspend fun clearProgressForUser(userId: String)

    // --- Downloads ---
    @Query("SELECT * FROM downloaded_books ORDER BY downloadedAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadedBookEntity>>

    @Query("SELECT * FROM downloaded_books ORDER BY downloadedAt DESC")
    suspend fun getAllDownloads(): List<DownloadedBookEntity>

    @Query("SELECT * FROM downloaded_books WHERE itemId = :itemId LIMIT 1")
    suspend fun getDownload(itemId: String): DownloadedBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: DownloadedBookEntity)

    @Query("DELETE FROM downloaded_books WHERE itemId = :itemId")
    suspend fun deleteDownload(itemId: String)

    // --- Queue ("Up Next") ---
    @Query("SELECT * FROM queued_items WHERE userId = :userId ORDER BY position ASC")
    fun observeQueue(userId: String): Flow<List<QueuedItemEntity>>

    @Query("SELECT * FROM queued_items WHERE userId = :userId ORDER BY position ASC")
    suspend fun getQueue(userId: String): List<QueuedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueueItems(items: List<QueuedItemEntity>)

    @Query("DELETE FROM queued_items WHERE compositeKey = :compositeKey")
    suspend fun deleteQueueItem(compositeKey: String)

    @Query("DELETE FROM queued_items WHERE userId = :userId")
    suspend fun clearQueue(userId: String)

    // --- Offline Local Sessions ---
    @Query("SELECT * FROM local_sessions WHERE userId = :userId ORDER BY updatedAt ASC")
    suspend fun getLocalSessions(userId: String): List<LocalSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalSession(session: LocalSessionEntity)

    @Query("DELETE FROM local_sessions WHERE sessionId = :sessionId")
    suspend fun deleteLocalSession(sessionId: String)

    @Query("DELETE FROM local_sessions WHERE userId = :userId")
    suspend fun clearLocalSessionsForUser(userId: String)
}

@Database(
    entities = [
        CachedBookEntity::class,
        MediaProgressEntity::class,
        DownloadedBookEntity::class,
        QueuedItemEntity::class,
        LocalSessionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class KoalaDatabase : RoomDatabase() {
    abstract fun koalaDao(): KoalaDao

    companion object {
        fun build(context: Context): KoalaDatabase {
            val dbFile = context.getDatabasePath("koalashelf.db")
            return Room.databaseBuilder<KoalaDatabase>(
                context = context.applicationContext,
                name = dbFile.absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
