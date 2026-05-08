package com.sonuverma.deeploader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for download operations.
 * Uses Flow for reactive UI updates — Room automatically notifies observers
 * when underlying data changes.
 */
@Dao
interface DownloadDao {

    // ─── Insert / Update / Delete ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<DownloadEntity>)

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    // ─── Queries — Flow (reactive) ───

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'MERGING', 'CONVERTING', 'SAVING') ORDER BY createdAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'FAILED' ORDER BY createdAt DESC")
    fun getFailedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE platform = :platform ORDER BY createdAt DESC")
    fun getDownloadsByPlatform(platform: String): Flow<List<DownloadEntity>>

    // ─── Queries — Suspend (one-shot) ───

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getActiveDownloadCount(): Int

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun getTotalCount(): Int

    @Query("SELECT SUM(downloadedBytes) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getTotalDownloadedBytes(): Long?

    // ─── Status Updates ───

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun updateStatusWithError(id: String, status: String, error: String)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, status: String)

    @Query("UPDATE downloads SET chunkOffsetsJson = :offsets WHERE id = :id")
    suspend fun updateChunkOffsets(id: String, offsets: String)

    @Query("UPDATE downloads SET filePath = :path, completedAt = :completedAt, status = 'COMPLETED' WHERE id = :id")
    suspend fun markCompleted(id: String, path: String, completedAt: Long = System.currentTimeMillis())

    // ─── Bulk Operations ───

    @Query("UPDATE downloads SET status = 'PAUSED' WHERE status = 'DOWNLOADING'")
    suspend fun pauseAllActive()

    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'PAUSED'")
    suspend fun resumeAllPaused()

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearHistory()

    // ─── Search ───

    @Query("SELECT * FROM downloads WHERE title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchDownloads(query: String): Flow<List<DownloadEntity>>
}
