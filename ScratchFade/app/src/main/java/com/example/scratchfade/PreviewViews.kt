package com.example.scratchfade

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * The live poster on the setup screen. It shows the chosen screen image with a
 * trail of dots already wiped out of it, revealing a slice of the message.
 */
class HeroPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var screen: Bitmap? = null
    private var message: Bitmap? = null
    private var composed: Bitmap? = null

    private val density = resources.displayMetrics.density
    private val tmpRect = RectF()
    private val clipPath = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 226, 232, 240)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    fun setContent(screenBitmap: Bitmap?, messageBitmap: Bitmap?) {
        screen = screenBitmap
        message = messageBitmap
        rebuild()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        if (width <= 0 || height <= 0) return
        val old = composed
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composed = bitmap
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF0B0B0B.toInt())

        message?.let { drawCenterCrop(canvas, it) }

        screen?.let { screenBitmap ->
            val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val layerCanvas = Canvas(layer)
            layerCanvas.drawColor(Color.BLACK)
            drawCenterCrop(layerCanvas, screenBitmap)

            val dotRadius = min(width, height) * 0.09f
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                style = Paint.Style.FILL
            }
            val clearEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                style = Paint.Style.FILL
                alpha = 100
                maskFilter = android.graphics.BlurMaskFilter(2.5f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            for (i in 0..7) {
                val t = i / 7f
                val x = width * (0.14f + 0.62f * t)
                val y = height * (0.80f - 0.46f * t)
                layerCanvas.drawCircle(x, y, dotRadius * 1.08f, clearEdge)
                layerCanvas.drawCircle(x, y, dotRadius, clear)
            }
            canvas.drawBitmap(layer, 0f, 0f, null)
            layer.recycle()
        }

        old?.recycle()
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap) {
        val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        tmpRect.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, tmpRect, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 7f * density
        val save = canvas.save()
        clipPath.rewind()
        clipPath.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        composed?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.restoreToCount(save)
        canvas.drawRoundRect(
            borderPaint.strokeWidth / 2f,
            borderPaint.strokeWidth / 2f,
            width - borderPaint.strokeWidth / 2f,
            height - borderPaint.strokeWidth / 2f,
            radius,
            radius,
            borderPaint
        )
    }
}

/**
 * A rounded, centre-cropped thumbnail used for the two setup previews.
 */
class PreviewImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF161616.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 203, 213, 225)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val tmpRect = RectF()
    private val clipPath = Path()

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 5f * density
        val save = canvas.save()
        clipPath.rewind()
        clipPath.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val bmp = bitmap
        if (bmp != null) {
            val scale = max(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            val drawWidth = bmp.width * scale
            val drawHeight = bmp.height * scale
            val left = (width - drawWidth) / 2f
            val top = (height - drawHeight) / 2f
            tmpRect.set(left, top, left + drawWidth, top + drawHeight)
            canvas.drawBitmap(bmp, null, tmpRect, bitmapPaint)
        }
        canvas.restoreToCount(save)

        canvas.drawRoundRect(
            borderPaint.strokeWidth / 2f,
            borderPaint.strokeWidth / 2f,
            width - borderPaint.strokeWidth / 2f,
            height - borderPaint.strokeWidth / 2f,
            radius,
            radius,
            borderPaint
        )
    }
}
