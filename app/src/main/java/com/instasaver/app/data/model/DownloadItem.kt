package com.instasaver.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val mediaStoreUri: String,
    val sourceUrl: String,
    val sizeBytes: Long,
    val durationMillis: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null
)
