package com.anton.quicknotes2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.Folder
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.data.NoteDatabase
import com.anton.quicknotes2.data.NoteList
import com.anton.quicknotes2.data.NoteRepository
import com.anton.quicknotes2.databinding.ActivityMainBinding
import com.anton.quicknotes2.databinding.DialogNewFolderBinding
import com.anton.quicknotes2.ui.HomeAdapter
import com.anton.quicknotes2.ui.HomeItem
import com.anton.quicknotes2.ui.NoteViewModel
import com.anton.quicknotes2.ui.NoteViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HomeAdapter

    private val viewModel: NoteViewModel by viewModels {
        val db = NoteDatabase.getDatabase(applicationContext)
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao()))
    }

    private var isDragging = false
    private var pendingIconNoteId: Int? = null
    private var pendingIconFolderId: Int? = null
    private var pendingIconWhiteboardId: Int? = null
    private var pendingIconListId: Int? = null

    // Drag-into-folder state — holds either a NoteItem or WhiteboardItem + the target folder
    private var highlightedCard: MaterialCardView? = null
    private var pendingFolderTarget: Pair<HomeItem, Folder>? = null
    private var draggingNote: Note? = null

    private fun setCardHighlight(card: MaterialCardView?) {
        highlightedCard?.strokeColor = Color.TRANSPARENT
        highlightedCard?.strokeWidth = 0
        highlightedCard = card
        card?.strokeColor = 0xFF6650A4.toInt()
        card?.strokeWidth = 6
    }

    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        pendingIconNoteId?.let { viewModel.updateNoteIcon(it, uri.toString()); pendingIconNoteId = null }
        pendingIconFolderId?.let { viewModel.updateFolderIcon(it, uri.toString()); pendingIconFolderId = null }
        pendingIconWhiteboardId?.let { viewModel.updateWhiteboardIcon(it, uri.toString()); pendingIconWhiteboardId = null }
        pendingIconListId?.let { viewModel.updateListIcon(it, uri.toString()); pendingIconListId = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = HomeAdapter(
            onNoteClick = { note ->
                startActivity(Intent(this, NoteEditorActivity::class.java).apply {
                    putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id)
                })
            },
            onFolderClick = { folder ->
                startActivity(Intent(this, FolderActivity::class.java).apply {
                    putExtra(FolderActivity.EXTRA_FOLDER_ID, folder.id)
                    putExtra(FolderActivity.EXTRA_FOLDER_NAME, folder.name)
                })
            },
            onNoteDelete = { note ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete note")
                    .setMessage("Are you sure you want to delete \"${note.title.ifBlank { "Untitled" }}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.delete(note) }
                    .setNegativeButton("Cancel", null).show()
            },
            onFolderDelete = { folder ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete folder")
                    .setMessage("Are you sure you want to delete \"${folder.name}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            val count = viewModel.getFolderItemCount(folder.id)
                            if (count == 0) {
                                viewModel.deleteFolder(folder)
                            } else {
                                MaterialAlertDialogBuilder(this@MainActivity)
                                    .setTitle("What about the items inside?")
                                    .setMessage("\"${folder.name}\" contains $count item${if (count == 1) "" else "s"}. Do you want to delete them too, or move them to the home screen?")
                                    .setPositiveButton("Delete all") { _, _ -> viewModel.deleteFolderAndNotes(folder) }
                                    .setNegativeButton("Move to home") { _, _ -> viewModel.deleteFolderMoveNotesOut(folder) }
                                    .setNeutralButton("Cancel", null)
                                    .show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null).show()
            },
            onOrderChanged = { items -> viewModel.reorderHomeItems(items) },
            onNoteIconClick = { note ->
                pendingIconNoteId = note.id; pendingIconFolderId = null; pendingIconWhiteboardId = null; pickIcon.launch("image/*")
            },
            onFolderIconClick = { folder ->
                pendingIconFolderId = folder.id; pendingIconNoteId = null; pendingIconWhiteboardId = null; pickIcon.launch("image/*")
            },
            onWhiteboardClick = { wb ->
                startActivity(Intent(this, WhiteboardEditorActivity::class.java).apply {
                    putExtra(WhiteboardEditorActivity.EXTRA_WHITEBOARD_ID, wb.id)
                })
            },
            onWhiteboardDelete = { wb ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete whiteboard")
                    .setMessage("Are you sure you want to delete \"${wb.title.ifBlank { "Untitled whiteboard" }}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteWhiteboard(wb) }
                    .setNegativeButton("Cancel", null).show()
            },
            onWhiteboardIconClick = { wb ->
                pendingIconWhiteboardId = wb.id; pendingIconNoteId = null; pendingIconFolderId = null; pickIcon.launch("image/*")
            },
            onListClick = { list ->
                startActivity(Intent(this, ListEditorActivity::class.java).apply {
                    putExtra(ListEditorActivity.EXTRA_LIST_ID, list.id)
                })
            },
            onListDelete = { list ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete list")
                    .setMessage("Are you sure you want to delete \"${list.title.ifBlank { "Untitled list" }}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteList(list) }
                    .setNegativeButton("Cancel", null).show()
            },
            onListIconClick = { list ->
                pendingIconListId = list.id; pendingIconNoteId = null; pendingIconFolderId = null; pendingIconWhiteboardId = null; pickIcon.launch("image/*")
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val draggedItem = adapter.getItemAt(from)
                val targetItem = adapter.getItemAt(to) // null when to == cancel row

                val isDraggable = draggedItem is HomeItem.NoteItem ||
                                  draggedItem is HomeItem.WhiteboardItem ||
                                  draggedItem is HomeItem.FolderItem ||
                                  draggedItem is HomeItem.ListItem

                when {
                    // Hovering over cancel row → clear any folder highlight
                    targetItem == null -> {
                        if (highlightedCard != null) {
                            setCardHighlight(null)
                            pendingFolderTarget = null
                        }
                        return false
                    }
                    // Note, Whiteboard, or Folder hovering over a different folder → highlight
                    isDraggable && targetItem is HomeItem.FolderItem &&
                    (draggedItem !is HomeItem.FolderItem || draggedItem.folder.id != targetItem.folder.id) -> {
                        val card = target.itemView as? MaterialCardView
                        if (card != highlightedCard) {
                            setCardHighlight(card)
                            pendingFolderTarget = Pair(draggedItem!!, targetItem.folder)
                        }
                        return false
                    }
                    // Moving to a normal row — clear folder highlight
                    else -> {
                        if (highlightedCard != null) {
                            setCardHighlight(null)
                            pendingFolderTarget = null
                        }
                        isDragging = true
                        adapter.moveItem(from, to)
                        return true
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    val pos = viewHolder.bindingAdapterPosition
                    val item = if (pos >= 0) adapter.getItemAt(pos) else null
                    if (item is HomeItem.NoteItem || item is HomeItem.WhiteboardItem ||
                        item is HomeItem.ListItem || item is HomeItem.FolderItem) {
                        adapter.isDraggingNote = true
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter.isDraggingNote = false
                setCardHighlight(null)
                val pending = pendingFolderTarget
                pendingFolderTarget = null
                draggingNote = null
                isDragging = false
                if (pending != null) {
                    val (item, folder) = pending
                    when (item) {
                        is HomeItem.NoteItem -> viewModel.update(item.note.copy(folderId = folder.id))
                        is HomeItem.WhiteboardItem -> viewModel.updateWhiteboard(item.whiteboard.copy(folderId = folder.id))
                        is HomeItem.FolderItem -> viewModel.updateFolder(item.folder.copy(parentFolderId = folder.id))
                        is HomeItem.ListItem -> viewModel.updateList(item.noteList.copy(folderId = folder.id))
                    }
                } else {
                    viewModel.reorderHomeItems(adapter.getItems())
                }
            }
        })
        touchHelper.attachToRecyclerView(binding.recyclerView)
        adapter.itemTouchHelper = touchHelper

        viewModel.allFolders.observe(this) { _ -> if (!isDragging) refreshHomeList() }
        viewModel.allNotes.observe(this) { _ -> if (!isDragging) refreshHomeList() }
        viewModel.allWhiteboards.observe(this) { _ -> if (!isDragging) refreshHomeList() }
        viewModel.allLists.observe(this) { _ -> if (!isDragging) refreshHomeList() }

        binding.fab.setOnClickListener { showFabMenu() }
    }

    private fun refreshHomeList() {
        val folders = viewModel.allFolders.value ?: emptyList()
        val notes = viewModel.allNotes.value ?: emptyList()
        val whiteboards = viewModel.allWhiteboards.value ?: emptyList()
        val lists = viewModel.allLists.value ?: emptyList()
        val items = buildHomeList(folders, notes, whiteboards, lists)
        adapter.submitList(items)
        binding.emptyText.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun buildHomeList(
        folders: List<com.anton.quicknotes2.data.Folder>,
        notes: List<com.anton.quicknotes2.data.Note>,
        whiteboards: List<com.anton.quicknotes2.data.Whiteboard>,
        lists: List<com.anton.quicknotes2.data.NoteList> = emptyList()
    ): List<HomeItem> {
        val combined = mutableListOf<HomeItem>()
        folders.forEach { combined.add(HomeItem.FolderItem(it)) }
        notes.forEach { combined.add(HomeItem.NoteItem(it)) }
        whiteboards.forEach { combined.add(HomeItem.WhiteboardItem(it)) }
        lists.forEach { combined.add(HomeItem.ListItem(it)) }
        combined.sortBy {
            when (it) {
                is HomeItem.NoteItem -> it.note.sortOrder
                is HomeItem.FolderItem -> it.folder.sortOrder
                is HomeItem.WhiteboardItem -> it.whiteboard.sortOrder
                is HomeItem.ListItem -> it.noteList.sortOrder
            }
        }
        return combined
    }

    private fun showFabMenu() {
        val sheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_fab, null)
        sheet.setContentView(sheetView)
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewNote).setOnClickListener {
            sheet.dismiss(); startActivity(Intent(this, NoteEditorActivity::class.java))
        }
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewWhiteboard).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, WhiteboardEditorActivity::class.java))
        }
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewList).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, ListEditorActivity::class.java))
        }
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewFolder).setOnClickListener {
            sheet.dismiss(); showNewFolderDialog()
        }
        sheet.show()
    }

    private fun showNewFolderDialog() {
        val dialogBinding = DialogNewFolderBinding.inflate(LayoutInflater.from(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_folder)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = dialogBinding.editFolderName.text.toString().trim()
                if (name.isNotEmpty()) viewModel.insertFolder(Folder(name = name))
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
