package com.anton.quicknotes2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dividers",
    foreignKeys = [ForeignKey(
        entity = Folder::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("folderId")]
)
data class Divider(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val label: String = "",
    val folderId: Int? = null,
    val sortOrder: Int = 0
)

