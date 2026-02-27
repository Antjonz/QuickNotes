package com.anton.quicknotes2

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
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
