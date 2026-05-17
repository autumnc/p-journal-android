package com.pjournal.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JournalEntryEntity::class, SyncLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN checksum TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        filename TEXT NOT NULL,
                        operation_type TEXT NOT NULL,
                        source_device_id TEXT NOT NULL,
                        target_device_id TEXT NOT NULL,
                        checksum TEXT,
                        timestamp INTEGER NOT NULL,
                        success INTEGER NOT NULL DEFAULT 1,
                        details TEXT
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE journal_entries SET filename = filename || '.txt' WHERE filename NOT LIKE '%.txt'")
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "pjournal.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
