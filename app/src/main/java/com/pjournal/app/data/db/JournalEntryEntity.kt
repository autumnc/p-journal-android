package com.pjournal.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "filename")
    val filename: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "prompt")
    val prompt: String? = null,

    @ColumnInfo(name = "word_count")
    val wordCount: Int = 0,

    @ColumnInfo(name = "date_key")
    val dateKey: String,

    @ColumnInfo(name = "checksum")
    val checksum: String? = null
)
