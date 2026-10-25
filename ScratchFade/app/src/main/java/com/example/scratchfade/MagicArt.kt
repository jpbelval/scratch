package com.example.scratchfade

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Random
import kotlin.math.max

/**
 * Draws the app's built-in "screen" and "message" artwork so a fresh install has a
 * complete trick without needing any bundled photographs.
 */
object MagicArt {
    const val DEFAULT_WIDTH = 1080
    const val DEFAULT_HEIGHT = 1920

    private val scriptTypeface: Typeface by lazy {
        Typeface.create("cursive", Typeface.NORMAL)
    }

    fun defaultScreen(
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBeach(canvas, width.toFloat(), height.toFloat())
        return bitmap
    }

    fun defaultMessage(
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val random = Random(19)

        canvas.drawColor(0xFF060606.toInt())

        // Warm halo behind the wooden heart so the black screen has some depth.
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.36f, w * 0.62f,
                intArrayOf(0xFF2A170A.toInt(), 0xFF120C07.toInt(), 0xFF060606.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, halo)

        val panelSize = w * 0.6f
        val panel = RectF(
            (w - panelSize) / 2f,
            h * 0.36f - panelSize / 2f,
            (w + panelSize) / 2f,
            h * 0.36f + panelSize / 2f
        )
        drawWoodPanel(canvas, panel, panelSize * 0.075f, seed = 21)
        drawHeart(canvas, panel.centerX(), panel.centerY() - panelSize * 0.015f, panelSize * 0.6f)

        sparkles(canvas, w, h, random, panel)

        // The handwritten message.
        val baseline = h * 0.69f
        val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = scriptTypeface
            textSize = w * 0.155f
            textAlign = Paint.Align.CENTER
            shader = LinearGradient(
                0f, baseline - textSize, 0f, baseline,
                intArrayOf(0xFFFFCE83.toInt(), 0xFFFF7A18.toInt(), 0xFFD72C00.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            setShadowLayer(textSize * 0.055f, 0f, textSize * 0.028f, 0xD0000000.toInt())
        }
        canvas.drawText("I love you.", w / 2f, baseline, messagePaint)

        // A faint signature, like the printed prop in the original act.
        val signature = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("serif", Typeface.ITALIC)
            textSize = w * 0.031f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
            color = 0xFF7A6552.toInt()
        }
        canvas.drawText("Simon Pierro", w / 2f, h * 0.93f, signature)

        val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = w * 0.019f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.32f
            color = 0xFF4A4A4A.toInt()
        }
        canvas.drawText("MAGIC WIPE", w / 2f, h * 0.955f, strip)

        return bitmap
    }

    /**
     * The little wooden heart tile that follows the finger during the performance.
     */
    fun wipePad(context: android.content.Context, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = size * 0.075f
        val rect = RectF(inset, inset, size - inset, size - inset)
        drawWoodPanel(canvas, rect, size * 0.15f, seed = 7)
        drawHeart(canvas, size / 2f, size * 0.485f, size * 0.56f)

        // A soft diagonal glint across the wood.
        val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(0x00FFFFFF, 0x66FFF4D8.toInt(), 0x00FFFFFF),
                floatArrayOf(0.15f, 0.42f, 0.8f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, size * 0.15f, size * 0.15f, glint)
        return bitmap
    }

    fun drawHeart(canvas: Canvas, cx: Float, cy: Float, width: Float) {
        val path = heartPath(cx, cy, width)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB33B1400.toInt()
            style = Paint.Style.STROKE
            strokeWidth = width * 0.055f
            maskFilter = BlurMaskFilter(width * 0.03f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(heartPath(cx, cy + width * 0.018f, width), shadow)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - width * 0.08f, cy - width * 0.2f, width * 0.78f,
                intArrayOf(0xFFFFC467.toInt(), 0xFFFF7B12.toInt(), 0xFFE03900.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, fill)

        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - width * 0.17f, cy - width * 0.24f, width * 0.58f,
                intArrayOf(0x99FFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawCircle(cx - width * 0.17f, cy - width * 0.24f, width * 0.58f, highlight)
        canvas.restore()

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF3CD87.toInt()
            style = Paint.Style.STROKE
            strokeWidth = width * 0.018f
        }
        canvas.drawPath(path, rim)
    }

    /**
     * A rounded square of warm, grainy wood with a bevelled rim.
     */
    fun drawWoodPanel(canvas: Canvas, rect: RectF, corner: Float, seed: Int) {
        val random = Random(seed.toLong())
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC000000.toInt()
            maskFilter = BlurMaskFilter(rect.width() * 0.055f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(
                rect.left,
                rect.top + rect.height() * 0.018f,
                rect.right,
                rect.bottom + rect.height() * 0.032f
            ),
            corner, corner, shadow
        )

        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    0xFFA65F2C.toInt(),
                    0xFF823F17.toInt(),
                    0xFF5A2A0D.toInt(),
                    0xFF8E4A1D.toInt()
                ),
                floatArrayOf(0f, 0.38f, 0.74f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, corner, corner, base)

        canvas.save()
        val clip = Path().apply {
            addRoundRect(rect, corner, corner, Path.Direction.CW)
        }
        canvas.clipPath(clip)

        val grainCount = (rect.width() / 5.5f).toInt().coerceIn(34, 150)
        val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        for (i in 0 until grainCount) {
            val x = rect.left + rect.width() * (i / (grainCount - 1f))
            val dark = random.nextBoolean()
            grain.color = if (dark) {
                Color.argb(35 + random.nextInt(55), 38, 17, 4)
            } else {
                Color.argb(18 + random.nextInt(32), 255, 194, 126)
            }
            grain.strokeWidth = rect.width() * (0.0012f + random.nextFloat() * 0.0035f)
            val amplitude = rect.width() * (0.002f + random.nextFloat() * 0.012f)
            val phase = random.nextFloat() * Math.PI.toFloat() * 2f
            val cycles = 1.5f + random.nextFloat() * 3.5f
            val path = Path()
            var y = rect.top
            var first = true
            while (y <= rect.bottom + 1f) {
                val xx = x + kotlin.math.sin((y - rect.top) / rect.height() * Math.PI.toFloat() * cycles + phase) * amplitude
                if (first) {
                    path.moveTo(xx, y)
                    first = false
                } else {
                    path.lineTo(xx, y)
                }
                y += rect.height() / 34f
            }
            canvas.drawPath(path, grain)
        }

        val knot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(105, 34, 14, 3)
            strokeWidth = rect.width() * 0.0045f
        }
        repeat(3) {
            val cx = rect.left + rect.width() * (0.15f + random.nextFloat() * 0.7f)
            val cy = rect.top + rect.height() * (0.15f + random.nextFloat() * 0.7f)
            val rx = rect.width() * (0.035f + random.nextFloat() * 0.06f)
            val ry = rx * (0.55f + random.nextFloat() * 0.5f)
            for (ring in 0..3) {
                val scale = 1f + ring * 0.55f
                canvas.drawOval(
                    cx - rx * scale, cy - ry * scale,
                    cx + rx * scale, cy + ry * scale,
                    knot
                )
            }
        }

        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.top + rect.height() * 0.6f,
                intArrayOf(0x55FFE7BF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect, sheen)
        canvas.restore()

        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rect.width() * 0.01f
            color = 0x99FFDCA8.toInt()
        }
        canvas.drawRoundRect(
            RectF(rect.left + edge.strokeWidth / 2f, rect.top + edge.strokeWidth / 2f,
                rect.right - edge.strokeWidth / 2f, rect.bottom - edge.strokeWidth / 2f),
            corner, corner, edge
        )
        val darkEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rect.width() * 0.006f
            color = 0x88000000.toInt()
        }
        canvas.drawRoundRect(rect, corner, corner, darkEdge)
    }

    fun heartPath(cx: Float, cy: Float, width: Float): Path {
        val s = width / 2f
        return Path().apply {
            moveTo(cx, cy - 0.48f * s)
            cubicTo(cx, cy - 0.98f * s, cx - s, cy - 0.98f * s, cx - s, cy - 0.46f * s)
            cubicTo(cx - s, cy + 0.06f * s, cx, cy + 0.46f * s, cx, cy + 0.95f * s)
            cubicTo(cx, cy + 0.46f * s, cx + s, cy + 0.06f * s, cx + s, cy - 0.46f * s)
            cubicTo(cx + s, cy - 0.98f * s, cx, cy - 0.98f * s, cx, cy - 0.48f * s)
            close()
        }
    }

    private fun drawBeach(canvas: Canvas, w: Float, h: Float) {
        val random = Random(31)
        val horizon = h * 0.56f
        val shore = h * 0.78f

        val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, horizon,
                intArrayOf(
                    0xFF082A59.toInt(),
                    0xFF155B9E.toInt(),
                    0xFF62B8D8.toInt(),
                    0xFFDDF4F9.toInt()
                ),
                floatArrayOf(0f, 0.42f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, horizon, sky)

        val sunX = w * 0.26f
        val sunY = horizon * 0.46f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sunX, sunY, w * 0.3f,
                intArrayOf(0xFFFFF3B4.toInt(), 0x88FFE49A.toInt(), 0x00FFD36E),
                floatArrayOf(0f, 0.22f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sunX, sunY, w * 0.3f, glow)
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sunX, sunY, w * 0.075f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF2AE.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sunX, sunY, w * 0.075f, core)

        val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 255, 255, 255)
            maskFilter = BlurMaskFilter(w * 0.018f, BlurMaskFilter.Blur.NORMAL)
        }
        fun drawCloud(cx: Float, cy: Float, width: Float) {
            canvas.drawOval(cx - width * 0.5f, cy - width * 0.11f, cx + width * 0.5f, cy + width * 0.13f, cloud)
            canvas.drawOval(cx - width * 0.35f, cy - width * 0.2f, cx + width * 0.05f, cy + width * 0.13f, cloud)
            canvas.drawOval(cx - width * 0.02f, cy - width * 0.24f, cx + width * 0.4f, cy + width * 0.12f, cloud)
        }
        drawCloud(w * 0.7f, horizon * 0.34f, w * 0.32f)
        drawCloud(w * 0.2f, horizon * 0.63f, w * 0.24f)
        drawCloud(w * 0.88f, horizon * 0.72f, w * 0.22f)

        val sea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, horizon, 0f, shore,
                intArrayOf(
                    0xFF0A5873.toInt(),
                    0xFF12899F.toInt(),
                    0xFF51CCD4.toInt(),
                    0xFFA8E9EC.toInt()
                ),
                floatArrayOf(0f, 0.34f, 0.74f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, horizon, w, shore, sea)

        val reflectionGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sunX, horizon + (shore - horizon) * 0.42f, w * 0.2f,
                intArrayOf(0x44FFF0C0.toInt(), 0x00FFE0A0),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sunX, horizon + (shore - horizon) * 0.42f, w * 0.2f, reflectionGlow)

        val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 255, 244, 196) }
        var y = horizon + h * 0.004f
        while (y < shore) {
            val t = ((y - horizon) / (shore - horizon)).coerceIn(0f, 1f)
            val half = w * (0.008f + 0.105f * t)
            val cx = sunX + (random.nextFloat() - 0.5f) * w * (0.012f + 0.04f * t)
            val segment = half * (0.25f + random.nextFloat() * 0.75f)
            glint.alpha = (180 * (1f - t) + 18).toInt()
            val thickness = h * (0.0016f + 0.0016f * random.nextFloat())
            canvas.drawRoundRect(
                cx - segment, y, cx + segment, y + thickness,
                thickness / 2f, thickness / 2f, glint
            )
            if (random.nextFloat() < 0.35f) {
                glint.alpha = (glint.alpha * 0.7f).toInt()
                canvas.drawCircle(
                    cx + (random.nextFloat() - 0.5f) * segment * 1.6f,
                    y + thickness,
                    thickness * (0.8f + random.nextFloat()),
                    glint
                )
            }
            y += h * (0.006f + random.nextFloat() * 0.011f)
        }

        val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        repeat(15) {
            val wy = horizon + (shore - horizon) * (0.07f + random.nextFloat() * 0.9f)
            val t = ((wy - horizon) / (shore - horizon)).coerceIn(0f, 1f)
            wave.color = Color.argb((18 + 30 * (1f - t)).toInt(), 255, 255, 255)
            wave.strokeWidth = h * (0.0011f + 0.0018f * t)
            val path = Path()
            val amp = h * (0.0015f + 0.003f * t)
            var x = -w * 0.1f
            path.moveTo(x, wy)
            var up = true
            while (x < w * 1.1f) {
                val next = x + w * 0.075f
                path.quadTo(x + w * 0.0375f, if (up) wy - amp else wy + amp, next, wy)
                x = next
                up = !up
            }
            canvas.drawPath(path, wave)
        }

        val sand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, shore, 0f, h,
                intArrayOf(0xFFF7E2B8.toInt(), 0xFFECCF97.toInt(), 0xFFD2A566.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, shore, w, h, sand)

        val wet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(105, 255, 255, 255) }
        canvas.drawRect(0f, shore - h * 0.007f, w, shore + h * 0.013f, wet)

        val foam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        repeat(6) { band ->
            val fy = shore + h * (0.005f + band * 0.007f + random.nextFloat() * 0.004f)
            foam.color = Color.argb((150 - band * 20).coerceAtLeast(25), 255, 255, 255)
            foam.strokeWidth = h * (0.0026f - band * 0.0002f)
            val path = Path()
            var x = -w * 0.05f
            path.moveTo(x, fy)
            var up = random.nextBoolean()
            while (x < w * 1.05f) {
                val next = x + w * (0.07f + random.nextFloat() * 0.06f)
                path.quadTo(
                    x + (next - x) / 2f,
                    fy + if (up) -h * 0.0025f else h * 0.0025f,
                    next, fy
                )
                x = next
                up = !up
            }
            canvas.drawPath(path, foam)
        }

        val fleck = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(260) {
            val fx = random.nextFloat() * w
            val fy = shore + random.nextFloat() * (h - shore)
            fleck.color = if (random.nextBoolean()) {
                Color.argb(38, 116, 78, 38)
            } else {
                Color.argb(28, 255, 255, 255)
            }
            canvas.drawCircle(fx, fy, h * 0.0007f + random.nextFloat() * h * 0.0012f, fleck)
        }

        val gull = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(190, 255, 255, 255)
            strokeWidth = w * 0.003f
            strokeCap = Paint.Cap.ROUND
        }
        fun drawGull(gx: Float, gy: Float, size: Float) {
            val path = Path()
            path.moveTo(gx - size, gy)
            path.quadTo(gx - size * 0.45f, gy - size * 0.55f, gx, gy - size * 0.06f)
            path.quadTo(gx + size * 0.45f, gy - size * 0.55f, gx + size, gy)
            canvas.drawPath(path, gull)
        }
        drawGull(w * 0.69f, horizon * 0.22f, w * 0.035f)
        drawGull(w * 0.76f, horizon * 0.29f, w * 0.024f)

        val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.42f, max(w, h) * 0.8f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0x66000000.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, vignette)
    }

    private fun sparkles(canvas: Canvas, w: Float, h: Float, random: Random, panel: RectF) {
        repeat(26) {
            val x = random.nextFloat() * w
            val y = h * (0.05f + random.nextFloat() * 0.86f)
            if (panel.contains(x, y)) return@repeat
            val alpha = 14 + random.nextInt(34)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 255, 203, 141)
            }
            canvas.drawCircle(x, y, h * 0.0008f + random.nextFloat() * h * 0.0018f, paint)
        }
    }
}
