package com.pjournal.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "filename")
    val filename: String,

    @ColumnInfo(name = "operation_type")
    val operationType: String,

    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String,

    @ColumnInfo(name = "target_device_id")
    val targetDeviceId: String,

    @ColumnInfo(name = "checksum")
    val checksum: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "success")
    val success: Boolean = true,

    @ColumnInfo(name = "details")
    val details: String? = null
)
