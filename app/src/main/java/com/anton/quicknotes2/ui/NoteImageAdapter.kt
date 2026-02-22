package com.anton.quicknotes2.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.NoteImage
import com.anton.quicknotes2.databinding.ItemImageThumbnailBinding
import java.util.Collections

class NoteImageAdapter(
    private val onImageClick: (Int) -> Unit,
    private val onImageDelete: (NoteImage) -> Unit,
    private val onOrderChanged: (List<NoteImage>) -> Unit
) : RecyclerView.Adapter<NoteImageAdapter.ImageViewHolder>() {

    private val items = mutableListOf<NoteImage>()
    var itemTouchHelper: ItemTouchHelper? = null

    // When true the × badges are visible and tapping them deletes immediately
    var isDeleteMode: Boolean = false
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    fun submitList(newItems: List<NoteImage>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun getItems(): List<NoteImage> = items.toList()

    fun moveItem(from: Int, to: Int) {
        if (from < to) for (i in from until to) Collections.swap(items, i, i + 1)
        else for (i in from downTo to + 1) Collections.swap(items, i, i - 1)
        notifyItemMoved(from, to)
    }

    override fun getItemCount() = items.size

    inner class ImageViewHolder(private val binding: ItemImageThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(image: NoteImage) {
            binding.imageThumb.setImageURI(Uri.parse(image.uri))

            // Use bindingAdapterPosition at click time so reordered positions are always correct
            binding.imageThumb.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onImageClick(pos)
            }

            // Show/hide the × badge based on delete mode; no confirmation in delete mode
            binding.btnDeleteImage.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
            binding.btnDeleteImage.setOnClickListener { onImageDelete(image) }

            binding.root.setOnLongClickListener {
                itemTouchHelper?.startDrag(this)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ImageViewHolder(
            ItemImageThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) =
        holder.bind(items[position])
}
