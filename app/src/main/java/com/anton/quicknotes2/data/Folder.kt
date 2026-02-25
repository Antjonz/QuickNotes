package com.anton.quicknotes2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [ForeignKey(
        entity = Folder::class,
        parentColumns = ["id"],
        childColumns = ["parentFolderId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("parentFolderId")]
)
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val iconUri: String? = null,
    val parentFolderId: Int? = null,
    val labelColor: String? = null
)

