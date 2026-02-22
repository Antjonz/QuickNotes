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
import com.anton.quicknotes2.databinding.ItemFolderBinding
import com.anton.quicknotes2.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.*

class HomeAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onFolderClick: (Folder) -> Unit,
    private val onNoteDelete: (Note) -> Unit,
    private val onFolderDelete: (Folder) -> Unit,
    private val onOrderChanged: (List<HomeItem>) -> Unit,
    private val onNoteIconClick: (Note) -> Unit,
    private val onFolderIconClick: (Folder) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<HomeItem>()
    var itemTouchHelper: ItemTouchHelper? = null

    // When true a cancel row is appended at the bottom
    var isDraggingNote: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(items.size)
            else notifyItemRemoved(items.size)
        }

    companion object {
        const val TYPE_NOTE = 0
        const val TYPE_FOLDER = 1
        const val TYPE_CANCEL = 2
    }

    fun submitList(newItems: List<HomeItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val old = items[o]; val new = newItems[n]
                return when {
                    old is HomeItem.NoteItem && new is HomeItem.NoteItem -> old.note.id == new.note.id
                    old is HomeItem.FolderItem && new is HomeItem.FolderItem -> old.folder.id == new.folder.id
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun getItems(): List<HomeItem> = items.toList()

    fun moveItem(from: Int, to: Int) {
        // Never move into/out of the cancel row
        if (from >= items.size || to >= items.size) return
        if (from < to) for (i in from until to) Collections.swap(items, i, i + 1)
        else for (i in from downTo to + 1) Collections.swap(items, i, i - 1)
        notifyItemMoved(from, to)
    }

    fun getItemAt(position: Int): HomeItem? =
        if (position >= 0 && position < items.size) items[position] else null

    override fun getItemCount() = items.size + if (isDraggingNote) 1 else 0

    override fun getItemViewType(position: Int): Int {
        if (isDraggingNote && position == items.size) return TYPE_CANCEL
        return when (items[position]) {
            is HomeItem.NoteItem -> TYPE_NOTE
            is HomeItem.FolderItem -> TYPE_FOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_NOTE -> NoteViewHolder(ItemNoteBinding.inflate(inflater, parent, false))
            TYPE_FOLDER -> FolderViewHolder(ItemFolderBinding.inflate(inflater, parent, false))
            else -> object : RecyclerView.ViewHolder(
                inflater.inflate(com.anton.quicknotes2.R.layout.item_drag_cancel, parent, false)
            ) {}
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_CANCEL) return
        when (val item = items[position]) {
            is HomeItem.NoteItem -> (holder as NoteViewHolder).bind(item.note)
            is HomeItem.FolderItem -> (holder as FolderViewHolder).bind(item.folder)
        }
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note) {
            binding.textTitle.text = note.title.ifBlank { "Untitled" }
            binding.textBody.text = note.body
            binding.textTimestamp.text = formatDate(note.timestamp)
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

    inner class FolderViewHolder(private val binding: ItemFolderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: Folder) {
            binding.textFolderName.text = folder.name
            binding.root.setOnClickListener { onFolderClick(folder) }
            binding.btnDelete.setOnClickListener { onFolderDelete(folder) }
            binding.itemIcon.setOnClickListener { onFolderIconClick(folder) }
            if (folder.iconUri != null) binding.itemIcon.setImageURI(Uri.parse(folder.iconUri))
            else binding.itemIcon.setImageResource(com.anton.quicknotes2.R.drawable.ic_folder_default)
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
        }
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(timestamp))
}
