package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DividerDao {

    @Query("SELECT * FROM dividers WHERE folderId IS NULL ORDER BY sortOrder ASC")
    fun getAllDividers(): LiveData<List<Divider>>

    @Query("SELECT * FROM dividers WHERE folderId IS NULL ORDER BY sortOrder ASC")
    suspend fun getAllDividersDirect(): List<Divider>

    @Query("SELECT * FROM dividers ORDER BY sortOrder ASC")
    suspend fun getAllDividersAllLevelsDirect(): List<Divider>

    @Query("SELECT * FROM dividers WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun getDividersInFolder(folderId: Int): LiveData<List<Divider>>

    @Query("SELECT * FROM dividers WHERE folderId = :folderId ORDER BY sortOrder ASC")
    suspend fun getDividersInFolderDirect(folderId: Int): List<Divider>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(divider: Divider)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(divider: Divider): Long

    @Update
    suspend fun update(divider: Divider)

    @Delete
    suspend fun delete(divider: Divider)

    @Query("SELECT * FROM dividers WHERE id = :id")
    suspend fun getDividerById(id: Int): Divider?

    @Query("DELETE FROM dividers")
    suspend fun deleteAll()
}

