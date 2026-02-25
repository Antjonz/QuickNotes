package com.anton.quicknotes2

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anton.quicknotes2.data.NoteDatabase
import com.anton.quicknotes2.data.NoteRepository
import com.anton.quicknotes2.data.Whiteboard
import com.anton.quicknotes2.databinding.ActivityWhiteboardEditorBinding
import com.anton.quicknotes2.ui.NoteViewModel
import com.anton.quicknotes2.ui.NoteViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class WhiteboardEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WHITEBOARD_ID = "extra_whiteboard_id"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
    }

    private lateinit var binding: ActivityWhiteboardEditorBinding

    private val viewModel: NoteViewModel by viewModels {
        val db = NoteDatabase.getDatabase(applicationContext)
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao(), db.dividerDao()))
    }

    private var whiteboardId: Int = -1
    private var folderId: Int? = null
    private var savedStrokesJson: String = "[]"
    private var savedTitle: String = ""
    // true = eraser active, false = pen active
    private var eraserActive = false

    private val colors = listOf(
        Color.BLACK, Color.DKGRAY, Color.GRAY,
        Color.RED, 0xFFE53935.toInt(), 0xFFFF6D00.toInt(),
        0xFFF9A825.toInt(), 0xFF2E7D32.toInt(), 0xFF1565C0.toInt(),
        0xFF6A1B9A.toInt(), 0xFF00838F.toInt(), Color.WHITE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhiteboardEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        whiteboardId = intent.getIntExtra(EXTRA_WHITEBOARD_ID, -1)
        folderId = intent.getIntExtra(EXTRA_FOLDER_ID, -1).takeIf { it != -1 }

        if (whiteboardId != -1) {
            lifecycleScope.launch {
                val wb = viewModel.getWhiteboardById(whiteboardId) ?: return@launch
                binding.editTitle.setText(wb.title)
                binding.whiteboardView.fromJson(wb.strokesJson)
                savedStrokesJson = wb.strokesJson
                savedTitle = wb.title
            }
        }

        // Pen size slider
        binding.seekPenSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.whiteboardView.penWidth = (progress + 1).toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        binding.whiteboardView.penWidth = (binding.seekPenSize.progress + 1).toFloat()

        // Pen button — starts selected
        setToolSelected(pen = true)
        binding.btnPen.setOnClickListener {
            eraserActive = false
            binding.whiteboardView.isEraser = false
            setToolSelected(pen = true)
        }

        // Eraser toggle
        binding.btnEraser.setOnClickListener {
            eraserActive = true
            binding.whiteboardView.isEraser = true
            setToolSelected(pen = false)
        }

        // Clear board
        binding.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear board")
                .setMessage("Are you sure you want to erase everything?")
                .setPositiveButton("Clear") { _, _ -> binding.whiteboardView.clearBoard() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Color picker — also switches back to pen
        updateColorSwatch(binding.whiteboardView.penColor)
        binding.btnColor.setOnClickListener { showColorPicker() }

        // Back with unsaved changes warning
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    MaterialAlertDialogBuilder(this@WhiteboardEditorActivity)
                        .setTitle("Unsaved changes")
                        .setMessage("You have unsaved changes. Leave without saving?")
                        .setPositiveButton("Leave") { _, _ -> finish() }
                        .setNegativeButton("Stay", null)
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    /** Highlight the active tool button using the selected state. */
    private fun setToolSelected(pen: Boolean) {
        binding.btnPen.isSelected = pen
        binding.btnEraser.isSelected = !pen
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_whiteboard_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> { save(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentTitle = binding.editTitle.text.toString().trim()
        val currentStrokes = binding.whiteboardView.toJson()
        return currentTitle != savedTitle || currentStrokes != savedStrokesJson
    }

    private fun save() {
        val title = binding.editTitle.text.toString().trim()
        val strokes = binding.whiteboardView.toJson()
        savedTitle = title
        savedStrokesJson = strokes
        lifecycleScope.launch {
            if (whiteboardId == -1) {
                whiteboardId = viewModel.insertWhiteboard(
                    Whiteboard(title = title, strokesJson = strokes, folderId = folderId)
                ).toInt()
            } else {
                val existing = viewModel.getWhiteboardById(whiteboardId) ?: return@launch
                viewModel.updateWhiteboard(existing.copy(
                    title = title,
                    strokesJson = strokes,
                    timestamp = System.currentTimeMillis()
                ))
            }
            finish()
        }
    }

    private fun updateColorSwatch(color: Int) {
        val bg = binding.btnColor.background as? GradientDrawable
            ?: GradientDrawable().also { binding.btnColor.background = it }
        bg.shape = GradientDrawable.OVAL
        bg.setColor(color)
        bg.setStroke(4, 0x88000000.toInt())
    }

    private fun showColorPicker() {
        val names = arrayOf(
            "Black", "Dark gray", "Gray",
            "Red", "Deep red", "Orange",
            "Yellow", "Green", "Blue",
            "Purple", "Teal", "White"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Pick a colour")
            .setItems(names) { _, which ->
                val color = colors[which]
                binding.whiteboardView.penColor = color
                updateColorSwatch(color)
                // Picking a colour switches back to pen
                eraserActive = false
                binding.whiteboardView.isEraser = false
                setToolSelected(pen = true)
            }
            .show()
    }
}
