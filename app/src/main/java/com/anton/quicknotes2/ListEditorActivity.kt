package com.anton.quicknotes2

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.NoteDatabase
import com.anton.quicknotes2.data.NoteList
import com.anton.quicknotes2.data.NoteListItem
import com.anton.quicknotes2.data.NoteRepository
import com.anton.quicknotes2.databinding.ActivityListEditorBinding
import com.anton.quicknotes2.ui.NoteViewModel
import com.anton.quicknotes2.ui.NoteViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Collections

class ListEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIST_ID = "extra_list_id"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
    }

    private lateinit var binding: ActivityListEditorBinding
    private val viewModel: NoteViewModel by viewModels {
        val db = NoteDatabase.getDatabase(applicationContext)
        NoteViewModelFactory(NoteRepository(db.noteDao(), db.folderDao(), db.noteImageDao(), db.whiteboardDao(), db.noteListDao(), db.dividerDao()))
    }

    private var listId: Int = -1
    private var folderId: Int? = null
    private var savedTitle: String = ""

    // In-memory rows — each has a NoteListItem (id=-1 if not yet persisted) and display state
    private data class Row(var item: NoteListItem, var dirty: Boolean = false)
    private val rows = mutableListOf<Row>()
    private lateinit var rowAdapter: RowAdapter
    private lateinit var touchHelper: ItemTouchHelper

    // True while a drag is in progress — suppresses renumber during move
    private var isDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listId = intent.getIntExtra(EXTRA_LIST_ID, -1)
        folderId = intent.getIntExtra(EXTRA_FOLDER_ID, -1).takeIf { it != -1 }

        rowAdapter = RowAdapter()

        // Use DefaultItemAnimator with a longer move duration for smooth slide-down
        val animator = DefaultItemAnimator()
        animator.moveDuration   = 350
        animator.removeDuration = 0
        animator.addDuration    = 0
        animator.changeDuration = 0

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.itemAnimator = animator
        binding.recyclerView.adapter = rowAdapter

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                src: RecyclerView.ViewHolder,
                tgt: RecyclerView.ViewHolder
            ): Boolean {
                val from = src.bindingAdapterPosition
                val to = tgt.bindingAdapterPosition
                val fromRow = rows.getOrNull(from) ?: return false
                val toRow   = rows.getOrNull(to)   ?: return false
                // Don't let unchecked items drag past checked items or vice-versa
                if (fromRow.item.checked || toRow.item.checked) return false
                Collections.swap(rows, from, to)
                rowAdapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                isDragging = false
                // Renumber and refresh labels once drag is fully done
                renumberUnchecked()
            }

            override fun isLongPressDragEnabled() = false
        })
        touchHelper.attachToRecyclerView(binding.recyclerView)
        rowAdapter.touchHelper = touchHelper

        // Load existing list
        if (listId != -1) {
            lifecycleScope.launch {
                val list = viewModel.getListById(listId) ?: return@launch
                binding.editTitle.setText(list.title)
                savedTitle = list.title
                val items = viewModel.getItemsForListDirect(listId)
                rows.clear()
                items.forEach { rows.add(Row(it)) }
                rowAdapter.notifyDataSetChanged()
            }
        }

        binding.fabAddItem.setOnClickListener { addNewRow() }

        // Use WindowInsets to track keyboard height and pad the RecyclerView accordingly.
        // We keep the base padding (set in XML) so the list can always be scrolled past
        // the last item. We ADD the keyboard height so items above it remain reachable.
        val rvBasePadding = binding.recyclerView.paddingBottom
        var lastKeyboardHeight = 0
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeInset = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboardHeight = (imeInset.bottom - navInset.bottom).coerceAtLeast(0)
            if (keyboardHeight != lastKeyboardHeight) {
                lastKeyboardHeight = keyboardHeight
                binding.recyclerView.setPadding(
                    binding.recyclerView.paddingLeft,
                    binding.recyclerView.paddingTop,
                    binding.recyclerView.paddingRight,
                    rvBasePadding + keyboardHeight
                )
                if (keyboardHeight > 0) {
                    // Give layout a moment to settle, then bring focused item into view
                    binding.recyclerView.postDelayed({ scrollFocusedItemIntoView() }, 100)
                }
            }
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    MaterialAlertDialogBuilder(this@ListEditorActivity)
                        .setTitle("Unsaved changes")
                        .setMessage("Leave without saving?")
                        .setPositiveButton("Leave") { _, _ -> finish() }
                        .setNegativeButton("Stay", null)
                        .show()
                } else finish()
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val r = Rect(); v.getGlobalVisibleRect(r)
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentTitle = binding.editTitle.text.toString().trim()
        if (currentTitle != savedTitle) return true
        if (listId == -1 && rows.isNotEmpty()) return true
        return rows.any { it.dirty || it.item.id == 0 }
    }

    private fun addNewRow() {
        val nextPos = rows.count { !it.item.checked }
        val newItem = NoteListItem(listId = listId, text = "", position = nextPos, checked = false)
        // Insert before the first checked item so it appears at end of unchecked block
        val insertAt = rows.indexOfFirst { it.item.checked }.takeIf { it >= 0 } ?: rows.size
        rows.add(insertAt, Row(newItem, dirty = true))
        rowAdapter.notifyItemInserted(insertAt)
        renumberUnchecked()
        binding.recyclerView.scrollToPosition(insertAt)
        // Focus the new row's EditText
        binding.recyclerView.post {
            val vh = binding.recyclerView.findViewHolderForAdapterPosition(insertAt)
            (vh?.itemView?.findViewById<EditText>(R.id.editItemText))?.requestFocus()
        }
    }

    /**
     * Walk the unchecked rows in their current list order and assign position = index.
     * Only updates labels; does NOT call notifyDataSetChanged during a drag.
     */
    private fun renumberUnchecked() {
        renumberUncheckedSilent()
        if (!isDragging) rowAdapter.notifyDataSetChanged()
    }

    private fun checkItem(pos: Int) {
        val r = rows[pos]
        r.item = r.item.copy(checked = true)
        r.dirty = true
        val dest = rows.size - 1
        rows.removeAt(pos)
        rows.add(r)
        rowAdapter.notifyItemMoved(pos, dest)
        // Wait for the move animation to finish before rebinding + renumbering
        binding.recyclerView.postDelayed({
            renumberUncheckedSilent()
            rowAdapter.notifyItemChanged(dest)
            // Refresh numbers on all unchecked rows
            rows.forEachIndexed { i, row -> if (!row.item.checked) rowAdapter.notifyItemChanged(i) }
        }, 370)
    }

    private fun uncheckItem(pos: Int) {
        val r = rows[pos]
        r.item = r.item.copy(checked = false)
        r.dirty = true
        val uncheckedCount = rows.count { !it.item.checked }
        val targetPos = r.item.position.coerceIn(0, uncheckedCount)
        rows.removeAt(pos)
        rows.add(targetPos, r)
        rowAdapter.notifyItemMoved(pos, targetPos)
        binding.recyclerView.postDelayed({
            renumberUncheckedSilent()
            rows.forEachIndexed { i, _ -> rowAdapter.notifyItemChanged(i) }
        }, 370)
    }

    /** Update position numbers in the data only — no notify calls. */
    private fun renumberUncheckedSilent() {
        var num = 0
        rows.forEach { row ->
            if (!row.item.checked) {
                if (row.item.position != num) {
                    row.item = row.item.copy(position = num)
                    row.dirty = true
                }
                num++
            }
        }
    }

    private fun scrollFocusedItemIntoView() {
        val focused = currentFocus ?: return
        // Walk up the view tree to find the direct child of RecyclerView
        var v: View? = focused
        while (v != null) {
            val parent = v.parent
            if (parent === binding.recyclerView) break
            v = parent as? View
        }
        val child = v ?: return


        // Coordinates relative to the RecyclerView
        val rvTop = binding.recyclerView.paddingTop
        // Visible bottom = RecyclerView height minus the bottom padding (keyboard area)
        val rvVisibleBottom = binding.recyclerView.height - binding.recyclerView.paddingBottom

        val childTop = child.top
        val childBottom = child.bottom

        when {
            childBottom > rvVisibleBottom -> {
                // Item is below the visible area — scroll down so it's fully visible
                binding.recyclerView.smoothScrollBy(0, childBottom - rvVisibleBottom + 8)
            }
            childTop < rvTop -> {
                // Item is above the visible area — scroll up
                binding.recyclerView.smoothScrollBy(0, childTop - rvTop - 8)
            }
            // else already visible — no scroll needed
        }
    }

    private fun save() {
        val title = binding.editTitle.text.toString().trim()
        savedTitle = title
        lifecycleScope.launch {
            if (listId == -1) {
                listId = viewModel.insertList(
                    NoteList(title = title, folderId = folderId)
                ).toInt()
                rows.forEach { it.item = it.item.copy(listId = listId) }
            } else {
                val existing = viewModel.getListById(listId) ?: return@launch
                viewModel.updateList(existing.copy(title = title, timestamp = System.currentTimeMillis()))
            }
            rows.forEach { row ->
                when {
                    row.item.id == 0 -> {
                        val id = viewModel.insertListItem(row.item.copy(listId = listId))
                        row.item = row.item.copy(id = id.toInt(), listId = listId)
                        row.dirty = false
                    }
                    row.dirty -> {
                        viewModel.updateListItem(row.item)
                        row.dirty = false
                    }
                }
            }
            finish()
        }
    }

    // ── Inner RecyclerView adapter ─────────────────────────
    inner class RowAdapter : RecyclerView.Adapter<RowAdapter.RowVH>() {
        var touchHelper: ItemTouchHelper? = null

        inner class RowVH(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val dragHandle: ImageView  = view.findViewById(R.id.dragHandle)
            val textNumber: TextView   = view.findViewById(R.id.textNumber)
            val editText: EditText     = view.findViewById(R.id.editItemText)
            val checkBox: CheckBox     = view.findViewById(R.id.checkItem)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteItem)
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_list_entry, parent, false)
            return RowVH(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: RowVH, position: Int) {
            val row  = rows[position]
            val item = row.item

            // Number / check mark
            if (!item.checked) {
                holder.textNumber.text  = "${item.position + 1}."
                holder.textNumber.alpha = 1f
            } else {
                holder.textNumber.text  = "✓"
                holder.textNumber.alpha = 0.4f
            }

            // Text + strikethrough
            holder.editText.setText(item.text)
            holder.editText.paintFlags = if (item.checked)
                holder.editText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            else
                holder.editText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.editText.alpha = if (item.checked) 0.5f else 1f

            holder.editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val p = holder.bindingAdapterPosition
                    if (p != RecyclerView.NO_ID.toInt()) {
                        // Short delay so the keyboard has time to open and insets are applied
                        binding.recyclerView.postDelayed({ scrollFocusedItemIntoView() }, 200)
                    }
                } else {
                    val p = holder.bindingAdapterPosition
                    if (p == RecyclerView.NO_ID.toInt()) return@setOnFocusChangeListener
                    val newText = holder.editText.text.toString()
                    if (newText != rows[p].item.text) {
                        rows[p].item  = rows[p].item.copy(text = newText)
                        rows[p].dirty = true
                    }
                }
            }

            // Checkbox — clear listener before setting isChecked to avoid re-entrancy
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = item.checked
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                val p = holder.bindingAdapterPosition
                if (p == RecyclerView.NO_ID.toInt()) return@setOnCheckedChangeListener
                if (isChecked) checkItem(p) else uncheckItem(p)
            }

            // Delete
            holder.btnDelete.setOnClickListener {
                val p = holder.bindingAdapterPosition
                if (p == RecyclerView.NO_ID.toInt()) return@setOnClickListener
                val r = rows[p]
                if (r.item.id > 0) lifecycleScope.launch { viewModel.deleteListItem(r.item) }
                rows.removeAt(p)
                notifyItemRemoved(p)
                renumberUnchecked()
            }

            // Drag handle — only active for unchecked items
            holder.dragHandle.alpha = if (item.checked) 0.15f else 0.4f
            holder.dragHandle.setOnTouchListener { _, event ->
                if (!item.checked && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper?.startDrag(holder)
                }
                false
            }
        }
    }
}
