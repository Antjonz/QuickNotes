package com.anton.quicknotes2.ui

import com.anton.quicknotes2.data.Folder
import com.anton.quicknotes2.data.Note

sealed class HomeItem {
    data class NoteItem(val note: Note) : HomeItem()
    data class FolderItem(val folder: Folder) : HomeItem()
}

