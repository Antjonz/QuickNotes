package com.anton.quicknotes2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anton.quicknotes2.data.Note
import com.anton.quicknotes2.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.*

class NoteAdapter(
    private val onNoteClick: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.NoteViewHolder>(NoteDiffCallback()) {

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.textTitle.text = note.title.ifBlank { "Untitled" }
            binding.textBody.text = note.body
            binding.textTimestamp.text = formatDate(note.timestamp)
            binding.root.setOnClickListener { onNoteClick(note) }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getNoteAt(position: Int): Note = getItem(position)

    private class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
    }
}

