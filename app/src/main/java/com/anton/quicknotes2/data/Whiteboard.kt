package com.anton.quicknotes2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whiteboards",
    foreignKeys = [ForeignKey(
        entity = Folder::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("folderId")]
)
data class Whiteboard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val strokesJson: String = "[]",   // serialised list of strokes
    val timestamp: Long = System.currentTimeMillis(),
    val folderId: Int? = null,
    val sortOrder: Int = 0,
    val iconUri: String? = null,
    val labelColor: String? = null
)

