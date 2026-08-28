package com.pjournal.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface JournalHistoryDao {
    @Query("SELECT * FROM journal_history WHERE filename = :filename ORDER BY created_at DESC, id DESC")
    suspend fun getHistory(filename: String): List<JournalHistoryEntity>

    @Query("SELECT * FROM journal_history WHERE id = :id")
    suspend fun getHistoryVersion(id: Long): JournalHistoryEntity?

    @Insert
    suspend fun insertHistory(version: JournalHistoryEntity): Long

    @Query(
        """
        DELETE FROM journal_history
        WHERE filename = :filename
        AND id NOT IN (
            SELECT id FROM journal_history
            WHERE filename = :filename
            ORDER BY created_at DESC, id DESC
            LIMIT :keep
        )
        """
    )
    suspend fun trimHistory(filename: String, keep: Int)

    @Query("DELETE FROM journal_history WHERE id = :id")
    suspend fun deleteHistoryVersion(id: Long)

    @Query("DELETE FROM journal_history WHERE filename = :filename")
    suspend fun deleteHistoryForEntry(filename: String)
}
