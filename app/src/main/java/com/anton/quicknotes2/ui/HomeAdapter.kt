package com.anton.quicknotes2.ui

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.Divider
import com.anton.quicknotes2.data.Folder
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.data.NoteList
import com.anton.quicknotes2.data.Whiteboard
import com.anton.quicknotes2.databinding.ItemDividerBinding
import com.anton.quicknotes2.databinding.ItemFolderBinding
import com.anton.quicknotes2.databinding.ItemListBinding
import com.anton.quicknotes2.databinding.ItemNoteBinding
import com.anton.quicknotes2.databinding.ItemWhiteboardBinding
import java.text.SimpleDateFormat
import java.util.*

class HomeAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onFolderClick: (Folder) -> Unit,
    private val onNoteDelete: (Note) -> Unit,
    private val onFolderDelete: (Folder) -> Unit,
    private val onOrderChanged: (List<HomeItem>) -> Unit,
    private val onNoteIconClick: (Note) -> Unit,
    private val onFolderIconClick: (Folder) -> Unit,
    private val onWhiteboardClick: (Whiteboard) -> Unit = {},
    private val onWhiteboardDelete: (Whiteboard) -> Unit = {},
    private val onWhiteboardIconClick: (Whiteboard) -> Unit = {},
    private val onListClick: (NoteList) -> Unit = {},
    private val onListDelete: (NoteList) -> Unit = {},
    private val onListIconClick: (NoteList) -> Unit = {},
    private val onDividerDelete: (Divider) -> Unit = {},
    private val onDividerRename: (Divider) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<HomeItem>()
    var itemTouchHelper: ItemTouchHelper? = null

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
        const val TYPE_WHITEBOARD = 3
        const val TYPE_LIST = 4
        const val TYPE_DIVIDER = 5
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
                    old is HomeItem.WhiteboardItem && new is HomeItem.WhiteboardItem -> old.whiteboard.id == new.whiteboard.id
                    old is HomeItem.ListItem && new is HomeItem.ListItem -> old.noteList.id == new.noteList.id
                    old is HomeItem.DividerItem && new is HomeItem.DividerItem -> old.divider.id == new.divider.id
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    /** Skips DiffUtil — forces every visible card to rebind. Use on resume. */
    fun forceRefresh(newItems: List<HomeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
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
            is HomeItem.WhiteboardItem -> TYPE_WHITEBOARD
            is HomeItem.ListItem -> TYPE_LIST
            is HomeItem.DividerItem -> TYPE_DIVIDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_NOTE -> NoteViewHolder(ItemNoteBinding.inflate(inflater, parent, false))
            TYPE_FOLDER -> FolderViewHolder(ItemFolderBinding.inflate(inflater, parent, false))
            TYPE_WHITEBOARD -> WhiteboardViewHolder(ItemWhiteboardBinding.inflate(inflater, parent, false))
            TYPE_LIST -> ListViewHolder(ItemListBinding.inflate(inflater, parent, false))
            TYPE_DIVIDER -> DividerViewHolder(ItemDividerBinding.inflate(inflater, parent, false))
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
            is HomeItem.WhiteboardItem -> (holder as WhiteboardViewHolder).bind(item.whiteboard)
            is HomeItem.ListItem -> (holder as ListViewHolder).bind(item.noteList)
            is HomeItem.DividerItem -> (holder as DividerViewHolder).bind(item.divider)
        }
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note) {
            binding.textTitle.text = note.title.ifBlank { "Untitled" }
            binding.textBody.text = if (note.body.startsWith("<"))
                android.text.Html.fromHtml(note.body, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
            else note.body
            binding.textTimestamp.text = formatDate(note.timestamp)
            binding.root.setOnClickListener { onNoteClick(note) }
            binding.btnDelete.setOnClickListener { onNoteDelete(note) }
            binding.itemIcon.setOnClickListener { onNoteIconClick(note) }
            applyLabelColor(binding.root, binding.accentStrip, binding.textItemType,
                note.labelColor, Color.WHITE, Color.parseColor("#FF00897B"))
            applyIconUri(binding.itemIcon, note.iconUri, com.anton.quicknotes2.R.drawable.ic_note_default)
            binding.root.setOnLongClickListener { itemTouchHelper?.startDrag(this); true }
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
            if (folder.labelColor != null) {
                try { binding.root.setCardBackgroundColor(Color.parseColor(folder.labelColor)) } catch (_: Exception) {}
                binding.textFolderName.setTextColor(Color.BLACK)
            } else {
                binding.root.setCardBackgroundColor(Color.parseColor("#FF757575"))
                binding.textFolderName.setTextColor(Color.WHITE)
            }
            applyIconUri(binding.itemIcon, folder.iconUri, com.anton.quicknotes2.R.drawable.ic_folder_default)
            binding.root.setOnLongClickListener { itemTouchHelper?.startDrag(this); true }
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
            binding.textTimestamp.text = formatDate(wb.timestamp)
            binding.root.setOnClickListener { onWhiteboardClick(wb) }
            binding.btnDelete.setOnClickListener { onWhiteboardDelete(wb) }
            binding.itemIcon.setOnClickListener { onWhiteboardIconClick(wb) }
            applyLabelColor(binding.root, binding.accentStrip, binding.textItemType,
                wb.labelColor, Color.WHITE, Color.parseColor("#FF6650A4"))
            applyIconUri(binding.itemIcon, wb.iconUri, com.anton.quicknotes2.R.drawable.ic_whiteboard_default)
            binding.root.setOnLongClickListener { itemTouchHelper?.startDrag(this); true }
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
        }
    }

    inner class ListViewHolder(private val binding: ItemListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(list: NoteList) {
            binding.textTitle.text = list.title.ifBlank { "Untitled list" }
            binding.textTimestamp.text = formatDate(list.timestamp)
            binding.root.setOnClickListener { onListClick(list) }
            binding.btnDelete.setOnClickListener { onListDelete(list) }
            binding.itemIcon.setOnClickListener { onListIconClick(list) }
            applyLabelColor(binding.root, binding.accentStrip, binding.textItemType,
                list.labelColor, Color.WHITE, Color.parseColor("#FF1565C0"))
            applyIconUri(binding.itemIcon, list.iconUri, com.anton.quicknotes2.R.drawable.ic_list_default)
            binding.root.setOnLongClickListener { itemTouchHelper?.startDrag(this); true }
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
        }
    }

    inner class DividerViewHolder(private val binding: ItemDividerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(divider: Divider) {
            val hasLabel = divider.label.isNotBlank()
            binding.textDividerLabel.text = divider.label
            binding.labelRow.visibility = if (hasLabel) android.view.View.VISIBLE else android.view.View.GONE
            binding.labelDividerLine.visibility = if (hasLabel) android.view.View.VISIBLE else android.view.View.GONE
            binding.noLabelRow.visibility = if (hasLabel) android.view.View.GONE else android.view.View.VISIBLE
            binding.root.setOnClickListener { onDividerRename(divider) }
            binding.root.setOnLongClickListener { itemTouchHelper?.startDrag(this); true }
            val dragListener = android.view.View.OnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(this)
                false
            }
            binding.dragHandle.setOnTouchListener(dragListener)
            binding.dragHandleNoLabel.setOnTouchListener(dragListener)
        }
    }

    private fun applyLabelColor(
        card: com.google.android.material.card.MaterialCardView,
        accentStrip: android.view.View,
        typeLabel: android.widget.TextView,
        labelColor: String?,
        defaultBg: Int,
        accentColor: Int
    ) {
        if (labelColor != null) {
            try { card.setCardBackgroundColor(Color.parseColor(labelColor)) } catch (_: Exception) {}
            accentStrip.visibility = android.view.View.GONE
            typeLabel.setTextColor(Color.BLACK)
        } else {
            card.setCardBackgroundColor(defaultBg)
            accentStrip.visibility = android.view.View.VISIBLE
            typeLabel.setTextColor(accentColor)
        }
    }

    private fun applyIconUri(imageView: ImageView, iconUri: String?, @DrawableRes fallback: Int) {
        if (iconUri != null) {
            if (iconUri.startsWith("color:")) {
                try {
                    val color = Color.parseColor(iconUri.removePrefix("color:"))
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 8f * imageView.resources.displayMetrics.density
                        setColor(color)
                    }
                    imageView.setImageDrawable(null)
                    imageView.background = bg
                } catch (_: Exception) {}
            } else imageView.setImageURISafe(iconUri, fallback)
        } else {
            imageView.setImageResource(fallback)
            imageView.background = androidx.core.content.ContextCompat.getDrawable(
                imageView.context, com.anton.quicknotes2.R.drawable.icon_placeholder_bg)
        }
    }

    private fun ImageView.setImageURISafe(uri: String, @DrawableRes fallback: Int) {
        try {
            setImageURI(Uri.parse(uri))
            if (drawable == null) setImageResource(fallback)
        } catch (_: Exception) { setImageResource(fallback) }
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(timestamp))
}
