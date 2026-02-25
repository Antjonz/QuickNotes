package com.anton.quicknotes2.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ColorPickerDialog {

    // A curated palette of note-friendly colors
    private val COLORS = listOf(
        "#FFFFFFFF" to "White",
        "#FFFFE0B2" to "Orange light",
        "#FFFFF9C4" to "Yellow",
        "#FFE8F5E9" to "Green light",
        "#FFE3F2FD" to "Blue light",
        "#FFFCE4EC" to "Pink light",
        "#FFF3E5F5" to "Purple light",
        "#FFEFEBE9" to "Brown light",
        "#FFFF8A65" to "Deep orange",
        "#FFFFD54F" to "Amber",
        "#FF81C784" to "Green",
        "#FF64B5F6" to "Blue",
        "#FFF06292" to "Pink",
        "#FFBA68C8" to "Purple",
        "#FFA1887F" to "Brown",
        "#FF90A4AE" to "Blue grey",
    )

    /**
     * Show color picker for note label color.
     * White (#FFFFFFFF) is treated as "reset to default" and calls onColorSelected(null).
     */
    fun show(
        context: Context,
        currentColor: String?,
        onColorSelected: (String?) -> Unit
    ) {
        showInternal(context, currentColor, labelMode = true) { hex ->
            onColorSelected(if (hex == "#FFFFFFFF") null else hex)
        }
    }

    /**
     * Show color picker for icon solid color.
     * All colors (including white) are passed back as-is — caller decides what to do.
     */
    fun showForIcon(
        context: Context,
        currentColor: String?,
        onColorSelected: (String) -> Unit
    ) {
        showInternal(context, currentColor, labelMode = false) { hex ->
            onColorSelected(hex)
        }
    }

    private fun showInternal(
        context: Context,
        currentColor: String?,
        labelMode: Boolean,
        callback: (String) -> Unit
    ) {
        val grid = GridLayout(context).apply {
            columnCount = 4
            setPadding(24, 16, 24, 16)
        }

        var dialog: AlertDialog? = null

        COLORS.forEach { (hex, _) ->
            val swatch = View(context).apply {
                val sizePx = (56 * context.resources.displayMetrics.density).toInt()
                val marginPx = (4 * context.resources.displayMetrics.density).toInt()
                val params = GridLayout.LayoutParams().apply {
                    width = sizePx; height = sizePx
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                layoutParams = params
                // Highlight current color with a purple stroke
                val isCurrent = hex.equals(currentColor ?: "#FFFFFFFF", ignoreCase = true)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 8f * context.resources.displayMetrics.density
                    setColor(Color.parseColor(hex))
                    setStroke(
                        (if (isCurrent) 3 else 1) * context.resources.displayMetrics.density.toInt(),
                        if (isCurrent) Color.parseColor("#FF6650A4") else Color.parseColor("#FFBDBDBD")
                    )
                }
            }
            grid.addView(swatch)
            swatch.setOnClickListener {
                callback(hex)
                dialog?.dismiss()
            }
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (labelMode) "Choose label color" else "Choose icon color")
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }
}
