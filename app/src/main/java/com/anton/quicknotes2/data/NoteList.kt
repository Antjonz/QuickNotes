package com.anton.quicknotes2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_lists",
    foreignKeys = [ForeignKey(
        entity = Folder::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("folderId")]
)
data class NoteList(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val folderId: Int? = null,
    val sortOrder: Int = 0,
    val iconUri: String? = null
)

