package com.anton.quicknotes2

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
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
import com.anton.quicknotes2.data.Divider
import com.anton.quicknotes2.databinding.ActivityMainBinding
import com.anton.quicknotes2.databinding.DialogNewFolderBinding
import com.anton.quicknotes2.ui.HomeAdapter
import com.anton.quicknotes2.ui.HomeItem
import com.anton.quicknotes2.ui.NoteViewModel
import com.anton.quicknotes2.ui.NoteViewModelFactory
import com.anton.quicknotes2.ui.ColorPickerDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HomeAdapter

    private val viewModel: NoteViewModel by viewModels {
        val db = NoteDatabase.getDatabase(applicationContext)
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao(), db.dividerDao()))
    }

    private var isDragging = false
    private var returningFromIconPicker = false
    private var pendingIconNoteId: Int? = null
    private var pendingIconFolderId: Int? = null
    private var pendingIconWhiteboardId: Int? = null
    private var pendingIconListId: Int? = null

    // Export/import
    private var pendingExportIncludeImages = false
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val db = NoteDatabase.getDatabase(applicationContext)
                contentResolver.openOutputStream(uri)?.use { out ->
                    ExportImportManager.export(db, out, pendingExportIncludeImages, this@MainActivity)
                }
                Toast.makeText(this@MainActivity, "Export successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private var pendingImportClearExisting = false
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val db = NoteDatabase.getDatabase(applicationContext)
                contentResolver.openInputStream(uri)?.use { input ->
                    ExportImportManager.import(db, input, pendingImportClearExisting, this@MainActivity)
                }
                Toast.makeText(this@MainActivity, "Import successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // The card currently highlighted as "pending move into folder" (the dragged item)
    private var highlightedCard: MaterialCardView? = null
    private var pendingFolderTarget: Pair<HomeItem, Folder>? = null
    private var draggingNote: Note? = null

    /** Darken [card] (the dragged item) to signal it's about to be dropped into a folder.
     *  Pass null to restore the previously highlighted card. */
    private fun setCardHighlight(card: MaterialCardView?) {
        // Restore the previously highlighted dragged card
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

    /** Returns true if the centre of [dragged] is within the bounds of [target] on screen. */
    private fun isCentreOver(dragged: android.view.View, target: android.view.View): Boolean {
        val dLoc = IntArray(2); dragged.getLocationOnScreen(dLoc)
        val cx = dLoc[0] + dragged.width / 2
        val cy = dLoc[1] + dragged.height / 2
        val tLoc = IntArray(2); target.getLocationOnScreen(tLoc)
        return cx in tLoc[0]..(tLoc[0] + target.width) &&
               cy in tLoc[1]..(tLoc[1] + target.height)
    }

    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        returningFromIconPicker = false
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        pendingIconNoteId?.let { viewModel.updateNoteIcon(it, uri.toString()); pendingIconNoteId = null }
        pendingIconFolderId?.let { viewModel.updateFolderIcon(it, uri.toString()); pendingIconFolderId = null }
        pendingIconWhiteboardId?.let { viewModel.updateWhiteboardIcon(it, uri.toString()); pendingIconWhiteboardId = null }
        pendingIconListId?.let { viewModel.updateListIcon(it, uri.toString()); pendingIconListId = null }
        // LiveData observers fire when Room commits → refreshHomeList() with correct data
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
                showIconOptionsDialog(
                    onPickImage = { returningFromIconPicker = true; pendingIconNoteId = note.id; pendingIconFolderId = null; pendingIconWhiteboardId = null; pendingIconListId = null; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateNoteIcon(note.id, "color:$color") },
                    onResetDefault = { viewModel.updateNoteIcon(note.id, null) },
                    onChangeLabelColor = {
                        com.anton.quicknotes2.ui.ColorPickerDialog.show(this, note.labelColor) { color ->
                            viewModel.updateNoteLabelColor(note.id, color)
                        }
                    }
                )
            },
            onFolderIconClick = { folder ->
                showIconOptionsDialog(
                    onPickImage = { returningFromIconPicker = true; pendingIconFolderId = folder.id; pendingIconNoteId = null; pendingIconWhiteboardId = null; pendingIconListId = null; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateFolderIcon(folder.id, "color:$color") },
                    onResetDefault = { viewModel.updateFolderIcon(folder.id, null) }
                )
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
                showIconOptionsDialog(
                    onPickImage = { returningFromIconPicker = true; pendingIconWhiteboardId = wb.id; pendingIconNoteId = null; pendingIconFolderId = null; pendingIconListId = null; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateWhiteboardIcon(wb.id, "color:$color") },
                    onResetDefault = { viewModel.updateWhiteboardIcon(wb.id, null) },
                    onChangeLabelColor = {
                        com.anton.quicknotes2.ui.ColorPickerDialog.show(this, wb.labelColor) { color ->
                            viewModel.updateWhiteboardLabelColor(wb.id, color)
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
                    onPickImage = { returningFromIconPicker = true; pendingIconListId = list.id; pendingIconNoteId = null; pendingIconFolderId = null; pendingIconWhiteboardId = null; pickIcon.launch("image/*") },
                    onPickColor = { color -> viewModel.updateListIcon(list.id, "color:$color") },
                    onResetDefault = { viewModel.updateListIcon(list.id, null) },
                    onChangeLabelColor = {
                        com.anton.quicknotes2.ui.ColorPickerDialog.show(this, list.labelColor) { color ->
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
                    // Hovering over cancel row → clear folder highlight, no reorder
                    targetItem == null -> {
                        if (highlightedCard != null) { setCardHighlight(null); pendingFolderTarget = null }
                        return false
                    }
                    // Draggable item hovering over a different folder AND centre is inside it → highlight dragged item for drop-in
                    isDraggable &&
                    targetItem is HomeItem.FolderItem &&
                    (draggedItem !is HomeItem.FolderItem || draggedItem.folder.id != targetItem.folder.id) &&
                    isCentreOver(source.itemView, target.itemView) -> {
                        val draggedCard = source.itemView as? MaterialCardView
                        if (draggedCard != highlightedCard) {
                            setCardHighlight(draggedCard)
                            pendingFolderTarget = Pair(draggedItem!!, targetItem.folder)
                        }
                        return false
                    }
                    // Otherwise: clear any folder highlight and do a normal reorder
                    else -> {
                        if (highlightedCard != null) { setCardHighlight(null); pendingFolderTarget = null }
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
                        is HomeItem.DividerItem -> { /* dividers cannot move into folders this way */ }
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
        viewModel.allDividers.observe(this) { _ -> if (!isDragging) refreshHomeList() }

        binding.fab.setOnClickListener { showFabMenu() }
    }

    override fun onResume() {
        super.onResume()
        // Skip the stale DB read when returning from the icon picker —
        // the LiveData observer fires after Room commits and delivers fresh data.
        if (returningFromIconPicker) return
        if (!isDragging) {
            lifecycleScope.launch {
                val db = com.anton.quicknotes2.data.NoteDatabase.getDatabase(applicationContext)
                val notes       = db.noteDao().getAllNotesDirect()
                val folders     = db.folderDao().getAllFoldersDirect()
                val whiteboards = db.whiteboardDao().getAllWhiteboardsDirect()
                val lists       = db.noteListDao().getAllListsDirect()
                val dividers    = db.dividerDao().getAllDividersDirect()
                val items = buildHomeList(folders, notes, whiteboards, lists, dividers)
                adapter.forceRefresh(items)
                binding.emptyText.visibility =
                    if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun refreshHomeList() {
        val folders = viewModel.allFolders.value ?: emptyList()
        val notes = viewModel.allNotes.value ?: emptyList()
        val whiteboards = viewModel.allWhiteboards.value ?: emptyList()
        val lists = viewModel.allLists.value ?: emptyList()
        val dividers = viewModel.allDividers.value ?: emptyList()
        val items = buildHomeList(folders, notes, whiteboards, lists, dividers)
        adapter.forceRefresh(items)
        binding.emptyText.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun buildHomeList(
        folders: List<com.anton.quicknotes2.data.Folder>,
        notes: List<com.anton.quicknotes2.data.Note>,
        whiteboards: List<com.anton.quicknotes2.data.Whiteboard>,
        lists: List<com.anton.quicknotes2.data.NoteList> = emptyList(),
        dividers: List<com.anton.quicknotes2.data.Divider> = emptyList()
    ): List<HomeItem> {
        val combined = mutableListOf<HomeItem>()
        folders.forEach { combined.add(HomeItem.FolderItem(it)) }
        notes.forEach { combined.add(HomeItem.NoteItem(it)) }
        whiteboards.forEach { combined.add(HomeItem.WhiteboardItem(it)) }
        lists.forEach { combined.add(HomeItem.ListItem(it)) }
        dividers.forEach { combined.add(HomeItem.DividerItem(it)) }
        combined.sortBy {
            when (it) {
                is HomeItem.NoteItem -> it.note.sortOrder
                is HomeItem.FolderItem -> it.folder.sortOrder
                is HomeItem.WhiteboardItem -> it.whiteboard.sortOrder
                is HomeItem.ListItem -> it.noteList.sortOrder
                is HomeItem.DividerItem -> it.divider.sortOrder
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
        sheetView.findViewById<android.widget.TextView>(R.id.btnNewDivider).setOnClickListener {
            sheet.dismiss(); showNewDividerDialog(null)
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
                    1 -> com.anton.quicknotes2.ui.ColorPickerDialog.showForIcon(this, null) { color ->
                        onPickColor(color)
                    }
                    2 -> onResetDefault()
                    3 -> onChangeLabelColor?.invoke()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewDividerDialog(folderId: Int?) {
        val editText = android.widget.EditText(this).apply {
            hint = "Label (optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setBackgroundResource(0)
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New divider")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val label = editText.text.toString().trim()
                lifecycleScope.launch {
                    viewModel.insertDivider(com.anton.quicknotes2.data.Divider(label = label, folderId = folderId))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDividerDialog(divider: Divider) {
        val editText = android.widget.EditText(this).apply {
            setText(divider.label)
            hint = "Label (optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Tint icon white so it's visible on the purple toolbar
        menu.findItem(R.id.action_export_import)?.icon?.setTint(android.graphics.Color.WHITE)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_export_import) {
            showExportImportChooser()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ── Export / Import dialogs ────────────────────────────────────────────────

    private fun showExportImportChooser() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Export / Import")
            .setMessage("What would you like to do?")
            .setPositiveButton("Export") { _, _ -> showExportConfirmation() }
            .setNegativeButton("Import") { _, _ -> showImportOptions() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showExportConfirmation() {
        val db = NoteDatabase.getDatabase(applicationContext)
        lifecycleScope.launch {
            val jsonSize = ExportImportManager.estimateJsonSize(db)
            val imgSize  = ExportImportManager.estimateImagesSize(db, this@MainActivity)
            val jsonStr  = ExportImportManager.formatBytes(jsonSize)
            val totalStr = ExportImportManager.formatBytes(jsonSize + imgSize)

            // Check if there is actually anything to export
            val hasData = db.noteDao().getAllNotesAllLevelsDirect().isNotEmpty() ||
                          db.folderDao().getAllFoldersAllLevelsDirect().isNotEmpty() ||
                          db.whiteboardDao().getAllWhiteboardsAllLevelsDirect().isNotEmpty() ||
                          db.noteListDao().getAllListsAllLevelsDirect().isNotEmpty() ||
                          db.dividerDao().getAllDividersAllLevelsDirect().isNotEmpty()

            if (!hasData) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Nothing to export")
                    .setMessage("There are no notes, folders, lists or whiteboards to export. Create some content first.")
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Export data")
                .setMessage(
                    "Your notes, folders, lists and whiteboards will be saved to a .qnbackup file.\n\n" +
                    "File size without pictures: $jsonStr\n" +
                    "File size with pictures: $totalStr\n\n" +
                    "Include pictures in the export?"
                )
                .setPositiveButton("Include pictures") { _, _ ->
                    pendingExportIncludeImages = true
                    exportLauncher.launch("quicknotes_backup.qnbackup")
                }
                .setNegativeButton("Without pictures") { _, _ ->
                    pendingExportIncludeImages = false
                    exportLauncher.launch("quicknotes_backup.qnbackup")
                }
                .setNeutralButton("Cancel", null)
                .show()
        }
    }

    private fun showImportOptions() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Import data")
            .setMessage("What should happen to your current notes, folders, lists and whiteboards?")
            .setPositiveButton("Delete current data") { _, _ ->
                confirmImport(clearExisting = true)
            }
            .setNegativeButton("Keep current data") { _, _ ->
                confirmImport(clearExisting = false)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun confirmImport(clearExisting: Boolean) {
        val msg = if (clearExisting)
            "All current data will be permanently deleted and replaced with the backup. Are you sure?"
        else
            "The backup will be added on top of your current data. Continue?"
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm import")
            .setMessage(msg)
            .setPositiveButton("Import") { _, _ ->
                pendingImportClearExisting = clearExisting
                importLauncher.launch(arrayOf("*/*"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
