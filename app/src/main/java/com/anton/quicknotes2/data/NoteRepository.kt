package com.anton.quicknotes2.data

import androidx.lifecycle.LiveData
import com.anton.quicknotes2.ui.HomeItem

class NoteRepository(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val noteImageDao: NoteImageDao,
    private val whiteboardDao: WhiteboardDao? = null
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


    suspend fun reorderNotesInFolder(notes: List<Note>) {
        notes.forEachIndexed { index, note ->
            noteDao.update(note.copy(sortOrder = index))
        }
    }

    /** Save sort order for any mix of notes, whiteboards, and folders inside a folder. */
    suspend fun reorderFolderContents(items: List<com.anton.quicknotes2.ui.FolderItem>) {
        items.forEachIndexed { index, item ->
            when (item) {
                is com.anton.quicknotes2.ui.FolderItem.NoteItem ->
                    noteDao.update(item.note.copy(sortOrder = index))
                is com.anton.quicknotes2.ui.FolderItem.WhiteboardItem ->
                    whiteboardDao?.update(item.wb.copy(sortOrder = index))
                is com.anton.quicknotes2.ui.FolderItem.SubFolderItem ->
                    folderDao.update(item.folder.copy(sortOrder = index))
            }
        }
    }

    // ── Folders ────────────────────────────────────────────
    val allFolders: LiveData<List<Folder>> = folderDao.getAllFolders()

    fun getSubFolders(parentId: Int): LiveData<List<Folder>> = folderDao.getSubFolders(parentId)

    suspend fun insertFolder(folder: Folder): Long {
        val parentId = folder.parentFolderId
        val maxOrder = if (parentId == null) {
            maxOf(
                folderDao.getAllFoldersDirect().maxOfOrNull { it.sortOrder } ?: -1,
                noteDao.getAllNotesDirect().maxOfOrNull { it.sortOrder } ?: -1,
                whiteboardDao?.getAllWhiteboardsDirect()?.maxOfOrNull { it.sortOrder } ?: -1
            )
        } else {
            maxOf(
                folderDao.getSubFoldersDirect(parentId).maxOfOrNull { it.sortOrder } ?: -1,
                noteDao.getNotesInFolderDirect(parentId).maxOfOrNull { it.sortOrder } ?: -1,
                whiteboardDao?.getWhiteboardsInFolderDirect(parentId)?.maxOfOrNull { it.sortOrder } ?: -1
            )
        }
        return folderDao.insertAndGetId(folder.copy(sortOrder = maxOrder + 1))
    }

    suspend fun updateFolder(folder: Folder) = folderDao.update(folder)
    suspend fun deleteFolder(folder: Folder) = folderDao.delete(folder)
    suspend fun getFolderById(id: Int): Folder? = folderDao.getFolderById(id)

    /** Recursively collect all descendant folder IDs of a folder. */
    private suspend fun collectDescendantIds(folderId: Int): List<Int> {
        val result = mutableListOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(folderId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            folderDao.getSubFoldersDirect(current).forEach { queue.add(it.id) }
        }
        return result
    }

    /** Delete folder and ALL descendants + their notes + whiteboards. */
    suspend fun deleteFolderAndNotes(folder: Folder) {
        val ids = collectDescendantIds(folder.id)
        for (id in ids) {
            noteDao.getNotesInFolderDirect(id).forEach { noteDao.delete(it) }
            whiteboardDao?.getWhiteboardsInFolderDirect(id)?.forEach { whiteboardDao.delete(it) }
        }
        // delete folders deepest-first to satisfy FK constraints
        for (id in ids.reversed()) {
            folderDao.getFolderById(id)?.let { folderDao.delete(it) }
        }
    }

    /** Delete folder but move its direct notes + whiteboards + subfolders to parent (or root). */
    suspend fun deleteFolderMoveNotesOut(folder: Folder) {
        val newParent = folder.parentFolderId  // move contents to grandparent (or root)
        val maxOrder = if (newParent == null) {
            maxOf(
                noteDao.getAllNotesDirect().maxOfOrNull { it.sortOrder } ?: -1,
                folderDao.getAllFoldersDirect().maxOfOrNull { it.sortOrder } ?: -1,
                whiteboardDao?.getAllWhiteboardsDirect()?.maxOfOrNull { it.sortOrder } ?: -1
            )
        } else {
            maxOf(
                noteDao.getNotesInFolderDirect(newParent).maxOfOrNull { it.sortOrder } ?: -1,
                folderDao.getSubFoldersDirect(newParent).maxOfOrNull { it.sortOrder } ?: -1,
                whiteboardDao?.getWhiteboardsInFolderDirect(newParent)?.maxOfOrNull { it.sortOrder } ?: -1
            )
        }
        var offset = 0
        noteDao.getNotesInFolderDirect(folder.id).forEach { note ->
            noteDao.update(note.copy(folderId = newParent, sortOrder = maxOrder + 1 + offset++))
        }
        whiteboardDao?.getWhiteboardsInFolderDirect(folder.id)?.forEach { wb ->
            whiteboardDao.update(wb.copy(folderId = newParent, sortOrder = maxOrder + 1 + offset++))
        }
        folderDao.getSubFoldersDirect(folder.id).forEach { sub ->
            folderDao.update(sub.copy(parentFolderId = newParent, sortOrder = maxOrder + 1 + offset++))
        }
        folderDao.delete(folder)
    }

    /** Count of direct notes + whiteboards + subfolders inside a folder. */
    suspend fun getFolderItemCount(folderId: Int): Int =
        noteDao.getNotesInFolderDirect(folderId).size +
        (whiteboardDao?.getWhiteboardsInFolderDirect(folderId)?.size ?: 0) +
        folderDao.getSubFoldersDirect(folderId).size

    suspend fun getNotesInFolderCount(folderId: Int): Int =
        noteDao.getNotesInFolderDirect(folderId).size
    suspend fun updateFolderIcon(folderId: Int, uri: String?) {
        val folder = folderDao.getFolderById(folderId) ?: return
        folderDao.update(folder.copy(iconUri = uri))
    }

    suspend fun insertNoteWithOrder(note: Note): Long {
        val maxOrder = if (note.folderId == null) {
            maxOf(
                noteDao.getAllNotesDirect().maxOfOrNull { it.sortOrder } ?: -1,
                folderDao.getAllFoldersDirect().maxOfOrNull { it.sortOrder } ?: -1,
                whiteboardDao?.getAllWhiteboardsDirect()?.maxOfOrNull { it.sortOrder } ?: -1
            )
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

    // ── Whiteboards ────────────────────────────────────────
    val allWhiteboards: LiveData<List<Whiteboard>>
        get() = whiteboardDao!!.getAllWhiteboards()

    fun getWhiteboardsInFolder(folderId: Int): LiveData<List<Whiteboard>> =
        whiteboardDao!!.getWhiteboardsInFolder(folderId)

    suspend fun getWhiteboardById(id: Int): Whiteboard? = whiteboardDao!!.getWhiteboardById(id)

    suspend fun insertWhiteboard(wb: Whiteboard): Long {
        val maxOrder = maxOf(
            noteDao.getAllNotesDirect().maxOfOrNull { it.sortOrder } ?: -1,
            folderDao.getAllFoldersDirect().maxOfOrNull { it.sortOrder } ?: -1,
            whiteboardDao!!.getAllWhiteboardsDirect().maxOfOrNull { it.sortOrder } ?: -1
        )
        return whiteboardDao.insertAndGetId(wb.copy(sortOrder = maxOrder + 1))
    }

    suspend fun insertWhiteboardInFolder(wb: Whiteboard): Long {
        val fid = wb.folderId ?: return whiteboardDao!!.insertAndGetId(wb)
        val maxOrder = maxOf(
            noteDao.getNotesInFolderDirect(fid).maxOfOrNull { it.sortOrder } ?: -1,
            whiteboardDao!!.getWhiteboardsInFolderDirect(fid).maxOfOrNull { it.sortOrder } ?: -1
        )
        return whiteboardDao.insertAndGetId(wb.copy(sortOrder = maxOrder + 1))
    }

    suspend fun updateWhiteboard(wb: Whiteboard) = whiteboardDao!!.update(wb)
    suspend fun deleteWhiteboard(wb: Whiteboard) = whiteboardDao!!.delete(wb)

    suspend fun updateWhiteboardIcon(id: Int, uri: String?) {
        val wb = whiteboardDao!!.getWhiteboardById(id) ?: return
        whiteboardDao.update(wb.copy(iconUri = uri))
    }

    suspend fun reorderHomeItemsWithWhiteboards(items: List<HomeItem>) {
        items.forEachIndexed { index, item ->
            when (item) {
                is HomeItem.NoteItem -> noteDao.update(item.note.copy(sortOrder = index))
                is HomeItem.FolderItem -> folderDao.update(item.folder.copy(sortOrder = index))
                is HomeItem.WhiteboardItem -> whiteboardDao!!.update(item.whiteboard.copy(sortOrder = index))
            }
        }
    }

    suspend fun reorderFolderItemsWithWhiteboards(items: List<Any>) {
        items.forEachIndexed { index, item ->
            when (item) {
                is Note -> noteDao.update(item.copy(sortOrder = index))
                is Whiteboard -> whiteboardDao!!.update(item.copy(sortOrder = index))
            }
        }
    }
}

