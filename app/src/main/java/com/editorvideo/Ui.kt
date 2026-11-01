package com.editorvideo

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Fundo vivo: manchas de luz que flutuam como líquido e bolhas que sobem devagar. */
class LiquidBackground(context: Context) : View(context) {

    private class Blob(
        val color: Int, val r: Float, val fx: Float, val fy: Float,
        val ox: Float, val oy: Float, val ph: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var radius = 1f
    }

    private class Bubble(val x: Float, val r: Float, val speed: Float, val off: Float, val wob: Float)

    private val blobs = listOf(
        Blob(0xFF1FD6C1.toInt(), 0.62f, 0.00017f, 0.00021f, 0.15f, 0.10f, 0.0f),
        Blob(0xFF6B4DFF.toInt(), 0.70f, 0.00013f, 0.00016f, 0.90f, 0.35f, 2.1f),
        Blob(0xFF1E6BFF.toInt(), 0.60f, 0.00019f, 0.00012f, 0.30f, 0.80f, 4.0f),
        Blob(0xFFFF4DA6.toInt(), 0.42f, 0.00011f, 0.00018f, 0.85f, 0.95f, 5.3f)
    )

    private val bubbles: List<Bubble>
    private val basePaint = Paint()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0DFFFFFF }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2EFFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    init {
        val rnd = Random(7)
        val d = resources.displayMetrics.density
        bubbles = List(14) {
            Bubble(
                rnd.nextFloat(),
                (4f + rnd.nextFloat() * 14f) * d,
                0.000025f + rnd.nextFloat() * 0.00005f,
                rnd.nextFloat(),
                0.01f + rnd.nextFloat() * 0.03f
            )
        }
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        basePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF050B18.toInt(), 0xFF081630.toInt(), 0xFF050B18.toInt()),
            null, Shader.TileMode.CLAMP
        )
        for (b in blobs) {
            b.radius = max(w, h) * b.r * 0.75f
            b.paint.shader = RadialGradient(
                0f, 0f, b.radius,
                intArrayOf(withAlpha(b.color, 0x4D), withAlpha(b.color, 0x00)),
                null, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, basePaint)
        val t = SystemClock.uptimeMillis().toFloat()
        for (b in blobs) {
            val cx = w * (b.ox + 0.28f * sin(t * b.fx + b.ph))
            val cy = h * (b.oy + 0.22f * cos(t * b.fy + b.ph))
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, b.radius, b.paint)
            canvas.restore()
        }
        for (bb in bubbles) {
            val p = (t * bb.speed + bb.off) % 1f
            val y = h * (1.1f - 1.2f * p)
            val x = w * bb.x + sin(t * 0.0006f + bb.off * 6.28f) * w * bb.wob
            canvas.drawCircle(x, y, bb.r, fillPaint)
            canvas.drawCircle(x, y, bb.r, strokePaint)
        }
        postInvalidateOnAnimation()
    }
}

/** Barra de progresso líquida: o final da barra ondula enquanto exporta. */
class LiquidProgress(context: Context) : View(context) {

    var progress: Int = 0
        set(v) {
            field = v.coerceIn(0, 100)
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val wave = Path()
    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fill.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            0xFF2DE2C8.toInt(), 0xFF6C9CFF.toInt(), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, track)
        val fw = w * progress / 100f
        if (fw > 0f) {
            clip.reset()
            clip.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            wave.reset()
            val ph = SystemClock.uptimeMillis() * 0.006f
            val amp = h * 0.35f
            wave.moveTo(0f, 0f)
            var y = 0f
            while (y <= h) {
                wave.lineTo(fw + amp * sin(y * 0.9f + ph), y)
                y += 1f
            }
            wave.lineTo(0f, h)
            wave.close()
            canvas.drawPath(wave, fill)
            canvas.restore()
        }
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }
}
