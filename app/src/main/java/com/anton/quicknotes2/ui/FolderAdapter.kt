package com.anton.quicknotes2.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.*

class FolderAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onNoteDelete: (Note) -> Unit,
    private val onOrderChanged: (List<Note>) -> Unit,
    private val onNoteIconClick: (Note) -> Unit = {},
    val onMoveOutOfFolder: (Note) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_DRAG_OUT = 0
        const val TYPE_NOTE = 1
        const val TYPE_CANCEL = 2
    }

    private val items = mutableListOf<Note>()
    var itemTouchHelper: ItemTouchHelper? = null

    // Cancel row shown at bottom during drag
    var isDragging: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            val cancelPos = items.size + 1  // after drag-out header + all notes
            if (value) notifyItemInserted(cancelPos)
            else notifyItemRemoved(cancelPos)
        }

    fun submitList(newItems: List<Note>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun getItems(): List<Note> = items.toList()

    // position in adapter = index+1 because row 0 is the drag-out header
    fun moveItem(from: Int, to: Int) {
        val fromIdx = from - 1
        val toIdx = to - 1
        if (fromIdx < 0 || toIdx < 0) return
        if (fromIdx < toIdx) for (i in fromIdx until toIdx) Collections.swap(items, i, i + 1)
        else for (i in fromIdx downTo toIdx + 1) Collections.swap(items, i, i - 1)
        notifyItemMoved(from, to)
    }

    fun getNoteAt(position: Int): Note? = items.getOrNull(position - 1)

    // Total = 1 (drag-out) + notes + 1 (cancel, only during drag)
    override fun getItemCount() = items.size + 1 + if (isDragging) 1 else 0

    override fun getItemViewType(position: Int): Int {
        if (position == 0) return TYPE_DRAG_OUT
        if (isDragging && position == items.size + 1) return TYPE_CANCEL
        return TYPE_NOTE
    }

    inner class DragOutViewHolder(root: android.view.View) : RecyclerView.ViewHolder(root)

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note) {
            binding.textTitle.text = note.title.ifBlank { "Untitled" }
            binding.textBody.text = note.body
            binding.textTimestamp.text =
                SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(note.timestamp))
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DRAG_OUT -> DragOutViewHolder(
                inflater.inflate(com.anton.quicknotes2.R.layout.item_drag_out, parent, false)
            )
            TYPE_CANCEL -> object : RecyclerView.ViewHolder(
                inflater.inflate(com.anton.quicknotes2.R.layout.item_drag_cancel, parent, false)
            ) {}
            else -> NoteViewHolder(ItemNoteBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is NoteViewHolder) holder.bind(items[position - 1])
    }
}
