package com.anton.quicknotes2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.data.NoteDatabase
import com.anton.quicknotes2.data.NoteImage
import com.anton.quicknotes2.data.NoteRepository
import com.anton.quicknotes2.databinding.ActivityNoteEditorBinding
import com.anton.quicknotes2.ui.NoteImageAdapter
import com.anton.quicknotes2.ui.NoteViewModel
import com.anton.quicknotes2.ui.NoteViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class NoteEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
    }

    private lateinit var binding: ActivityNoteEditorBinding
    private lateinit var imageAdapter: NoteImageAdapter

    private val viewModel: NoteViewModel by viewModels {
        val db = NoteDatabase.getDatabase(applicationContext)
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao()))
    }

    private var existingNote: Note? = null
    private var targetFolderId: Int? = null
    private var currentNoteId: Int = -1
    private var isDraggingImages = false
    private var originalTitle: String = ""
    private var originalBody: String = ""

    // Photo picker launcher
    private val pickImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            // If the note hasn't been saved yet, create it now so we have an ID
            if (currentNoteId == -1) {
                ensureNoteSaved()
            }
            uris.forEach { uri ->
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.insertImage(NoteImage(noteId = currentNoteId, uri = uri.toString()))
            }
            observeImages(currentNoteId)
        }
    }

    // Image viewer launcher (returns reordered URIs)
    private val openViewer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val reorderedUris = result.data
                ?.getStringArrayListExtra(ImageViewerActivity.RESULT_REORDERED_URIS) ?: return@registerForActivityResult
            val currentImages = imageAdapter.getItems().toMutableList()
            if (reorderedUris.size == currentImages.size) {
                val uriToImage = currentImages.associateBy { it.uri }
                val reordered = reorderedUris.mapNotNull { uriToImage[it] }
                imageAdapter.submitList(reordered)
                viewModel.reorderImages(reordered)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1)
        val folderIdExtra = intent.getIntExtra(EXTRA_FOLDER_ID, -1)
        if (folderIdExtra != -1) targetFolderId = folderIdExtra

        if (noteId != -1) {
            currentNoteId = noteId
            lifecycleScope.launch {
                val note = viewModel.getNoteById(noteId)
                note?.let {
                    existingNote = it
                    binding.editTitle.setText(it.title)
                    binding.editBody.setText(it.body)
                    originalTitle = it.title
                    originalBody = it.body
                }
            }
        }

        // ── Image thumbnail strip ──────────────────────────
        imageAdapter = NoteImageAdapter(
            onImageClick = { index ->
                val uris = imageAdapter.getItems().map { it.uri }
                val intent = Intent(this, ImageViewerActivity::class.java).apply {
                    putStringArrayListExtra(ImageViewerActivity.EXTRA_URIS, ArrayList(uris))
                    putExtra(ImageViewerActivity.EXTRA_START_INDEX, index)
                }
                openViewer.launch(intent)
            },
            onImageDelete = { image -> viewModel.deleteImage(image) },
            onOrderChanged = { images -> viewModel.reorderImages(images) }
        )
        binding.recyclerImages.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerImages.adapter = imageAdapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(rv: RecyclerView, src: RecyclerView.ViewHolder, tgt: RecyclerView.ViewHolder): Boolean {
                isDraggingImages = true
                imageAdapter.moveItem(src.bindingAdapterPosition, tgt.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                if (isDraggingImages) {
                    isDraggingImages = false
                    viewModel.reorderImages(imageAdapter.getItems())
                }
            }
        })
        touchHelper.attachToRecyclerView(binding.recyclerImages)
        imageAdapter.itemTouchHelper = touchHelper

        binding.btnAddPhoto.setOnClickListener { pickImages.launch("image/*") }

        binding.btnToggleDeleteImages.setOnClickListener {
            imageAdapter.isDeleteMode = !imageAdapter.isDeleteMode
            val tint = if (imageAdapter.isDeleteMode)
                android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, com.anton.quicknotes2.R.color.delete_red)
                )
            else null
            androidx.core.widget.ImageViewCompat.setImageTintList(binding.btnToggleDeleteImages, tint)
        }

        // Observe images only when we have a note ID
        if (noteId != -1) observeImages(noteId)

        onBackPressedDispatcher.addCallback(this) {
            if (hasUnsavedChanges()) {
                showDiscardDialog()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun observeImages(noteId: Int) {
        viewModel.getImagesForNote(noteId).observe(this) { images ->
            if (!isDraggingImages) {
                imageAdapter.submitList(images)
                val hasImages = images.isNotEmpty()
                binding.imageStripContainer.visibility =
                    if (hasImages) android.view.View.VISIBLE else android.view.View.GONE
                binding.btnToggleDeleteImages.visibility =
                    if (hasImages) android.view.View.VISIBLE else android.view.View.GONE
                // Turn off delete mode when all images are removed
                if (!hasImages) imageAdapter.isDeleteMode = false
            }
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        if (hasUnsavedChanges()) {
            showDiscardDialog()
        } else {
            finish()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                lifecycleScope.launch {
                    val title = binding.editTitle.text.toString().trim()
                    val body = binding.editBody.text.toString().trim()
                    val now = System.currentTimeMillis()
                    val current = existingNote
                    if (current == null) {
                        ensureNoteSaved()
                    } else {
                        viewModel.update(current.copy(title = title, body = body, timestamp = now))
                    }
                    originalTitle = title
                    originalBody = body
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val title = binding.editTitle.text.toString().trim()
        val body = binding.editBody.text.toString().trim()
        return title != originalTitle || body != originalBody
    }

    private fun showDiscardDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard changes?")
            .setMessage("You have unsaved changes. Are you sure you want to leave without saving?")
            .setPositiveButton("Leave") { _, _ -> finish() }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    private fun saveNote() {
        val title = binding.editTitle.text.toString().trim()
        val body = binding.editBody.text.toString().trim()
        val now = System.currentTimeMillis()
        val current = existingNote
        if (current == null) {
            lifecycleScope.launch {
                ensureNoteSaved()
            }
        } else {
            viewModel.update(current.copy(title = title, body = body, timestamp = now))
        }
    }

    /** Creates the note in the DB if it hasn't been saved yet, giving us a valid currentNoteId. */
    private suspend fun ensureNoteSaved() {
        if (currentNoteId != -1) return
        val title = binding.editTitle.text.toString().trim()
        val body = binding.editBody.text.toString().trim()
        val now = System.currentTimeMillis()
        val db = NoteDatabase.getDatabase(applicationContext)
        val repo = NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao())
        val newId = repo.insertNoteWithOrder(
            Note(title = title, body = body, timestamp = now, folderId = targetFolderId)
        ).toInt()
        currentNoteId = newId
        existingNote = Note(id = newId, title = title, body = body, timestamp = now, folderId = targetFolderId)
    }
}
