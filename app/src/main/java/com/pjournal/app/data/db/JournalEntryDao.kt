package com.pjournal.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    @Query("SELECT * FROM journal_entries ORDER BY created_at DESC")
    fun getAllEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY created_at DESC")
    suspend fun getAllEntriesList(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE filename = :filename")
    suspend fun getEntry(filename: String): JournalEntryEntity?

    @Query("SELECT COUNT(*) FROM journal_entries WHERE date_key = :dateKey")
    suspend fun countByDate(dateKey: String): Int

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM journal_entries")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT date_key FROM journal_entries GROUP BY date_key ORDER BY date_key DESC")
    suspend fun getDistinctDates(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE filename = :filename")
    suspend fun deleteEntry(filename: String)

    @Query("SELECT * FROM journal_entries ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getEntriesPaged(limit: Int, offset: Int): List<JournalEntryEntity>
}
