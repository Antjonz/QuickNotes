package com.anton.quicknotes2

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.NoteDatabase
import com.anton.quicknotes2.data.NoteRepository
import com.anton.quicknotes2.databinding.ActivityFolderBinding
import com.anton.quicknotes2.ui.FolderAdapter
import com.anton.quicknotes2.ui.NoteViewModel
import com.anton.quicknotes2.ui.NoteViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FolderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_ID = "extra_folder_id"
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
    }

    private lateinit var binding: ActivityFolderBinding
    private lateinit var adapter: FolderAdapter
    private var folderId: Int = -1
    private var isDragging = false
    private var pendingIconNoteId: Int? = null

    private val viewModel: NoteViewModel by viewModels {
        val db = NoteDatabase.getDatabase(applicationContext)
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao()))
    }

    private val pickIcon = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        pendingIconNoteId?.let {
            viewModel.updateNoteIcon(it, uri.toString())
            pendingIconNoteId = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        folderId = intent.getIntExtra(EXTRA_FOLDER_ID, -1)
        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: getString(R.string.folder)
        supportActionBar?.title = folderName

        adapter = FolderAdapter(
            onNoteClick = { note ->
                startActivity(Intent(this, NoteEditorActivity::class.java).apply {
                    putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id)
                })
            },
            onNoteDelete = { note ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete note")
                    .setMessage("Are you sure you want to delete \"${note.title.ifBlank { "Untitled" }}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.delete(note) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onOrderChanged = { notes -> viewModel.reorderNotesInFolder(notes) },
            onNoteIconClick = { note ->
                pendingIconNoteId = note.id
                pickIcon.launch("image/*")
            },
            onMoveOutOfFolder = { note ->
                viewModel.update(note.copy(folderId = null))
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        var pendingMoveOut: com.anton.quicknotes2.data.Note? = null

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            var dragOutView: android.view.View? = null

            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == 0) return false

                val isCancelRow = adapter.isDragging && to == adapter.itemCount - 1

                if (isCancelRow) {
                    // Hovering cancel row → clear drag-out highlight, cancel pending
                    if (dragOutView != null) {
                        dragOutView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        dragOutView = null
                        pendingMoveOut = null
                    }
                    return false
                }

                if (to == 0) {
                    // Hovering over drag-out row — highlight, return false to keep firing
                    if (dragOutView == null) {
                        target.itemView.setBackgroundColor(0x1A6650A4.toInt())
                        dragOutView = target.itemView
                        pendingMoveOut = adapter.getNoteAt(from)
                    }
                    return false
                }

                // Moved to a note row — clear drag-out highlight
                if (dragOutView != null) {
                    dragOutView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    dragOutView = null
                    pendingMoveOut = null
                }

                isDragging = true
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    adapter.isDragging = true
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter.isDragging = false
                dragOutView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                dragOutView = null
                val pending = pendingMoveOut
                pendingMoveOut = null
                isDragging = false
                if (pending != null) {
                    viewModel.update(pending.copy(folderId = null))
                } else {
                    viewModel.reorderNotesInFolder(adapter.getItems())
                }
            }

            override fun isLongPressDragEnabled() = true
        })
        touchHelper.attachToRecyclerView(binding.recyclerView)
        adapter.itemTouchHelper = touchHelper

        viewModel.getNotesInFolder(folderId).observe(this) { notes ->
            if (isDragging) return@observe
            adapter.submitList(notes)
            binding.emptyText.visibility =
                if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.fab.setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java).apply {
                putExtra(NoteEditorActivity.EXTRA_FOLDER_ID, folderId)
            })
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
