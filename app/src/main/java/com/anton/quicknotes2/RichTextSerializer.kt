package com.anton.quicknotes2

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises/deserialises rich text (Spannable) to/from a JSON-based format that fully round-trips
 * all spans used by the note editor:
 *   bold, italic, underline, font-size (sp), foreground color, background/highlight color.
 *
 * Format:
 * {
 *   "text": "plain text content",
 *   "spans": [
 *     { "type": "bold",        "start": 0, "end": 4 },
 *     { "type": "italic",      "start": 0, "end": 4 },
 *     { "type": "underline",   "start": 0, "end": 4 },
 *     { "type": "size",        "start": 0, "end": 4, "sp": 20 },
 *     { "type": "fgColor",     "start": 0, "end": 4, "color": "#FFD32F2F" },
 *     { "type": "bgColor",     "start": 0, "end": 4, "color": "#FFF9A825" }
 *   ]
 * }
 *
 * For backward-compat, if the stored string doesn't start with '{'  it is treated as legacy HTML
 * (or plain text) and parsed with Html.fromHtml as before.
 */
object RichTextSerializer {

    private const val PREFIX = "{\"qnrt\":"   // unique prefix so we can detect our format

    fun serialize(spannable: CharSequence?): String {
        if (spannable == null) return ""
        val ssb = spannable as? Spannable ?: return spannable.toString()
        val text = ssb.toString()
        val spansArr = JSONArray()

        ssb.getSpans(0, ssb.length, Any::class.java).forEach { span ->
            val start = ssb.getSpanStart(span)
            val end   = ssb.getSpanEnd(span)
            if (start < 0 || end < 0) return@forEach
            val obj = when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD   -> JSONObject().apply { put("type","bold");      put("s",start); put("e",end) }
                    Typeface.ITALIC -> JSONObject().apply { put("type","italic");    put("s",start); put("e",end) }
                    else            -> null
                }
                is UnderlineSpan     -> JSONObject().apply { put("type","underline"); put("s",start); put("e",end) }
                is AbsoluteSizeSpan  -> JSONObject().apply {
                    put("type","size")
                    put("s",start); put("e",end)
                    // Store the value in sp (dip flag = true)
                    put("sp", if (span.dip) span.size else Math.round(span.size / 1f))
                }
                is ForegroundColorSpan -> JSONObject().apply {
                    put("type","fgColor"); put("s",start); put("e",end)
                    put("color", String.format("#%08X", span.foregroundColor))
                }
                is BackgroundColorSpan -> JSONObject().apply {
                    put("type","bgColor"); put("s",start); put("e",end)
                    put("color", String.format("#%08X", span.backgroundColor))
                }
                else -> null
            }
            obj?.let { spansArr.put(it) }
        }

        val root = JSONObject()
        root.put("qnrt", 1)      // version
        root.put("text", text)
        root.put("spans", spansArr)
        return root.toString()
    }

    /**
     * Deserialize.  Handles:
     *   - our JSON format (starts with {"qnrt":)
     *   - legacy HTML (contains < >)
     *   - plain text (anything else)
     */
    fun deserialize(stored: String): CharSequence {
        if (stored.startsWith("{\"qnrt\":")) {
            return try {
                val root    = JSONObject(stored)
                val text    = root.getString("text")
                val ssb     = SpannableStringBuilder(text)
                val arr     = root.optJSONArray("spans") ?: return ssb
                for (i in 0 until arr.length()) {
                    val obj   = arr.getJSONObject(i)
                    val start = obj.getInt("s")
                    val end   = obj.getInt("e")
                    if (start < 0 || end > text.length || start >= end) continue
                    when (obj.getString("type")) {
                        "bold"      -> ssb.setSpan(StyleSpan(Typeface.BOLD),   start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "italic"    -> ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "underline" -> ssb.setSpan(UnderlineSpan(),            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "size"      -> ssb.setSpan(AbsoluteSizeSpan(obj.getInt("sp"), true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "fgColor"   -> ssb.setSpan(ForegroundColorSpan(parseColor(obj.getString("color"))), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "bgColor"   -> ssb.setSpan(BackgroundColorSpan(parseColor(obj.getString("color"))), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                ssb
            } catch (_: Exception) {
                SpannableStringBuilder(stored)
            }
        }
        // Legacy: HTML or plain text
        return if (stored.contains("<") && stored.contains(">")) {
            android.text.Html.fromHtml(stored, android.text.Html.FROM_HTML_MODE_COMPACT)
        } else {
            SpannableStringBuilder(stored)
        }
    }

    private fun parseColor(hex: String): Int = android.graphics.Color.parseColor(hex)
}

