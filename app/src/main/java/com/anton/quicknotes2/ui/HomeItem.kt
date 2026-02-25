package com.anton.quicknotes2.ui

import com.anton.quicknotes2.data.Divider
import com.anton.quicknotes2.data.Folder
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.data.NoteList
import com.anton.quicknotes2.data.Whiteboard

sealed class HomeItem {
    data class NoteItem(val note: Note) : HomeItem()
    data class FolderItem(val folder: Folder) : HomeItem()
    data class WhiteboardItem(val whiteboard: Whiteboard) : HomeItem()
    data class ListItem(val noteList: NoteList) : HomeItem()
    data class DividerItem(val divider: Divider) : HomeItem()
}
