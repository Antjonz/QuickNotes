package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY sortOrder ASC")
    fun getAllFolders(): LiveData<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Int): Folder?

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun getNotesInFolder(folderId: Int): LiveData<List<Note>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY sortOrder ASC")
    suspend fun getAllFoldersDirect(): List<Folder>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY sortOrder ASC")
    fun getSubFolders(parentId: Int): LiveData<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY sortOrder ASC")
    suspend fun getSubFoldersDirect(parentId: Int): List<Folder>

    @Query("SELECT * FROM folders ORDER BY sortOrder ASC")
    suspend fun getAllFoldersAllLevelsDirect(): List<Folder>
}
