package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface NoteListDao {

    // ── NoteList ──────────────────────────────────────────
    @Query("SELECT * FROM note_lists WHERE folderId IS NULL ORDER BY sortOrder ASC")
    fun getAllLists(): LiveData<List<NoteList>>

    @Query("SELECT * FROM note_lists WHERE folderId IS NULL ORDER BY sortOrder ASC")
    suspend fun getAllListsDirect(): List<NoteList>

    @Query("SELECT * FROM note_lists WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun getListsInFolder(folderId: Int): LiveData<List<NoteList>>

    @Query("SELECT * FROM note_lists WHERE folderId = :folderId ORDER BY sortOrder ASC")
    suspend fun getListsInFolderDirect(folderId: Int): List<NoteList>

    @Query("SELECT * FROM note_lists WHERE id = :id")
    suspend fun getListById(id: Int): NoteList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(list: NoteList): Long

    @Update
    suspend fun update(list: NoteList)

    @Delete
    suspend fun delete(list: NoteList)

    // ── NoteListItem ──────────────────────────────────────
    @Query("SELECT * FROM note_list_items WHERE listId = :listId ORDER BY checked ASC, position ASC")
    fun getItemsForList(listId: Int): LiveData<List<NoteListItem>>

    @Query("SELECT * FROM note_list_items WHERE listId = :listId ORDER BY checked ASC, position ASC")
    suspend fun getItemsForListDirect(listId: Int): List<NoteListItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: NoteListItem): Long

    @Update
    suspend fun updateItem(item: NoteListItem)

    @Delete
    suspend fun deleteItem(item: NoteListItem)
}

