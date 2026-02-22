package com.anton.quicknotes2.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.Folder
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.data.NoteList
import com.anton.quicknotes2.data.Whiteboard
import com.anton.quicknotes2.databinding.ItemFolderBinding
import com.anton.quicknotes2.databinding.ItemNoteBinding
import com.anton.quicknotes2.databinding.ItemWhiteboardBinding
import java.text.SimpleDateFormat
import java.util.*

sealed class FolderItem {
    data class NoteItem(val note: Note) : FolderItem()
    data class WhiteboardItem(val wb: Whiteboard) : FolderItem()
    data class SubFolderItem(val folder: Folder) : FolderItem()
    data class ListItem(val noteList: NoteList) : FolderItem()
}

class FolderAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onNoteDelete: (Note) -> Unit,
    private val onOrderChanged: (List<Note>) -> Unit,
    private val onNoteIconClick: (Note) -> Unit = {},
    val onMoveOutOfFolder: (Note) -> Unit = {},
    private val onWhiteboardClick: (Whiteboard) -> Unit = {},
    private val onWhiteboardDelete: (Whiteboard) -> Unit = {},
    private val onWhiteboardIconClick: (Whiteboard) -> Unit = {},
    private val onSubFolderClick: (Folder) -> Unit = {},
    private val onSubFolderDelete: (Folder) -> Unit = {},
    private val onSubFolderIconClick: (Folder) -> Unit = {},
    private val onListClick: (NoteList) -> Unit = {},
    private val onListDelete: (NoteList) -> Unit = {},
    private val onListIconClick: (NoteList) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_DRAG_OUT = 0
        const val TYPE_NOTE = 1
        const val TYPE_CANCEL = 2
        const val TYPE_WHITEBOARD = 3
        const val TYPE_FOLDER = 4
        const val TYPE_LIST = 5
    }

    // Mixed list of notes and whiteboards
    private val items = mutableListOf<FolderItem>()
    var itemTouchHelper: ItemTouchHelper? = null

    var isDragging: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            val cancelPos = items.size + 1
            if (value) notifyItemInserted(cancelPos)
            else notifyItemRemoved(cancelPos)
        }

    fun submitMixed(notes: List<Note>, whiteboards: List<Whiteboard>, subFolders: List<Folder> = emptyList(), lists: List<NoteList> = emptyList()) {
        val newItems = mutableListOf<FolderItem>()
        notes.forEach { newItems.add(FolderItem.NoteItem(it)) }
        whiteboards.forEach { newItems.add(FolderItem.WhiteboardItem(it)) }
        subFolders.forEach { newItems.add(FolderItem.SubFolderItem(it)) }
        lists.forEach { newItems.add(FolderItem.ListItem(it)) }
        newItems.sortBy {
            when (it) {
                is FolderItem.NoteItem -> it.note.sortOrder
                is FolderItem.WhiteboardItem -> it.wb.sortOrder
                is FolderItem.SubFolderItem -> it.folder.sortOrder
                is FolderItem.ListItem -> it.noteList.sortOrder
            }
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val old = items[o]; val new = newItems[n]
                return when {
                    old is FolderItem.NoteItem && new is FolderItem.NoteItem -> old.note.id == new.note.id
                    old is FolderItem.WhiteboardItem && new is FolderItem.WhiteboardItem -> old.wb.id == new.wb.id
                    old is FolderItem.SubFolderItem && new is FolderItem.SubFolderItem -> old.folder.id == new.folder.id
                    old is FolderItem.ListItem && new is FolderItem.ListItem -> old.noteList.id == new.noteList.id
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    // Legacy single-type submit (notes only) — kept for compatibility
    fun submitList(notes: List<Note>) = submitMixed(notes, emptyList())

    fun getItems(): List<Note> = items.filterIsInstance<FolderItem.NoteItem>().map { it.note }
    fun getAllItems(): List<FolderItem> = items.toList()

    fun moveItem(from: Int, to: Int) {
        val fromIdx = from - 1
        val toIdx = to - 1
        if (fromIdx < 0 || toIdx < 0 || fromIdx >= items.size || toIdx >= items.size) return
        if (fromIdx < toIdx) for (i in fromIdx until toIdx) Collections.swap(items, i, i + 1)
        else for (i in fromIdx downTo toIdx + 1) Collections.swap(items, i, i - 1)
        notifyItemMoved(from, to)
    }

    fun getNoteAt(position: Int): Note? =
        (items.getOrNull(position - 1) as? FolderItem.NoteItem)?.note

    /** Returns the note or whiteboard at adapter position (1-based, row 0 = drag-out header). */
    fun getFolderItemAt(position: Int): FolderItem? = items.getOrNull(position - 1)

    override fun getItemCount() = items.size + 1 + if (isDragging) 1 else 0

    override fun getItemViewType(position: Int): Int {
        if (position == 0) return TYPE_DRAG_OUT
        if (isDragging && position == items.size + 1) return TYPE_CANCEL
        return when (items[position - 1]) {
            is FolderItem.NoteItem -> TYPE_NOTE
            is FolderItem.WhiteboardItem -> TYPE_WHITEBOARD
            is FolderItem.SubFolderItem -> TYPE_FOLDER
            is FolderItem.ListItem -> TYPE_LIST
        }
    }

    inner class DragOutViewHolder(root: android.view.View) : RecyclerView.ViewHolder(root)

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note) {
            binding.textTitle.text = note.title.ifBlank { "Untitled" }
            binding.textBody.text = note.body
            binding.textTimestamp.text = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(note.timestamp))
            binding.root.setOnClickListener { onNoteClick(note) }
            binding.btnDelete.setOnClickListener { onNoteDelete(note) }
            binding.itemIcon.setOnClickListener { onNoteIconClick(note) }
            if (note.iconUri != null) binding.itemIcon.setImageURI(Uri.parse(note.iconUri))
            else binding.itemIcon.setImageResource(com.anton.quicknotes2.R.drawable.ic_note_default)
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
        }
    }

    inner class WhiteboardViewHolder(private val binding: ItemWhiteboardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(wb: Whiteboard) {
            binding.textTitle.text = wb.title.ifBlank { "Untitled whiteboard" }
            binding.textTimestamp.text = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(wb.timestamp))
            binding.root.setOnClickListener { onWhiteboardClick(wb) }
            binding.btnDelete.setOnClickListener { onWhiteboardDelete(wb) }
            binding.itemIcon.setOnClickListener { onWhiteboardIconClick(wb) }
            if (wb.iconUri != null) binding.itemIcon.setImageURI(Uri.parse(wb.iconUri))
            else binding.itemIcon.setImageResource(com.anton.quicknotes2.R.drawable.ic_note_default)
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
        }
    }

    inner class SubFolderViewHolder(private val binding: ItemFolderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: Folder) {
            binding.textFolderName.text = folder.name
            binding.root.setOnClickListener { onSubFolderClick(folder) }
            binding.btnDelete.setOnClickListener { onSubFolderDelete(folder) }
            binding.itemIcon.setOnClickListener { onSubFolderIconClick(folder) }
            if (folder.iconUri != null) binding.itemIcon.setImageURI(Uri.parse(folder.iconUri))
            else binding.itemIcon.setImageResource(com.anton.quicknotes2.R.drawable.ic_folder_default)
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
        }
    }

    inner class ListViewHolder(private val binding: ItemWhiteboardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(list: NoteList) {
            binding.textTitle.text = list.title.ifBlank { "Untitled list" }
            binding.textTimestamp.text = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(list.timestamp))
            binding.root.setOnClickListener { onListClick(list) }
            binding.btnDelete.setOnClickListener { onListDelete(list) }
            binding.itemIcon.setOnClickListener { onListIconClick(list) }
            if (list.iconUri != null) binding.itemIcon.setImageURI(Uri.parse(list.iconUri))
            else binding.itemIcon.setImageResource(com.anton.quicknotes2.R.drawable.ic_note_default)
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DRAG_OUT -> DragOutViewHolder(
                inflater.inflate(com.anton.quicknotes2.R.layout.item_drag_out, parent, false)
            )
            TYPE_CANCEL -> object : RecyclerView.ViewHolder(
                inflater.inflate(com.anton.quicknotes2.R.layout.item_drag_cancel, parent, false)
            ) {}
            TYPE_WHITEBOARD -> WhiteboardViewHolder(ItemWhiteboardBinding.inflate(inflater, parent, false))
            TYPE_FOLDER -> SubFolderViewHolder(ItemFolderBinding.inflate(inflater, parent, false))
            TYPE_LIST -> ListViewHolder(ItemWhiteboardBinding.inflate(inflater, parent, false))
            else -> NoteViewHolder(ItemNoteBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position == 0 || (isDragging && position == items.size + 1)) return
        when (val item = items[position - 1]) {
            is FolderItem.NoteItem -> (holder as? NoteViewHolder)?.bind(item.note)
            is FolderItem.WhiteboardItem -> (holder as? WhiteboardViewHolder)?.bind(item.wb)
            is FolderItem.SubFolderItem -> (holder as? SubFolderViewHolder)?.bind(item.folder)
            is FolderItem.ListItem -> (holder as? ListViewHolder)?.bind(item.noteList)
        }
    }
}
