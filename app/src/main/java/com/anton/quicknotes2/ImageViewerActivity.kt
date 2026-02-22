package com.anton.quicknotes2

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.anton.quicknotes2.databinding.ActivityImageViewerBinding
import com.anton.quicknotes2.databinding.ItemImageFullBinding
import java.util.Collections

class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val RESULT_REORDERED_URIS = "result_reordered_uris"
    }

    private lateinit var binding: ActivityImageViewerBinding
    private val uris = mutableListOf<String>()
    private lateinit var pagerAdapter: FullImageAdapter

    private var swipeStartY = 0f
    private var swipeDy = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val incoming = intent.getStringArrayListExtra(EXTRA_URIS) ?: arrayListOf()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        uris.addAll(incoming)

        pagerAdapter = FullImageAdapter(uris) { from, to ->
            if (from < to) for (i in from until to) Collections.swap(uris, i, i + 1)
            else for (i in from downTo to + 1) Collections.swap(uris, i, i - 1)
            updateDots(binding.viewPager.currentItem)
        }
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.setCurrentItem(startIndex, false)

        buildDots()
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })

        binding.btnClose.setOnClickListener { finishWithResult() }

        onBackPressedDispatcher.addCallback(this) {
            finishWithResult()
        }
    }

    internal fun handleSwipeDown(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartY = event.rawY
                swipeDy = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                swipeDy = event.rawY - swipeStartY
                if (swipeDy > 0) {
                    binding.viewPager.translationY = swipeDy
                    binding.root.alpha = 1f - (swipeDy / 600f).coerceIn(0f, 1f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeDy > 200f) {
                    finishWithResult()
                } else {
                    ObjectAnimator.ofFloat(binding.viewPager, "translationY", 0f).apply { duration = 200; start() }
                    ObjectAnimator.ofFloat(binding.root, "alpha", 1f).apply { duration = 200; start() }
                }
            }
        }
        return false
    }

    private fun buildDots() {
        binding.dotsLayout.removeAllViews()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        for (i in uris.indices) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp8, dp8).also {
                    it.marginEnd = dp8 / 2; it.marginStart = dp8 / 2
                }
                setBackgroundResource(android.R.drawable.presence_online)
                alpha = if (i == binding.viewPager.currentItem) 1f else 0.4f
            }
            binding.dotsLayout.addView(dot)
        }
    }

    private fun updateDots(selected: Int) {
        for (i in 0 until binding.dotsLayout.childCount) {
            binding.dotsLayout.getChildAt(i).alpha = if (i == selected) 1f else 0.4f
        }
    }

    private fun finishWithResult() {
        val result = Intent().putStringArrayListExtra(RESULT_REORDERED_URIS, ArrayList(uris))
        setResult(RESULT_OK, result)
        finish()
    }

    inner class FullImageAdapter(
        private val list: MutableList<String>,
        private val onMoved: (Int, Int) -> Unit
    ) : RecyclerView.Adapter<FullImageAdapter.VH>() {

        private var touchHelper: ItemTouchHelper? = null

        init {
            binding.viewPager.post {
                val rv = binding.viewPager.getChildAt(0) as? RecyclerView ?: return@post
                val cb = object : ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
                ) {
                    override fun onMove(r: RecyclerView, src: RecyclerView.ViewHolder, tgt: RecyclerView.ViewHolder): Boolean {
                        onMoved(src.bindingAdapterPosition, tgt.bindingAdapterPosition)
                        notifyItemMoved(src.bindingAdapterPosition, tgt.bindingAdapterPosition)
                        return true
                    }
                    override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
                }
                touchHelper = ItemTouchHelper(cb).also { it.attachToRecyclerView(rv) }
            }
        }

        inner class VH(val b: ItemImageFullBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(uri: String) {
                b.fullImage.setImageURI(Uri.parse(uri))
                b.root.setOnLongClickListener { touchHelper?.startDrag(this); true }
                b.root.setOnTouchListener { _, ev -> handleSwipeDown(ev) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemImageFullBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(list[position])
        override fun getItemCount() = list.size
    }
}
