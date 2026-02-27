package com.anton.quicknotes2

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    // Font size: null = default, otherwise sp value
    private var activeFontSizeSp: Int? = null

    // Text color: null = default (black)
    private var activeTextColor: Int? = null

    // Text highlight/background color: null = none
    private var activeHighlightColor: Int? = null

    // Last chosen special character (default = checkmark)
    private var lastSpecialChar: String = "✓"

    // Tab indent tracking
    private var isHandlingTab = false

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
        const val TAB_SPACES = "      " // 6 spaces
        const val MAX_TABS = 8          // maximum tab stops per line
        // Font size options (sp values) + button label letter
        val FONT_SIZES = listOf(
            Triple("Small",  "S", 12),
            Triple("Normal", "N", 16),
            Triple("Large",  "L", 20),
            Triple("Huge",   "H", 28)
        )
        val DEFAULT_SPECIAL_CHAR = "✓"
        val SPECIAL_CHARS = listOf(
            "✓ Checkmark" to "✓",
            "✗ Cross" to "✗",
            "= Equals" to "=",
            "€ Euro" to "€",
            "→ Arrow" to "→",
            "★ Star" to "★",
            "• Bullet" to "•",
            "… Ellipsis" to "…",
            "° Degree" to "°",
            "¼ Quarter" to "¼",
            "½ Half" to "½",
            "¾ Three quarters" to "¾"
        )
    }
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
                    binding.editBody.setText(RichTextSerializer.deserialize(it.body))
                    originalTitle = it.title
                    // Normalize so a just-loaded note doesn't falsely appear "changed"
                    originalBody = RichTextSerializer.serialize(binding.editBody.text)
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

        // ── Font size button ───────────────────────────────
        binding.btnTextSize.setOnClickListener { showFontSizeMenu(it) }

        // ── Tab button ─────────────────────────────────────
        binding.btnTab.setOnClickListener { insertTab() }

        // ── Text color button ──────────────────────────────
        binding.btnTextColor.setOnClickListener { showTextColorMenu(it) }

        // ── Text highlight button ──────────────────────────
        binding.btnTextHighlight.setOnClickListener { showHighlightMenu(it) }

        // ── Special characters button ──────────────────────
        // Single tap: insert last chosen char (default ✓)
        binding.btnSpecialChar.setOnClickListener { insertSpecialChar(lastSpecialChar) }
        // Long press: show picker menu
        binding.btnSpecialChar.setOnLongClickListener {
            showSpecialCharMenu(it)
            true
        }

        // TextWatcher: only apply active format spans to newly typed characters
        binding.editBody.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isApplyingFormat || isHandlingTab || count == 0) return
                val hasAnyFormat = isBoldActive || isItalicActive || isUnderlineActive ||
                    activeFontSizeSp != null || activeTextColor != null || activeHighlightColor != null
                if (!hasAnyFormat) return
                isApplyingFormat = true
                val text = binding.editBody.text as? Spannable ?: run { isApplyingFormat = false; return }
                if (isBoldActive)
                    text.setSpan(StyleSpan(Typeface.BOLD), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isItalicActive)
                    text.setSpan(StyleSpan(Typeface.ITALIC), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isUnderlineActive)
                    text.setSpan(UnderlineSpan(), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                activeFontSizeSp?.let { sp ->
                    text.setSpan(AbsoluteSizeSpan(sp, true), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                activeTextColor?.let { color ->
                    text.setSpan(ForegroundColorSpan(color), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                activeHighlightColor?.let { color ->
                    text.setSpan(BackgroundColorSpan(color), start, start + count, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                isApplyingFormat = false
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // (no setOnKeyListener — it breaks soft keyboard input on wrapped lines)

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

        // Pad the scroll view bottom when the keyboard opens so the cursor stays visible
        val scrollBasePadding = binding.bodyScrollView.paddingBottom
        var lastKbHeight = 0
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeInset = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val kbHeight = (imeInset.bottom - navInset.bottom).coerceAtLeast(0)
            if (kbHeight != lastKbHeight) {
                lastKbHeight = kbHeight
                binding.bodyScrollView.setPadding(
                    binding.bodyScrollView.paddingLeft,
                    binding.bodyScrollView.paddingTop,
                    binding.bodyScrollView.paddingRight,
                    scrollBasePadding + kbHeight
                )
                // After the padding settles, scroll so the cursor line is visible
                if (kbHeight > 0) {
                    binding.bodyScrollView.postDelayed({
                        val layout = binding.editBody.layout ?: return@postDelayed
                        val cursorLine = layout.getLineForOffset(
                            binding.editBody.selectionStart.coerceAtLeast(0))
                        val cursorBottom = layout.getLineBottom(cursorLine) +
                            binding.editBody.paddingTop
                        val scrollTo = cursorBottom -
                            (binding.bodyScrollView.height - kbHeight - scrollBasePadding)
                        if (scrollTo > binding.bodyScrollView.scrollY) {
                            binding.bodyScrollView.smoothScrollTo(0, scrollTo)
                        }
                    }, 100)
                }
            }
            insets
        }

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

    // ── Font size ──────────────────────────────────────────────────────────────
    private fun showFontSizeMenu(anchor: View) {
        val btn = anchor as? android.widget.Button
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, "Default")
        FONT_SIZES.forEachIndexed { index, (label, _, _) ->
            popup.menu.add(0, index + 1, index + 1, label)
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 0) {
                if (hasSelection()) {
                    val text = binding.editBody.text as? Spannable ?: return@setOnMenuItemClickListener true
                    val start = binding.editBody.selectionStart
                    val end = binding.editBody.selectionEnd
                    text.getSpans(start, end, AbsoluteSizeSpan::class.java).forEach { text.removeSpan(it) }
                }
                activeFontSizeSp = null
                btn?.text = "D"
                btn?.isSelected = false
            } else {
                val (_, letter, sp) = FONT_SIZES[item.itemId - 1]
                activeFontSizeSp = sp
                btn?.text = letter
                btn?.isSelected = false  // no background change for size button
                if (hasSelection()) {
                    val text = binding.editBody.text as? Spannable ?: return@setOnMenuItemClickListener true
                    val start = binding.editBody.selectionStart
                    val end = binding.editBody.selectionEnd
                    text.getSpans(start, end, AbsoluteSizeSpan::class.java).forEach { text.removeSpan(it) }
                    text.setSpan(AbsoluteSizeSpan(sp, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            true
        }
        popup.show()
    }

    // ── Tab logic ──────────────────────────────────────────────────────────────
    private fun insertTab() {
        val text = binding.editBody.text ?: return
        val cursor = binding.editBody.selectionStart.coerceAtLeast(0)
        val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
        val prefix = text.substring(lineStart, cursor)
        var tabCount = 0; var idx = 0
        while (idx + TAB_SPACES.length <= prefix.length &&
            prefix.substring(idx, idx + TAB_SPACES.length) == TAB_SPACES) {
            tabCount++; idx += TAB_SPACES.length
        }
        if (tabCount >= MAX_TABS) return
        isHandlingTab = true
        try { text.insert(cursor, TAB_SPACES) }
        finally { isHandlingTab = false }
    }


    // ── Text color ─────────────────────────────────────────────────────────────
    private fun showTextColorMenu(anchor: View) {
        val colorOptions = listOf(
            "Default (black)" to null as Int?,
            "Red"      to Color.parseColor("#D32F2F"),
            "Orange"   to Color.parseColor("#E65100"),
            "Yellow"   to Color.parseColor("#F9A825"),
            "Green"    to Color.parseColor("#2E7D32"),
            "Blue"     to Color.parseColor("#1565C0"),
            "Purple"   to Color.parseColor("#6A1B9A"),
            "Pink"     to Color.parseColor("#AD1457"),
            "Gray"     to Color.parseColor("#616161")
        )
        val popup = android.widget.PopupMenu(this, anchor)
        colorOptions.forEachIndexed { index, (label, _) ->
            popup.menu.add(0, index, index, label)
        }
        popup.setOnMenuItemClickListener { item ->
            val (_, color) = colorOptions[item.itemId]
            activeTextColor = color
            // Update button background to show the active color
            applyColorButtonBackground(anchor, color)
            if (hasSelection()) {
                val text = binding.editBody.text as? Spannable ?: return@setOnMenuItemClickListener true
                val start = binding.editBody.selectionStart
                val end = binding.editBody.selectionEnd
                text.getSpans(start, end, ForegroundColorSpan::class.java).forEach { text.removeSpan(it) }
                if (color != null) {
                    text.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            true
        }
        popup.show()
    }

    /**
     * Sets the button background to a solid rounded rectangle in [color],
     * or reverts to the default outlined bg_format_button when [color] is null.
     */
    private fun applyColorButtonBackground(view: View, color: Int?) {
        if (color == null) {
            view.background = ContextCompat.getDrawable(this, R.drawable.bg_format_button)
        } else {
            val shape = android.graphics.drawable.GradientDrawable()
            shape.cornerRadius = 8 * resources.displayMetrics.density
            shape.setColor(color)
            view.background = shape
        }
    }

    // ── Text highlight / background color ─────────────────────────────────────
    private fun showHighlightMenu(anchor: View) {
        val colorOptions = listOf(
            "None"         to null as Int?,
            "Yellow"       to Color.parseColor("#FFF9A825"),
            "Green"        to Color.parseColor("#FFA5D6A7"),
            "Blue"         to Color.parseColor("#FF90CAF9"),
            "Pink"         to Color.parseColor("#FFF48FB1"),
            "Orange"       to Color.parseColor("#FFFFCC80"),
            "Purple"       to Color.parseColor("#FFCE93D8"),
            "Gray"         to Color.parseColor("#FFE0E0E0")
        )
        val popup = android.widget.PopupMenu(this, anchor)
        colorOptions.forEachIndexed { index, (label, _) ->
            popup.menu.add(0, index, index, label)
        }
        popup.setOnMenuItemClickListener { item ->
            val (_, color) = colorOptions[item.itemId]
            activeHighlightColor = color
            applyColorButtonBackground(anchor, color)
            if (hasSelection()) {
                val text = binding.editBody.text as? Spannable ?: return@setOnMenuItemClickListener true
                val start = binding.editBody.selectionStart
                val end = binding.editBody.selectionEnd
                text.getSpans(start, end, BackgroundColorSpan::class.java).forEach { text.removeSpan(it) }
                if (color != null) {
                    text.setSpan(BackgroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            true
        }
        popup.show()
    }

    // ── Special characters ─────────────────────────────────────────────────────
    private fun insertSpecialChar(char: String) {
        val text = binding.editBody.text ?: return
        val cursor = binding.editBody.selectionStart.coerceAtLeast(0)
        text.insert(cursor, char)
    }

    private fun showSpecialCharMenu(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        SPECIAL_CHARS.forEachIndexed { index, (label, _) ->
            popup.menu.add(0, index, index, label)
        }
        popup.setOnMenuItemClickListener { item ->
            val (_, char) = SPECIAL_CHARS[item.itemId]
            lastSpecialChar = char
            // Update button text to show the chosen character
            (anchor as? android.widget.Button)?.text = char
            insertSpecialChar(char)
            true
        }
        popup.show()
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
                    val body = RichTextSerializer.serialize(binding.editBody.text)
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
        val body = RichTextSerializer.serialize(binding.editBody.text)
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
        val body = RichTextSerializer.serialize(binding.editBody.text)
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
