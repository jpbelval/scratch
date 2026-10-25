package com.example.scratchfade

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Full-screen performance surface.
 *
 * The chosen "screen" image is painted onto a scratch layer. Dragging a finger
 * clears overlapping wooden hearts from that layer, revealing the "message"
 * underneath. Once enough of the card is wiped the remaining screen fades away,
 * then a tap resets the trick and a long-press returns to the setup screen.
 */
class MagicWipeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onRevealed: (() -> Unit)? = null
    var onExitRequested: (() -> Unit)? = null

    private var screen: Bitmap? = null
    private var message: Bitmap? = null
    private var layer: Bitmap? = null
    private var layerCanvas: Canvas? = null
    private var hasCard = false

    internal val isRevealed: Boolean get() = revealed

    private var layerAlpha = 255
    private var revealing = false
    private var revealed = false
    private var touching = false
    private var padAlpha = 0f
    private var hintAlpha = 255f

    private var lastX = 0f
    private var lastY = 0f
    private var lastProgressCheck = 0L

    private val gestureRect = Rect()
    private val gestureRects = listOf(gestureRect)

    private val density = resources.displayMetrics.density
    private val brushSize = 64f * density
    private val stampStep = brushSize * 0.42f
    private val heartPath = MagicArt.heartPath(0f, 0f, brushSize)
    private val heartEdgePath = MagicArt.heartPath(0f, 0f, brushSize * 1.10f)
    private val padBitmap = MagicArt.wipePad(context, (66f * density).toInt())

    private val tmpRect = RectF()

    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }
    private val clearEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
        alpha = 110
        maskFilter = BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        letterSpacing = 0.18f
        setShadowLayer(3f * density, 0f, 1f * density, Color.argb(200, 0, 0, 0))
    }
    private val hintLabel = context.getString(R.string.wipe_to_reveal)
    private val hintBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val hintBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private var revealAnimator: ValueAnimator? = null
    private var padAnimator: ValueAnimator? = null
    private var hintAnimator: ValueAnimator? = null

    private val hintFadeRunnable = Runnable { fadeHint(420L) }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            resetCard()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onExitRequested?.invoke()
        }
    })

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.performance_description)
    }

    fun setCards(screenBitmap: Bitmap?, messageBitmap: Bitmap?) {
        screen = screenBitmap
        message = messageBitmap
        hasCard = screenBitmap != null && messageBitmap != null
        if (width > 0 && height > 0) resetCard()
        invalidate()
    }

    fun prepare() {
        resetCard()
    }

    fun stopPerformance() {
        revealAnimator?.cancel()
        revealAnimator = null
        padAnimator?.cancel()
        padAnimator = null
        hintAnimator?.cancel()
        hintAnimator = null
        removeCallbacks(hintFadeRunnable)
        touching = false
        revealing = false
        padAlpha = 0f
        invalidate()
    }

    fun resetCard() {
        if (width <= 0 || height <= 0) return
        revealAnimator?.cancel()
        revealAnimator = null
        padAnimator?.cancel()
        padAnimator = null
        hintAnimator?.cancel()
        hintAnimator = null
        removeCallbacks(hintFadeRunnable)

        if (layer == null || layer!!.width != width || layer!!.height != height) {
            createLayer()
        }
        val canvas = layerCanvas ?: return
        canvas.drawColor(Color.BLACK)
        screen?.let { drawCenterCrop(canvas, it, fillPaint) }

        layerAlpha = 255
        revealing = false
        revealed = false
        touching = false
        padAlpha = 0f
        hintAlpha = 255f
        lastProgressCheck = 0L
        invalidate()

        if (hasCard) postDelayed(hintFadeRunnable, 3200L)
    }

    private fun createLayer() {
        val old = layer
        layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        layerCanvas = Canvas(layer!!)
        old?.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            createLayer()
            resetCard()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width > 0 && height > 0) {
            // Keep edge wipes from being stolen by the system back/home gestures.
            gestureRect.set(0, 0, width, height)
            systemGestureExclusionRects = gestureRects
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val msg = message
        if (msg != null) {
            drawCenterCrop(canvas, msg, messagePaint)
        } else {
            canvas.drawColor(Color.BLACK)
        }

        val cardLayer = layer
        if (hasCard && cardLayer != null && layerAlpha > 0) {
            screenPaint.alpha = layerAlpha.coerceIn(0, 255)
            canvas.drawBitmap(cardLayer, 0f, 0f, screenPaint)
        }

        if (!revealed && !revealing && hintAlpha > 0.5f) {
            drawHint(canvas)
        }
        if (padAlpha > 0.5f) {
            drawPad(canvas)
        }
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, paint: Paint) {
        val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        tmpRect.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, tmpRect, paint)
    }

    private fun drawHint(canvas: Canvas) {
        val text = hintLabel
        hintTextPaint.alpha = hintAlpha.toInt().coerceIn(0, 255)
        hintBorderPaint.alpha = (hintAlpha * 0.22f).toInt().coerceIn(0, 255)
        hintBgPaint.alpha = (hintAlpha * 0.38f).toInt().coerceIn(0, 255)

        val textWidth = hintTextPaint.measureText(text)
        val halfHeight = 15f * density
        val centerY = height * 0.84f
        val rect = RectF(
            width / 2f - textWidth / 2f - 22f * density,
            centerY - halfHeight,
            width / 2f + textWidth / 2f + 22f * density,
            centerY + halfHeight
        )
        canvas.drawRoundRect(rect, halfHeight, halfHeight, hintBgPaint)
        canvas.drawRoundRect(rect, halfHeight, halfHeight, hintBorderPaint)

        val metrics = hintTextPaint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, width / 2f, baseline, hintTextPaint)
    }

    private fun drawPad(canvas: Canvas) {
        padPaint.alpha = padAlpha.toInt().coerceIn(0, 255)
        val size = padBitmap.width
        tmpRect.set(
            lastX - size / 2f,
            lastY - size / 2f,
            lastX + size / 2f,
            lastY + size / 2f
        )
        val save = canvas.save()
        canvas.rotate(-8f, lastX, lastY)
        canvas.drawBitmap(padBitmap, null, tmpRect, padPaint)
        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hasCard) return false
        if (revealed) {
            gestures.onTouchEvent(event)
            return true
        }
        if (revealing) return true

        val x = event.x.coerceIn(0f, width.toFloat() - 1f)
        val y = event.y.coerceIn(0f, height.toFloat() - 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touching = true
                lastX = x
                lastY = y
                padAnimator?.cancel()
                padAlpha = 232f
                hideHintNow()
                stampAt(x, y)
                checkProgress(force = true)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!touching) return true
                val dx = x - lastX
                val dy = y - lastY
                val distance = hypot(dx, dy)
                if (distance > 0.5f) {
                    val steps = ceil(distance / stampStep).toInt().coerceAtLeast(1)
                    for (i in 1..steps) {
                        val t = i / steps.toFloat()
                        stampAt(lastX + dx * t, lastY + dy * t)
                    }
                    lastX = x
                    lastY = y
                    checkProgress()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touching) {
                    stampAt(x, y)
                    touching = false
                    fadePad()
                    checkProgress(force = true)
                }
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun stampAt(x: Float, y: Float) {
        val canvas = layerCanvas ?: return
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(-8f)
        canvas.drawPath(heartEdgePath, clearEdgePaint)
        canvas.drawPath(heartPath, clearPaint)
        canvas.restore()
    }

    private fun checkProgress(force: Boolean = false) {
        if (revealing || revealed) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastProgressCheck < 110L) return
        lastProgressCheck = now
        if (coverage() >= REVEAL_FRACTION) beginReveal()
    }

    private fun coverage(): Float {
        val bitmap = layer ?: return 0f
        val columns = 22
        val rows = 40
        var cleared = 0
        var total = 0
        for (row in 0 until rows) {
            val y = (((row + 0.5f) / rows) * height).toInt().coerceIn(0, height - 1)
            for (column in 0 until columns) {
                val x = (((column + 0.5f) / columns) * width).toInt().coerceIn(0, width - 1)
                total++
                if (Color.alpha(bitmap.getPixel(x, y)) < 50) cleared++
            }
        }
        return if (total == 0) 0f else cleared.toFloat() / total
    }

    private fun beginReveal() {
        if (revealing || revealed) return
        revealing = true
        touching = false
        fadePad()
        removeCallbacks(hintFadeRunnable)
        hintAnimator?.cancel()
        hintAlpha = 0f
        invalidate()

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 680L
            startDelay = 140L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                layerAlpha = ((it.animatedValue as Float) * 255f).toInt().coerceIn(0, 255)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled || revealAnimator !== animation) return
                    layerAlpha = 0
                    revealing = false
                    revealed = true
                    revealAnimator = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    onRevealed?.invoke()
                    invalidate()
                }
            })
        }
        revealAnimator = animator
        animator.start()
    }

    private fun hideHintNow() {
        removeCallbacks(hintFadeRunnable)
        hintAnimator?.cancel()
        hintAnimator = null
        hintAlpha = 0f
        invalidate()
    }

    private fun fadeHint(duration: Long) {
        if (hintAlpha <= 0f) return
        hintAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(hintAlpha, 0f).apply {
            this.duration = duration
            addUpdateListener {
                hintAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        hintAnimator = animator
        animator.start()
    }

    private fun fadePad() {
        padAnimator?.cancel()
        if (padAlpha <= 0f) return
        val animator = ValueAnimator.ofFloat(padAlpha, 0f).apply {
            duration = 260L
            addUpdateListener {
                padAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        padAnimator = animator
        animator.start()
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        revealAnimator = null
        padAnimator?.cancel()
        padAnimator = null
        hintAnimator?.cancel()
        hintAnimator = null
        removeCallbacks(hintFadeRunnable)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REVEAL_FRACTION = 0.36f
    }
}
