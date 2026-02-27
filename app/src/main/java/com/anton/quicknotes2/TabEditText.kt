package com.anton.quicknotes2

import android.content.Context
import android.graphics.Rect
import android.text.InputType
import android.util.AttributeSet
import android.view.ActionMode
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

class TabEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    companion object {
        const val TAB_SPACES = "      " // 6 spaces
    }

    /**
     * Intercept the floating copy/paste toolbar.
     * We compute a content rect in SCREEN coordinates that sits just below the
     * selected text, so Android always places the popup below the selection
     * rather than above it (where it would cover our formatting toolbar).
     *
     * We only do this when the selection is close to the top of the visible
     * screen area; otherwise we leave the default behaviour untouched.
     */
    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        if (type == ActionMode.TYPE_FLOATING && callback is ActionMode.Callback2) {
            return super.startActionMode(BelowSelectionCallback(callback), type)
        }
        return super.startActionMode(callback, type)
    }

    private inner class BelowSelectionCallback(
        private val wrapped: ActionMode.Callback2
    ) : ActionMode.Callback2() {

        override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu) =
            wrapped.onCreateActionMode(mode, menu)

        override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu) =
            wrapped.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem) =
            wrapped.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: ActionMode) =
            wrapped.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: android.view.View, outRect: Rect) {
            // Let Android compute the default rect first.
            // outRect is in VIEW-LOCAL coordinates of this EditText.
            wrapped.onGetContentRect(mode, view, outRect)

            val density = resources.displayMetrics.density

            // Where does this EditText start in the window?
            val editPos = IntArray(2)
            this@TabEditText.getLocationInWindow(editPos)
            val editTopInWindow = editPos[1]

            // Selection top in window coordinates = editTopInWindow + outRect.top
            // The popup is ~48 dp tall and tries to appear above the selection.
            // It would overlap our toolbar if:
            //   (editTopInWindow + outRect.top) - popupH  <  editTopInWindow
            // → outRect.top  <  popupH
            val popupH = (48 * density).toInt()
            val gap    = (8  * density).toInt()

            if (outRect.top < popupH) {
                // Not enough room above — move the rect to just below the selection
                // so Android is forced to place the popup below the text.
                val textLayout = this@TabEditText.layout ?: return
                val selEnd = this@TabEditText.selectionEnd
                    .coerceAtLeast(this@TabEditText.selectionStart.coerceAtLeast(0))
                val line = textLayout.getLineForOffset(selEnd)
                val lineBottom = textLayout.getLineBottom(line) +
                    this@TabEditText.paddingTop - this@TabEditText.scrollY

                outRect.top    = lineBottom + gap
                outRect.bottom = outRect.top + (outRect.bottom - outRect.top)
                    .coerceAtLeast((20 * density).toInt())
                // left/right unchanged — popup centres over the selection horizontally
            }
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return TabInputConnection(base)
    }

    private inner class TabInputConnection(
        target: InputConnection
    ) : InputConnectionWrapper(target, true) {

        // ── Enter: carry forward tab indentation ──────────────────────────────

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text != null && '\n' in text) {
                return super.commitText(withIndent(text), newCursorPosition)
            }
            return super.commitText(text, newCursorPosition)
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text != null && '\n' in text) {
                return super.setComposingText(withIndent(text), newCursorPosition)
            }
            return super.setComposingText(text, newCursorPosition)
        }

        private fun withIndent(text: CharSequence): String = buildString {
            for (ch in text) {
                append(ch)
                if (ch == '\n') append(currentLineIndent())
            }
        }

        // ── Backspace: delete whole tab group in one press ────────────────────

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0) {
                val editable = this@TabEditText.text
                val cursor = this@TabEditText.selectionStart
                if (editable != null && cursor >= TAB_SPACES.length) {
                    val lineStart = editable.lastIndexOf('\n', cursor - 1)
                        .let { if (it < 0) 0 else it + 1 }
                    if (editable.substring(lineStart, cursor).endsWith(TAB_SPACES)) {
                        return super.deleteSurroundingText(TAB_SPACES.length, 0)
                    }
                }
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        // Hardware keyboard backspace
        override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                event.action == android.view.KeyEvent.ACTION_DOWN) {
                val editable = this@TabEditText.text
                val cursor = this@TabEditText.selectionStart
                if (editable != null && cursor >= TAB_SPACES.length) {
                    val lineStart = editable.lastIndexOf('\n', cursor - 1)
                        .let { if (it < 0) 0 else it + 1 }
                    if (editable.substring(lineStart, cursor).endsWith(TAB_SPACES)) {
                        editable.delete(cursor - TAB_SPACES.length, cursor)
                        return true
                    }
                }
            }
            return super.sendKeyEvent(event)
        }

        // ── Helper ────────────────────────────────────────────────────────────

        private fun currentLineIndent(): String {
            val editable = this@TabEditText.text ?: return ""
            val cursor = this@TabEditText.selectionStart.coerceAtLeast(0)
            val lineStart = editable.lastIndexOf('\n', cursor - 1)
                .let { if (it < 0) 0 else it + 1 }
            val line = editable.substring(lineStart, cursor)
            val sb = StringBuilder()
            var i = 0
            while (i + TAB_SPACES.length <= line.length &&
                line.substring(i, i + TAB_SPACES.length) == TAB_SPACES) {
                sb.append(TAB_SPACES); i += TAB_SPACES.length
            }
            return sb.toString()
        }
    }
}
