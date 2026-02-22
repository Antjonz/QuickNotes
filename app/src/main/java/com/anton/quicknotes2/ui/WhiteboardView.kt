package com.anton.quicknotes2.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

data class Stroke(
    val points: List<PointF>,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean
)

class WhiteboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── State ────────────────────────────────────────────────
    private val strokes = mutableListOf<Stroke>()
    private var currentPoints = mutableListOf<PointF>()

    var penColor: Int = Color.BLACK
    var penWidth: Float = 6f
    var isEraser: Boolean = false

    // ── Pan / Zoom ───────────────────────────────────────────
    private var offsetX = 0f
    private var offsetY = 0f
    private var scaleFactor = 1f

    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isPanning = false
    private var isDrawing = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = scaleFactor * detector.scaleFactor
                scaleFactor = newScale.coerceIn(0.1f, 10f)
                invalidate()
                return true
            }
        })

    // ── Paint ────────────────────────────────────────────────
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bgPaint = Paint().apply { color = Color.WHITE }

    // ── Draw ─────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)

        for (stroke in strokes) drawStroke(canvas, stroke)
        // draw in-progress stroke
        if (currentPoints.size >= 2) {
            drawPaint.color = if (isEraser) Color.WHITE else penColor
            drawPaint.strokeWidth = (if (isEraser) penWidth * 3 else penWidth) / scaleFactor
            drawPaint.xfermode = null
            val path = pointsToPath(currentPoints)
            canvas.drawPath(path, drawPaint)
        }

        canvas.restore()
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.size < 2) return
        drawPaint.color = if (stroke.isEraser) Color.WHITE else stroke.color
        drawPaint.strokeWidth = (if (stroke.isEraser) stroke.strokeWidth * 3 else stroke.strokeWidth) / scaleFactor
        drawPaint.xfermode = null
        canvas.drawPath(pointsToPath(stroke.points), drawPaint)
    }

    private fun pointsToPath(pts: List<PointF>): Path {
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        return path
    }

    // ── Touch ─────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount

        when {
            // Two fingers → pan (and scale is handled by scaleDetector)
            pointerCount >= 2 -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        // finish any in-progress stroke
                        if (isDrawing && currentPoints.size >= 2) commitStroke()
                        isDrawing = false
                        isPanning = true
                        lastPanX = event.getX(0)
                        lastPanY = event.getY(0)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isPanning && !scaleDetector.isInProgress) {
                            val dx = event.getX(0) - lastPanX
                            val dy = event.getY(0) - lastPanY
                            offsetX += dx
                            offsetY += dy
                            invalidate()
                        }
                        lastPanX = event.getX(0)
                        lastPanY = event.getY(0)
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        isPanning = false
                    }
                }
                return true
            }

            // Single finger → draw
            else -> {
                if (isPanning) return true
                val canvasX = (event.x - offsetX) / scaleFactor
                val canvasY = (event.y - offsetY) / scaleFactor
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDrawing = true
                        currentPoints.clear()
                        currentPoints.add(PointF(canvasX, canvasY))
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDrawing) {
                            currentPoints.add(PointF(canvasX, canvasY))
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDrawing && currentPoints.size >= 2) commitStroke()
                        isDrawing = false
                        invalidate()
                    }
                }
                return true
            }
        }
    }

    private fun commitStroke() {
        strokes.add(Stroke(currentPoints.toList(), penColor, penWidth, isEraser))
        currentPoints.clear()
    }

    // ── Public API ────────────────────────────────────────────
    fun clearBoard() {
        strokes.clear()
        currentPoints.clear()
        invalidate()
    }

    fun hasContent() = strokes.isNotEmpty()

    // ── Serialisation ─────────────────────────────────────────
    fun toJson(): String {
        val arr = JSONArray()
        for (stroke in strokes) {
            val obj = JSONObject()
            obj.put("color", stroke.color)
            obj.put("width", stroke.strokeWidth)
            obj.put("eraser", stroke.isEraser)
            val pts = JSONArray()
            for (p in stroke.points) {
                val pt = JSONObject()
                pt.put("x", p.x)
                pt.put("y", p.y)
                pts.put(pt)
            }
            obj.put("points", pts)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun fromJson(json: String) {
        strokes.clear()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val color = obj.getInt("color")
                val width = obj.getDouble("width").toFloat()
                val eraser = obj.optBoolean("eraser", false)
                val ptsArr = obj.getJSONArray("points")
                val pts = mutableListOf<PointF>()
                for (j in 0 until ptsArr.length()) {
                    val pt = ptsArr.getJSONObject(j)
                    pts.add(PointF(pt.getDouble("x").toFloat(), pt.getDouble("y").toFloat()))
                }
                if (pts.size >= 2) strokes.add(Stroke(pts, color, width, eraser))
            }
        } catch (_: Exception) {}
        invalidate()
    }
}

