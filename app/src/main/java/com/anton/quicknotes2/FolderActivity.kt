package com.anton.quicknotes2

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.Folder
import com.anton.quicknotes2.data.NoteDatabase
import com.anton.quicknotes2.data.NoteList
import com.anton.quicknotes2.data.NoteRepository
import com.anton.quicknotes2.data.Divider
import com.anton.quicknotes2.ui.ColorPickerDialog
import com.anton.quicknotes2.databinding.ActivityFolderBinding
import com.anton.quicknotes2.databinding.DialogNewFolderBinding
import com.anton.quicknotes2.ui.FolderAdapter
import com.anton.quicknotes2.ui.FolderItem
import com.anton.quicknotes2.ui.NoteViewModel
import com.anton.quicknotes2.ui.NoteViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

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
    private var pendingIconWhiteboardId: Int? = null
    private var pendingIconSubFolderId: Int? = null
    private var pendingIconListId: Int? = null

    // Drag-into-subfolder state
    private var highlightedCard: MaterialCardView? = null
    private var pendingSubFolderTarget: Pair<FolderItem, Folder>? = null

    // Latest data cached for onResume refresh
    private var latestNotes = emptyList<com.anton.quicknotes2.data.Note>()
    private var latestWhiteboards = emptyList<com.anton.quicknotes2.data.Whiteboard>()
    private var latestSubFolders = emptyList<Folder>()
    private var latestLists = emptyList<NoteList>()
    private var latestDividers = emptyList<Divider>()

    private val viewModel: NoteViewModel by viewModels {
        val db = NoteDatabase.getDatabase(applicationContext)
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao(), db.dividerDao()))
    }

    private fun setCardHighlight(card: MaterialCardView?) {
        if (highlightedCard != null) {
            val orig = highlightedCard!!.getTag(R.id.tag_original_color) as? Int
            if (orig != null) highlightedCard!!.setCardBackgroundColor(orig)
        }
        highlightedCard = card
        if (card != null) {
            if (card.getTag(R.id.tag_original_color) == null) {
                card.setTag(R.id.tag_original_color, card.cardBackgroundColor.defaultColor)
            }
            val orig = card.getTag(R.id.tag_original_color) as Int
            val r = (android.graphics.Color.red(orig) * 0.78f).toInt()
            val g = (android.graphics.Color.green(orig) * 0.78f).toInt()
            val b = (android.graphics.Color.blue(orig) * 0.78f).toInt()
            card.setCardBackgroundColor(android.graphics.Color.rgb(r, g, b))
        }
    }

    private fun isCentreOver(dragged: android.view.View, target: android.view.View): Boolean {
        val dLoc = IntArray(2); dragged.getLocationOnScreen(dLoc)
        val cx = dLoc[0] + dragged.width / 2
        val cy = dLoc[1] + dragged.height / 2
        val tLoc = IntArray(2); target.getLocationOnScreen(tLoc)
        return cx in tLoc[0]..(tLoc[0] + target.width) &&
               cy in tLoc[1]..(tLoc[1] + target.height)
    }

    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        pendingIconNoteId?.let { viewModel.updateNoteIcon(it, uri.toString()); pendingIconNoteId = null }
        pendingIconWhiteboardId?.let { viewModel.updateWhiteboardIcon(it, uri.toString()); pendingIconWhiteboardId = null }
        pendingIconSubFolderId?.let { viewModel.updateFolderIcon(it, uri.toString()); pendingIconSubFolderId = null }
        pendingIconListId?.let { viewModel.updateListIcon(it, uri.toString()); pendingIconListId = null }
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
                    .setNegativeButton("Cancel", null).show()
            },
            onOrderChanged = { notes -> viewModel.reorderNotesInFolder(notes) },
            onNoteIconClick = { note ->
                showIconOptionsDialog(
                    onPickImage = { pendingIconNoteId = note.id; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateNoteIcon(note.id, "color:$color") },
                    onResetDefault = { viewModel.updateNoteIcon(note.id, null) },
                    onChangeLabelColor = {
                        ColorPickerDialog.show(this, note.labelColor) { color ->
                            viewModel.updateNoteLabelColor(note.id, color)
                        }
                    }
                )
            },
            onMoveOutOfFolder = { note -> viewModel.update(note.copy(folderId = null)) },
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
                showIconOptionsDialog(
                    onPickImage = { pendingIconWhiteboardId = wb.id; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateWhiteboardIcon(wb.id, "color:$color") },
                    onResetDefault = { viewModel.updateWhiteboardIcon(wb.id, null) },
                    onChangeLabelColor = {
                        ColorPickerDialog.show(this, wb.labelColor) { color ->
                            viewModel.updateWhiteboardLabelColor(wb.id, color)
                        }
                    }
                )
            },
            onSubFolderClick = { folder ->
                startActivity(Intent(this, FolderActivity::class.java).apply {
                    putExtra(EXTRA_FOLDER_ID, folder.id)
                    putExtra(EXTRA_FOLDER_NAME, folder.name)
                })
            },
            onSubFolderDelete = { folder -> showDeleteFolderDialog(folder) },
            onSubFolderIconClick = { folder ->
                showIconOptionsDialog(
                    onPickImage = { pendingIconSubFolderId = folder.id; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateFolderIcon(folder.id, "color:$color") },
                    onResetDefault = { viewModel.updateFolderIcon(folder.id, null) },
                    onChangeLabelColor = {
                        ColorPickerDialog.show(this, folder.labelColor) { color ->
                            viewModel.updateFolderLabelColor(folder.id, color)
                        }
                    }
                )
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
                showIconOptionsDialog(
                    onPickImage = { pendingIconListId = list.id; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateListIcon(list.id, "color:$color") },
                    onResetDefault = { viewModel.updateListIcon(list.id, null) },
                    onChangeLabelColor = {
                        ColorPickerDialog.show(this, list.labelColor) { color ->
                            viewModel.updateListLabelColor(list.id, color)
                        }
                    }
                )
            },
            onDividerDelete = { divider ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete divider")
                    .setMessage("Are you sure you want to delete this divider?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteDivider(divider) }
                    .setNegativeButton("Cancel", null).show()
            },
            onDividerRename = { divider -> showRenameDividerDialog(divider) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        var pendingMoveOut: FolderItem? = null

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            var dragOutView: android.view.View? = null

            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == 0) return false

                val draggedItem = adapter.getFolderItemAt(from)
                val targetItem = adapter.getFolderItemAt(to)
                val isCancelRow = adapter.isDragging && to == adapter.itemCount - 1

                if (isCancelRow) {
                    if (dragOutView != null) {
                        dragOutView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        dragOutView = null
                        pendingMoveOut = null
                    }
                    setCardHighlight(null)
                    pendingSubFolderTarget = null
                    return false
                }

                // Hovering over drag-out row → move out of current folder
                if (to == 0) {
                    setCardHighlight(null)
                    pendingSubFolderTarget = null
                    if (dragOutView == null) {
                        target.itemView.setBackgroundColor(0x1A6650A4.toInt())
                        dragOutView = target.itemView
                        pendingMoveOut = draggedItem
                    }
                    return false
                }

                // Draggable item hovering over a sub-folder → highlight to drop into it
                val isDraggable = draggedItem is FolderItem.NoteItem ||
                                  draggedItem is FolderItem.WhiteboardItem ||
                                  draggedItem is FolderItem.SubFolderItem ||
                                  draggedItem is FolderItem.ListItem
                if (isDraggable && targetItem is FolderItem.SubFolderItem &&
                    (draggedItem !is FolderItem.SubFolderItem || draggedItem.folder.id != targetItem.folder.id) &&
                    isCentreOver(source.itemView, target.itemView)) {
                    val draggedCard = source.itemView as? MaterialCardView
                    if (draggedCard != highlightedCard) {
                        setCardHighlight(draggedCard)
                        pendingSubFolderTarget = Pair(draggedItem!!, targetItem.folder)
                    }
                    if (dragOutView != null) {
                        dragOutView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        dragOutView = null
                        pendingMoveOut = null
                    }
                    return false
                }

                // Normal reorder
                setCardHighlight(null)
                pendingSubFolderTarget = null
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
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) adapter.isDragging = true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter.isDragging = false
                dragOutView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                dragOutView = null
                setCardHighlight(null)
                val moveOut = pendingMoveOut
                val moveInto = pendingSubFolderTarget
                pendingMoveOut = null
                pendingSubFolderTarget = null
                isDragging = false
                when {
                    moveInto != null -> {
                        val (item, targetFolder) = moveInto
                        when (item) {
                            is FolderItem.NoteItem -> viewModel.update(item.note.copy(folderId = targetFolder.id))
                            is FolderItem.WhiteboardItem -> viewModel.updateWhiteboard(item.wb.copy(folderId = targetFolder.id))
                            is FolderItem.SubFolderItem -> viewModel.updateFolder(item.folder.copy(parentFolderId = targetFolder.id))
                            is FolderItem.ListItem -> viewModel.updateList(item.noteList.copy(folderId = targetFolder.id))
                            is FolderItem.DividerItem -> { /* dividers don't move into sub-folders */ }
                        }
                    }
                    moveOut != null -> {
                        lifecycleScope.launch {
                            val parent = viewModel.getFolderById(folderId)
                            val grandParentId = parent?.parentFolderId
                            when (moveOut) {
                                is FolderItem.NoteItem -> viewModel.update(moveOut.note.copy(folderId = grandParentId))
                                is FolderItem.WhiteboardItem -> viewModel.updateWhiteboard(moveOut.wb.copy(folderId = grandParentId))
                                is FolderItem.SubFolderItem -> viewModel.updateFolder(moveOut.folder.copy(parentFolderId = grandParentId))
                                is FolderItem.ListItem -> viewModel.updateList(moveOut.noteList.copy(folderId = grandParentId))
                                is FolderItem.DividerItem -> viewModel.updateDivider(moveOut.divider.copy(folderId = grandParentId))
                            }
                        }
                    }
                    else -> viewModel.reorderFolderContents(adapter.getAllItems())
                }
            }

            override fun isLongPressDragEnabled() = true
        })
        touchHelper.attachToRecyclerView(binding.recyclerView)
        adapter.itemTouchHelper = touchHelper


        fun refresh() {
            if (!isDragging) {
                adapter.forceRefresh(latestNotes, latestWhiteboards, latestSubFolders, latestLists, latestDividers)
                val empty = latestNotes.isEmpty() && latestWhiteboards.isEmpty() && latestSubFolders.isEmpty() && latestLists.isEmpty() && latestDividers.isEmpty()
                binding.emptyText.visibility = if (empty) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        viewModel.getNotesInFolder(folderId).observe(this) { latestNotes = it; refresh() }
        viewModel.getWhiteboardsInFolder(folderId).observe(this) { latestWhiteboards = it; refresh() }
        viewModel.getSubFolders(folderId).observe(this) { latestSubFolders = it; refresh() }
        viewModel.getListsInFolder(folderId).observe(this) { latestLists = it; refresh() }
        viewModel.getDividersInFolder(folderId).observe(this) { latestDividers = it; refresh() }

        binding.fab.setOnClickListener { showFabMenu() }
    }

    override fun onResume() {
        super.onResume()
        if (!isDragging) {
            lifecycleScope.launch {
                val db = com.anton.quicknotes2.data.NoteDatabase.getDatabase(applicationContext)
                latestNotes       = db.noteDao().getNotesInFolderDirect(folderId)
                latestWhiteboards = db.whiteboardDao().getWhiteboardsInFolderDirect(folderId)
                latestSubFolders  = db.folderDao().getSubFoldersDirect(folderId)
                latestLists       = db.noteListDao().getListsInFolderDirect(folderId)
                latestDividers    = db.dividerDao().getDividersInFolderDirect(folderId)
                adapter.forceRefresh(latestNotes, latestWhiteboards, latestSubFolders, latestLists, latestDividers)
                val empty = latestNotes.isEmpty() && latestWhiteboards.isEmpty() &&
                            latestSubFolders.isEmpty() && latestLists.isEmpty() && latestDividers.isEmpty()
                binding.emptyText.visibility =
                    if (empty) android.view.View.VISIBLE else android.view.View.GONE
                binding.recyclerView.scrollToPosition(0)
            }
        }
    }

    private fun showDeleteFolderDialog(folder: Folder) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete folder")
            .setMessage("Are you sure you want to delete \"${folder.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val count = viewModel.getFolderItemCount(folder.id)
                    if (count == 0) {
                        viewModel.deleteFolder(folder)
                    } else {
                        MaterialAlertDialogBuilder(this@FolderActivity)
                            .setTitle("What about the items inside?")
                            .setMessage("\"${folder.name}\" contains $count item${if (count == 1) "" else "s"}. Delete them all, or move them here?")
                            .setPositiveButton("Delete all") { _, _ -> viewModel.deleteFolderAndNotes(folder) }
                            .setNegativeButton("Move here") { _, _ -> viewModel.deleteFolderMoveNotesOut(folder) }
                            .setNeutralButton("Cancel", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showFabMenu() {
        val sheet = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_fab, null)
        sheet.setContentView(sheetView)
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewNote).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, NoteEditorActivity::class.java).apply {
                putExtra(NoteEditorActivity.EXTRA_FOLDER_ID, folderId)
            })
        }
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewWhiteboard).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, WhiteboardEditorActivity::class.java).apply {
                putExtra(WhiteboardEditorActivity.EXTRA_FOLDER_ID, folderId)
            })
        }
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewList).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, ListEditorActivity::class.java).apply {
                putExtra(ListEditorActivity.EXTRA_FOLDER_ID, folderId)
            })
        }
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewFolder).setOnClickListener {
            sheet.dismiss()
            showNewSubFolderDialog()
        }
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewDivider).setOnClickListener {
            sheet.dismiss()
            showNewDividerDialog()
        }
        sheet.show()
    }

    private fun showNewSubFolderDialog() {
        val dialogBinding = DialogNewFolderBinding.inflate(LayoutInflater.from(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_folder)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = dialogBinding.editFolderName.text.toString().trim()
                if (name.isNotEmpty()) viewModel.insertFolder(Folder(name = name, parentFolderId = folderId))
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showIconOptionsDialog(
        onPickImage: () -> Unit,
        onPickColor: (String) -> Unit,
        onResetDefault: () -> Unit,
        onChangeLabelColor: (() -> Unit)? = null
    ) {
        val options = if (onChangeLabelColor != null)
            arrayOf("Use a picture", "Use a solid color", "Reset to default", "Change label color")
        else
            arrayOf("Use a picture", "Use a solid color", "Reset to default")

        MaterialAlertDialogBuilder(this)
            .setTitle("Change icon")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onPickImage()
                    1 -> ColorPickerDialog.showForIcon(this, null) { color ->
                        onPickColor(color)
                    }
                    2 -> onResetDefault()
                    3 -> onChangeLabelColor?.invoke()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewDividerDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "Label (optional)"
            setBackgroundResource(0)
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New divider")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val label = editText.text.toString().trim()
                lifecycleScope.launch {
                    viewModel.insertDivider(Divider(label = label, folderId = folderId))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDividerDialog(divider: Divider) {
        val editText = android.widget.EditText(this).apply {
            setText(divider.label)
            hint = "Label (optional)"
            setBackgroundResource(0)
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Edit divider label")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val label = editText.text.toString().trim()
                viewModel.updateDivider(divider.copy(label = label))
            }
            .setNeutralButton("Delete") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete divider")
                    .setMessage("Are you sure you want to delete this divider?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteDivider(divider) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_folder, menu)
        menu.findItem(R.id.action_rename_folder)?.icon?.setTint(android.graphics.Color.WHITE)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_rename_folder) {
            showRenameFolderDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showRenameFolderDialog() {
        val dialogBinding = DialogNewFolderBinding.inflate(LayoutInflater.from(this))
        // Pre-fill with the current folder name
        dialogBinding.editFolderName.setText(supportActionBar?.title)
        dialogBinding.editFolderName.selectAll()
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename folder")
            .setView(dialogBinding.root)
            .setPositiveButton("Rename") { _, _ ->
                val newName = dialogBinding.editFolderName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        val folder = viewModel.getFolderById(folderId) ?: return@launch
                        viewModel.updateFolder(folder.copy(name = newName))
                        supportActionBar?.title = newName
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
