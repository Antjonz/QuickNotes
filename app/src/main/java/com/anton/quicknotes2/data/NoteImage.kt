package com.anton.quicknotes2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_images",
    foreignKeys = [ForeignKey(
        entity = Note::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class NoteImage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val noteId: Int,
    val uri: String,          // content URI or file URI stored as string
    val sortOrder: Int = 0
)

