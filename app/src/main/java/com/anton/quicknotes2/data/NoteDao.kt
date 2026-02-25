package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE folderId IS NULL ORDER BY sortOrder ASC")
    fun getAllNotes(): LiveData<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): Note?

    @Query("SELECT * FROM notes WHERE folderId IS NULL ORDER BY sortOrder ASC")
    suspend fun getAllNotesDirect(): List<Note>

    @Query("SELECT * FROM notes ORDER BY sortOrder ASC")
    suspend fun getAllNotesAllLevelsDirect(): List<Note>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY sortOrder ASC")
    suspend fun getNotesInFolderDirect(folderId: Int): List<Note>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
