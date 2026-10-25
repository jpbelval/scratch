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
 * clears overlapping dots from that layer, revealing the "message" underneath.
 * Once enough of the card is wiped the remaining screen fades away, then a tap
 * resets the trick and a long-press returns to the setup screen.
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

    private var lastX = 0f
    private var lastY = 0f
    private var lastProgressCheck = 0L

    private val gestureRect = Rect()
    private val gestureRects = listOf(gestureRect)

    private val density = resources.displayMetrics.density
    private val brushRadius = 32f * density
    private val stampStep = brushRadius * 0.84f

    private val tmpRect = RectF()

    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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

    private var revealAnimator: ValueAnimator? = null

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
        touching = false
        revealing = false
        invalidate()
    }

    fun resetCard() {
        if (width <= 0 || height <= 0) return
        revealAnimator?.cancel()
        revealAnimator = null

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
        lastProgressCheck = 0L
        invalidate()
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
        canvas.drawCircle(x, y, brushRadius * 1.08f, clearEdgePaint)
        canvas.drawCircle(x, y, brushRadius, clearPaint)
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

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        revealAnimator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REVEAL_FRACTION = 0.36f
    }
}
