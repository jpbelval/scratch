package com.example.scratchfade

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max

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
        textSize = 18f * resources.displayMetrics.density * resources.configuration.fontScale
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f * resources.displayMetrics.density * resources.configuration.fontScale
        textAlign = Paint.Align.CENTER
    }
    private val viewRect = RectF()
    private val photoDst = RectF()
    private val clipPath = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(148, 163, 184)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val handler = Handler(Looper.getMainLooper())
    private var fadeAnimator: ValueAnimator? = null
    private var fading = false
    private var alpha = 255
    private var scratchedDistance = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var onRevealStateChanged: ((Boolean) -> Unit)? = null
    private val density = resources.displayMetrics.density

    fun setOnRevealStateChanged(listener: (Boolean) -> Unit) { onRevealStateChanged = listener }

    fun loadPhoto(uri: Uri): Boolean {
        val decoded = decodePhoto(uri) ?: return false
        photo = decoded
        resetCard()
        return true
    }

    private fun decodePhoto(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longestSide = max(info.size.width, info.size.height)
                if (longestSide > MAX_PHOTO_SIZE) {
                    val scale = MAX_PHOTO_SIZE.toFloat() / longestSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            decodePhotoLegacy(uri)
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    // ImageDecoder (API 28+) applies EXIF rotation itself; older devices need it manually.
    private fun decodePhotoLegacy(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (_: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_PHOTO_SIZE) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: Exception) {
            null
        } ?: return null
        return orientPhoto(decoded, orientation)
    }

    private fun orientPhoto(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) bitmap.recycle()
            oriented
        } catch (_: Exception) {
            bitmap
        }
    }

    fun resetCard() {
        handler.removeCallbacksAndMessages(null)
        fadeAnimator?.cancel()
        fadeAnimator = null
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
        viewRect.set(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 18f * density
        val save = canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(viewRect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        val bitmap = photo
        if (bitmap != null) {
            val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            photoDst.set((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
            photoPaint.alpha = alpha
            canvas.drawBitmap(bitmap, null, photoDst, photoPaint)
        } else {
            canvas.drawColor(Color.rgb(31, 41, 55))
            canvas.drawText("Choose a photo to begin", width / 2f, height / 2f, emptyTextPaint)
        }
        if (!fading && bitmap != null) {
            if (cover == null) createCover()
            cover?.let { canvas.drawBitmap(it, 0f, 0f, coverPaint) }
            if (scratchedDistance < 1f) {
                canvas.drawText("SCRATCH HERE", width / 2f, height / 2f, coverTextPaint)
            }
        }
        canvas.restoreToCount(save)
        canvas.drawRoundRect(viewRect, radius, radius, borderPaint)
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
            if (!fading) return@postDelayed
            val animator = ValueAnimator.ofInt(255, 0).apply {
                duration = 1100L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    alpha = it.animatedValue as Int
                    invalidate()
                }
            }
            fadeAnimator = animator
            animator.start()
            handler.postDelayed({
                fadeAnimator = null
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

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        fadeAnimator?.cancel()
        fadeAnimator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_PHOTO_SIZE = 2048
    }
}
