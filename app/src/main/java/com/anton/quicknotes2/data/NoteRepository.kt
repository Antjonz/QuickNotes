package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import com.anton.quicknotes2.ui.HomeItem

class NoteRepository(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val noteImageDao: NoteImageDao
) {

    // ── Notes ──────────────────────────────────────────────
    val allNotes: LiveData<List<Note>> = noteDao.getAllNotes()

    suspend fun insert(note: Note) = noteDao.insert(note)
    suspend fun update(note: Note) = noteDao.update(note)
    suspend fun delete(note: Note) = noteDao.delete(note)
    suspend fun getNoteById(id: Int): Note? = noteDao.getNoteById(id)
    suspend fun updateNoteIcon(noteId: Int, uri: String?) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.update(note.copy(iconUri = uri))
    }

    fun getNotesInFolder(folderId: Int): LiveData<List<Note>> =
        folderDao.getNotesInFolder(folderId)

    suspend fun reorderHomeItems(items: List<HomeItem>) {
        items.forEachIndexed { index, item ->
            when (item) {
                is HomeItem.NoteItem -> noteDao.update(item.note.copy(sortOrder = index))
                is HomeItem.FolderItem -> folderDao.update(item.folder.copy(sortOrder = index))
            }
        }
    }

    suspend fun reorderNotesInFolder(notes: List<Note>) {
        notes.forEachIndexed { index, note ->
            noteDao.update(note.copy(sortOrder = index))
        }
    }

    // ── Folders ────────────────────────────────────────────
    val allFolders: LiveData<List<Folder>> = folderDao.getAllFolders()

    suspend fun insertFolder(folder: Folder): Long {
        val maxOrder = folderDao.getAllFoldersDirect().maxOfOrNull { it.sortOrder } ?: -1
        return folderDao.insertAndGetId(folder.copy(sortOrder = maxOrder + 1))
    }
    suspend fun updateFolder(folder: Folder) = folderDao.update(folder)
    suspend fun deleteFolder(folder: Folder) = folderDao.delete(folder)
    suspend fun getFolderById(id: Int): Folder? = folderDao.getFolderById(id)

    /** Delete folder and all notes inside it. */
    suspend fun deleteFolderAndNotes(folder: Folder) {
        noteDao.getNotesInFolderDirect(folder.id).forEach { noteDao.delete(it) }
        folderDao.delete(folder)
    }

    /** Delete folder but move its notes to the home screen. */
    suspend fun deleteFolderMoveNotesOut(folder: Folder) {
        val maxOrder = maxOf(
            noteDao.getAllNotesDirect().maxOfOrNull { it.sortOrder } ?: -1,
            folderDao.getAllFoldersDirect().maxOfOrNull { it.sortOrder } ?: -1
        )
        noteDao.getNotesInFolderDirect(folder.id).forEachIndexed { i, note ->
            noteDao.update(note.copy(folderId = null, sortOrder = maxOrder + 1 + i))
        }
        folderDao.delete(folder)
    }

    suspend fun getNotesInFolderCount(folderId: Int): Int =
        noteDao.getNotesInFolderDirect(folderId).size
    suspend fun updateFolderIcon(folderId: Int, uri: String?) {
        val folder = folderDao.getFolderById(folderId) ?: return
        folderDao.update(folder.copy(iconUri = uri))
    }

    suspend fun insertNoteWithOrder(note: Note): Long {
        val maxOrder = if (note.folderId == null) {
            val maxNote = noteDao.getAllNotesDirect().maxOfOrNull { it.sortOrder } ?: -1
            val maxFolder = folderDao.getAllFoldersDirect().maxOfOrNull { it.sortOrder } ?: -1
            maxOf(maxNote, maxFolder)
        } else {
            noteDao.getNotesInFolderDirect(note.folderId).maxOfOrNull { it.sortOrder } ?: -1
        }
        return noteDao.insertAndGetId(note.copy(sortOrder = maxOrder + 1))
    }

    // ── Images ─────────────────────────────────────────────
    fun getImagesForNote(noteId: Int): LiveData<List<NoteImage>> =
        noteImageDao.getImagesForNote(noteId)

    suspend fun insertImage(image: NoteImage) {
        val maxOrder = noteImageDao.getImagesForNoteDirect(image.noteId)
            .maxOfOrNull { it.sortOrder } ?: -1
        noteImageDao.insert(image.copy(sortOrder = maxOrder + 1))
    }

    suspend fun deleteImage(image: NoteImage) = noteImageDao.delete(image)

    suspend fun reorderImages(images: List<NoteImage>) {
        images.forEachIndexed { index, img ->
            noteImageDao.update(img.copy(sortOrder = index))
        }
    }
}

