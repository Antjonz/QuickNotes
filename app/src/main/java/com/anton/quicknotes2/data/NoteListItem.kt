package com.anton.quicknotes2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A single item (row) inside a NoteList. */
@Entity(
    tableName = "note_list_items",
    foreignKeys = [ForeignKey(
        entity = NoteList::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("listId")]
)
data class NoteListItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val listId: Int,
    val text: String = "",
    /** 0-based position among unchecked items — determines the display number */
    val position: Int = 0,
    val checked: Boolean = false
)

