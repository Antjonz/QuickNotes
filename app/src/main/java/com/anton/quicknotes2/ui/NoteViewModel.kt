package com.anton.quicknotes2.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anton.quicknotes2.data.Folder
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.data.NoteImage
import com.anton.quicknotes2.data.NoteRepository
import com.anton.quicknotes2.data.Whiteboard
import kotlinx.coroutines.launch

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    // ── Notes ──────────────────────────────────────────────
    val allNotes: LiveData<List<Note>> = repository.allNotes

    fun insert(note: Note) = viewModelScope.launch { repository.insertNoteWithOrder(note) }
    fun update(note: Note) = viewModelScope.launch { repository.update(note) }
    fun delete(note: Note) = viewModelScope.launch { repository.delete(note) }
    suspend fun getNoteById(id: Int): Note? = repository.getNoteById(id)
    fun updateNoteIcon(noteId: Int, uri: String?) =
        viewModelScope.launch { repository.updateNoteIcon(noteId, uri) }

    fun getNotesInFolder(folderId: Int): LiveData<List<Note>> =
        repository.getNotesInFolder(folderId)


    fun reorderNotesInFolder(notes: List<Note>) =
        viewModelScope.launch { repository.reorderNotesInFolder(notes) }

    fun reorderFolderContents(items: List<com.anton.quicknotes2.ui.FolderItem>) =
        viewModelScope.launch { repository.reorderFolderContents(items) }

    // ── Folders ────────────────────────────────────────────
    val allFolders: LiveData<List<Folder>> = repository.allFolders

    fun getSubFolders(parentId: Int) = repository.getSubFolders(parentId)

    fun insertFolder(folder: Folder) = viewModelScope.launch { repository.insertFolder(folder) }
    fun updateFolder(folder: Folder) = viewModelScope.launch { repository.updateFolder(folder) }
    fun deleteFolder(folder: Folder) = viewModelScope.launch { repository.deleteFolder(folder) }
    fun deleteFolderAndNotes(folder: Folder) = viewModelScope.launch { repository.deleteFolderAndNotes(folder) }
    fun deleteFolderMoveNotesOut(folder: Folder) = viewModelScope.launch { repository.deleteFolderMoveNotesOut(folder) }
    suspend fun getNotesInFolderCount(folderId: Int): Int = repository.getNotesInFolderCount(folderId)
    suspend fun getFolderItemCount(folderId: Int): Int = repository.getFolderItemCount(folderId)
    suspend fun getFolderById(id: Int): Folder? = repository.getFolderById(id)
    fun updateFolderIcon(folderId: Int, uri: String?) =
        viewModelScope.launch { repository.updateFolderIcon(folderId, uri) }

    // ── Images ─────────────────────────────────────────────
    fun getImagesForNote(noteId: Int): LiveData<List<NoteImage>> =
        repository.getImagesForNote(noteId)

    fun insertImage(image: NoteImage) = viewModelScope.launch { repository.insertImage(image) }
    fun deleteImage(image: NoteImage) = viewModelScope.launch { repository.deleteImage(image) }
    fun reorderImages(images: List<NoteImage>) =
        viewModelScope.launch { repository.reorderImages(images) }

    // ── Whiteboards ────────────────────────────────────────
    val allWhiteboards = repository.allWhiteboards
    fun getWhiteboardsInFolder(folderId: Int) = repository.getWhiteboardsInFolder(folderId)
    suspend fun getWhiteboardById(id: Int) = repository.getWhiteboardById(id)
    suspend fun insertWhiteboard(wb: Whiteboard) = repository.insertWhiteboard(wb)
    suspend fun insertWhiteboardInFolder(wb: Whiteboard) = repository.insertWhiteboardInFolder(wb)
    fun updateWhiteboard(wb: Whiteboard) = viewModelScope.launch { repository.updateWhiteboard(wb) }
    fun deleteWhiteboard(wb: Whiteboard) = viewModelScope.launch { repository.deleteWhiteboard(wb) }
    fun updateWhiteboardIcon(id: Int, uri: String?) = viewModelScope.launch { repository.updateWhiteboardIcon(id, uri) }
    fun reorderHomeItems(items: List<HomeItem>) =
        viewModelScope.launch { repository.reorderHomeItemsWithWhiteboards(items) }
}

