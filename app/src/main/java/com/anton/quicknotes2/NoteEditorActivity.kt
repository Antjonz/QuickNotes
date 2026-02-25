package com.anton.quicknotes2

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Editable
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao(), db.dividerDao()))
    }

    private var existingNote: Note? = null
    private var targetFolderId: Int? = null
    private var currentNoteId: Int = -1
    private var isDraggingImages = false
    private var originalTitle: String = ""
    private var originalBody: String = ""

    // Persistent formatting toggle state
    private var isBoldActive = false
    private var isItalicActive = false
    private var isUnderlineActive = false
    private var isApplyingFormat = false  // guard to prevent TextWatcher re-entrance

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
                    // Load body: if HTML, parse spans; otherwise plain text
                    if (it.body.contains("<") && it.body.contains(">")) {
                        binding.editBody.setText(Html.fromHtml(it.body, Html.FROM_HTML_MODE_COMPACT))
                    } else {
                        binding.editBody.setText(it.body)
                    }
                    originalTitle = it.title
                    // Normalize originalBody the same way hasUnsavedChanges() does,
                    // so plain-text bodies imported from old backups don't trigger a
                    // false "unsaved changes" warning before the user types anything.
                    originalBody = Html.toHtml(
                        binding.editBody.text,
                        Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
                    ).trim()
                }
            }
        }

        // ── Formatting buttons ─────────────────────────────
        updateFormatButtonStates() // set initial outlined appearance
        binding.btnBold.setOnClickListener {
            if (hasSelection()) {
                toggleSpanOnSelection(StyleSpan(Typeface.BOLD), Typeface.BOLD)
                // Reflect selection state in the toggle
                isBoldActive = selectionHasSpan(StyleSpan::class.java) { it.style == Typeface.BOLD }
            } else {
                isBoldActive = !isBoldActive
            }
            updateFormatButtonStates()
        }
        binding.btnItalic.setOnClickListener {
            if (hasSelection()) {
                toggleSpanOnSelection(StyleSpan(Typeface.ITALIC), Typeface.ITALIC)
                isItalicActive = selectionHasSpan(StyleSpan::class.java) { it.style == Typeface.ITALIC }
            } else {
                isItalicActive = !isItalicActive
            }
            updateFormatButtonStates()
        }
        binding.btnUnderline.setOnClickListener {
            if (hasSelection()) {
                toggleSpanOnSelection(UnderlineSpan(), -1)
                isUnderlineActive = selectionHasSpan(UnderlineSpan::class.java) { true }
            } else {
                isUnderlineActive = !isUnderlineActive
            }
            updateFormatButtonStates()
        }

        // TextWatcher: apply active formats to each character as it's typed
        binding.editBody.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isApplyingFormat || count == 0) return
                if (!isBoldActive && !isItalicActive && !isUnderlineActive) return
                isApplyingFormat = true
                val text = binding.editBody.text as? Spannable ?: run { isApplyingFormat = false; return }
                if (isBoldActive)
                    text.setSpan(StyleSpan(Typeface.BOLD), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isItalicActive)
                    text.setSpan(StyleSpan(Typeface.ITALIC), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isUnderlineActive)
                    text.setSpan(UnderlineSpan(), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                isApplyingFormat = false
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
                    ContextCompat.getColor(this, R.color.delete_red)
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

    private fun hasSelection(): Boolean {
        val start = binding.editBody.selectionStart
        val end = binding.editBody.selectionEnd
        return start >= 0 && end > start
    }

    private fun <T : Any> selectionHasSpan(cls: Class<T>, predicate: (T) -> Boolean): Boolean {
        val text = binding.editBody.text as? Spannable ?: return false
        val start = binding.editBody.selectionStart.coerceAtLeast(0)
        val end = binding.editBody.selectionEnd.coerceAtLeast(0)
        return text.getSpans(start, end, cls).any { predicate(it) && text.getSpanStart(it) <= start && text.getSpanEnd(it) >= end }
    }

    private fun toggleSpanOnSelection(span: Any, styleType: Int) {
        val text = binding.editBody.text as? Spannable ?: return
        val start = binding.editBody.selectionStart.coerceAtLeast(0)
        val end = binding.editBody.selectionEnd.coerceAtLeast(0)
        if (start >= end) return
        val existingSpans = when (span) {
            is StyleSpan -> text.getSpans(start, end, StyleSpan::class.java).filter { it.style == styleType }
            is UnderlineSpan -> text.getSpans(start, end, UnderlineSpan::class.java).toList()
            else -> emptyList<Any>()
        }
        val fullySpanned = existingSpans.isNotEmpty() && existingSpans.any { s ->
            text.getSpanStart(s) <= start && text.getSpanEnd(s) >= end
        }
        if (fullySpanned) {
            existingSpans.forEach { text.removeSpan(it) }
        } else {
            val newSpan = when (span) {
                is StyleSpan -> StyleSpan(span.style)
                else -> UnderlineSpan()
            }
            text.setSpan(newSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun updateFormatButtonStates() {
        binding.btnBold.isSelected = isBoldActive
        binding.btnItalic.isSelected = isItalicActive
        binding.btnUnderline.isSelected = isUnderlineActive
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
                    // Serialize rich text to HTML
                    val spanned = binding.editBody.text
                    val body = Html.toHtml(spanned, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE).trim()
                    val now = System.currentTimeMillis()
                    val current = existingNote
                    if (current == null) {
                        ensureNoteSavedWithBody(title, body, now)
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
        val spanned = binding.editBody.text
        val body = Html.toHtml(spanned, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE).trim()
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

    /** Creates the note in the DB if it hasn't been saved yet, giving us a valid currentNoteId. */
    private suspend fun ensureNoteSaved() {
        if (currentNoteId != -1) return
        val title = binding.editTitle.text.toString().trim()
        val spanned = binding.editBody.text
        val body = Html.toHtml(spanned, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE).trim()
        val now = System.currentTimeMillis()
        val db = NoteDatabase.getDatabase(applicationContext)
        val repo = NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao(), db.dividerDao())
        val newId = repo.insertNoteWithOrder(
            Note(title = title, body = body, timestamp = now, folderId = targetFolderId)
        ).toInt()
        currentNoteId = newId
        existingNote = Note(id = newId, title = title, body = body, timestamp = now, folderId = targetFolderId)
        originalBody = body
    }

    private suspend fun ensureNoteSavedWithBody(title: String, body: String, now: Long) {
        if (currentNoteId != -1) {
            existingNote?.let { viewModel.update(it.copy(title = title, body = body, timestamp = now)) }
            return
        }
        val db = NoteDatabase.getDatabase(applicationContext)
        val repo = NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao(), db.dividerDao())
        val newId = repo.insertNoteWithOrder(
            Note(title = title, body = body, timestamp = now, folderId = targetFolderId)
        ).toInt()
        currentNoteId = newId
        existingNote = Note(id = newId, title = title, body = body, timestamp = now, folderId = targetFolderId)
    }
}
