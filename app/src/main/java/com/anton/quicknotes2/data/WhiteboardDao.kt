package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WhiteboardDao {

    @Query("SELECT * FROM whiteboards WHERE folderId IS NULL ORDER BY sortOrder ASC")
    fun getAllWhiteboards(): LiveData<List<Whiteboard>>

    @Query("SELECT * FROM whiteboards WHERE folderId IS NULL ORDER BY sortOrder ASC")
    suspend fun getAllWhiteboardsDirect(): List<Whiteboard>

    @Query("SELECT * FROM whiteboards WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun getWhiteboardsInFolder(folderId: Int): LiveData<List<Whiteboard>>

    @Query("SELECT * FROM whiteboards WHERE folderId = :folderId ORDER BY sortOrder ASC")
    suspend fun getWhiteboardsInFolderDirect(folderId: Int): List<Whiteboard>

    @Query("SELECT * FROM whiteboards WHERE id = :id")
    suspend fun getWhiteboardById(id: Int): Whiteboard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(whiteboard: Whiteboard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(whiteboard: Whiteboard): Long

    @Update
    suspend fun update(whiteboard: Whiteboard)

    @Delete
    suspend fun delete(whiteboard: Whiteboard)
}

