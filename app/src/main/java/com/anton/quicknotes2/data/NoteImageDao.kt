package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface NoteImageDao {

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY sortOrder ASC")
    fun getImagesForNote(noteId: Int): LiveData<List<NoteImage>>

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY sortOrder ASC")
    suspend fun getImagesForNoteDirect(noteId: Int): List<NoteImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: NoteImage)

    @Update
    suspend fun update(image: NoteImage)

    @Delete
    suspend fun delete(image: NoteImage)

    @Query("SELECT * FROM note_images ORDER BY sortOrder ASC")
    suspend fun getAllImagesDirect(): List<NoteImage>

    @Query("DELETE FROM note_images")
    suspend fun deleteAll()
}

