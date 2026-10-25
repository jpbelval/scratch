package com.example.scratchfade

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max
import kotlin.math.min

class ScratchView(context: Context) : View(context) {
    private var photo: Bitmap? = null
    private var cover: Bitmap? = null
    private var coverCanvas: Canvas? = null
    private val scratchPath = Path()
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 58f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val coverTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 65, 81)
        textSize = 18f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(148, 163, 184)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val handler = Handler(Looper.getMainLooper())
    private var fading = false
    private var alpha = 255
    private var scratchedDistance = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var onRevealStateChanged: ((Boolean) -> Unit)? = null
    private val density = resources.displayMetrics.density

    fun setOnRevealStateChanged(listener: (Boolean) -> Unit) { onRevealStateChanged = listener }

    fun loadPhoto(uri: Uri) {
        try {
            val input = context.contentResolver.openInputStream(uri)
            val decoded = input.use { BitmapFactory.decodeStream(it) } ?: return
            photo = decoded
            resetCard()
        } catch (_: Exception) { }
    }

    fun resetCard() {
        handler.removeCallbacksAndMessages(null)
        fading = false
        alpha = 255
        scratchedDistance = 0f
        scratchPath.reset()
        createCover()
        onRevealStateChanged?.invoke(false)
        invalidate()
    }

    private fun createCover() {
        if (width <= 0 || height <= 0) return
        cover = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        coverCanvas = Canvas(cover!!)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.rgb(226, 232, 240), Color.rgb(148, 163, 184), Color.rgb(203, 213, 225)),
            null, Shader.TileMode.CLAMP)
        coverCanvas!!.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
        p.color = Color.argb(45, 255, 255, 255)
        p.strokeWidth = 1f * density
        var x = -height.toFloat()
        while (x < width + height) {
            coverCanvas!!.drawLine(x, 0f, x + height, height.toFloat(), p)
            x += 18f * density
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { createCover() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 18f * density
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        val bitmap = photo
        if (bitmap != null) {
            val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val dst = RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
            photoPaint.alpha = alpha
            canvas.drawBitmap(bitmap, null, dst, photoPaint)
        } else {
            canvas.drawColor(Color.rgb(31, 41, 55))
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f * density; textAlign = Paint.Align.CENTER }
            canvas.drawText("Choose a photo to begin", width / 2f, height / 2f, textPaint)
        }
        if (!fading) {
            if (cover == null) createCover()
            cover?.let { canvas.drawBitmap(it, 0f, 0f, coverPaint) }
            if (photo != null && scratchedDistance < 1f) {
                canvas.drawText("SCRATCH HERE", width / 2f, height / 2f, coverTextPaint)
            }
        }
        canvas.restoreToCount(save)
        canvas.drawRoundRect(r, radius, radius, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (photo == null || fading) return true
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x; lastY = y
                scratchPath.moveTo(x, y)
                scratchPath.lineTo(x + 0.1f, y + 0.1f)
                coverCanvas?.drawPath(scratchPath, erasePaint)
                scratchedDistance += 1f
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                coverCanvas?.drawLine(lastX, lastY, x, y, erasePaint)
                scratchedDistance += kotlin.math.hypot((x - lastX).toDouble(), (y - lastY).toDouble()).toFloat()
                lastX = x; lastY = y
                invalidate()
                if (scratchedDistance > width * height * 0.32f / (erasePaint.strokeWidth * 0.8f)) beginFade()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (scratchedDistance > width * height * 0.32f / (erasePaint.strokeWidth * 0.8f)) beginFade()
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun beginFade() {
        if (fading) return
        fading = true
        onRevealStateChanged?.invoke(true)
        handler.postDelayed({
            val animator = ValueAnimator.ofInt(255, 0).apply {
                duration = 1100L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    alpha = it.animatedValue as Int
                    invalidate()
                }
                start()
            }
            handler.postDelayed({
                alpha = 255
                scratchedDistance = 0f
                scratchPath.reset()
                createCover()
                fading = false
                onRevealStateChanged?.invoke(false)
                invalidate()
            }, 1200L)
        }, 1400L)
    }
}
