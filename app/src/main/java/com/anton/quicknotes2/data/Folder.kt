package com.anton.quicknotes2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val iconUri: String? = null
)

