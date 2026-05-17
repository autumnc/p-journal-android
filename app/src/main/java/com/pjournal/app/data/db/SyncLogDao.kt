package com.pjournal.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(log: SyncLogEntity)

    @Query("SELECT * FROM sync_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SyncLogEntity>>

    @Query("DELETE FROM sync_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
