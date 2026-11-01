package com.editorvideo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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


/** Linha do tempo contínua: a agulha fica no centro e a trilha desliza por baixo (estilo KineMaster). */
class TimelineView(context: Context) : View(context) {

    class ClipInfo(
        val effMs: Long,
        val thumb: Bitmap?,
        val label: String,
        val wave: WaveData? = null,
        val srcStartMs: Long = 0L,
        val speed: Float = 1f
    )
    class Bar(val startMs: Long, val endMs: Long, val label: String, val color: Int)

    var clips: List<ClipInfo> = emptyList()
    var bars: List<Bar> = emptyList()
    var selected = -1
    var musicMs = 0L
    var voiceMs = 0L
    var musicWave: WaveData? = null
    var voiceWave: WaveData? = null
    var transOn = false
    var posMs = 0L
        set(v) {
            field = v
            invalidate()
        }

    var onSeek: (Long) -> Unit = {}
    var onDrag: (Boolean) -> Unit = {}
    var onSelect: (Int) -> Unit = {}

    private val d = resources.displayMetrics.density
    private var pxPerSec = 40f * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 10f * d
    }
    private val rect = RectF()
    private val dst = RectF()
    private val clipPath = Path()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downPos = 0L
    private var moved = false
    private var snappedTo = -1L

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pxPerSec = (pxPerSec * detector.scaleFactor).coerceIn(8f * d, 220f * d)
                invalidate()
                return true
            }
        }
    )

    fun total(): Long = clips.sumOf { it.effMs }

    private fun fmtRuler(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val r = s % 60
        return if (ms % 1000L != 0L) "%d:%02d.5".format(m, r) else "%d:%02d".format(m, r)
    }

    /**
     * Barras da forma de onda entre [x0] e [x1] (só as colunas visíveis). [srcStart] e [speed] ligam o tempo
     * do clipe na linha do tempo ao tempo do arquivo. Com [centered], a barra cresce para os dois lados de [base].
     */
    private fun drawWave(
        canvas: Canvas, wd: WaveData, x0: Float, x1: Float, base: Float, maxH: Float,
        centered: Boolean, ppm: Float, srcStart: Long, speed: Float, color: Int, viewW: Float
    ) {
        val step = 2f * d
        val barW = 1.4f * d
        var x = max(x0, 0f)
        val limit = min(x1, viewW)
        paint.style = Paint.Style.FILL
        paint.color = color
        while (x < limit) {
            val tA = (x - x0) / ppm
            val tB = tA + step / ppm
            val pk = WaveMath.maxIn(wd.peaks, wd.bucketMs, srcStart + tA * speed.toDouble(), srcStart + tB * speed.toDouble())
            val hh = max(0.8f * d, WaveMath.level(pk, wd.norm) * maxH)
            val xr = min(x + barW, limit)
            if (centered) canvas.drawRect(x, base - hh, xr, base + hh, paint)
            else canvas.drawRect(x, base - hh, xr, base, paint)
            x += step
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val ppm = pxPerSec / 1000f
        val cx = w / 2f

        paint.style = Paint.Style.FILL
        paint.color = 0x14FFFFFF
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 16f * d, 16f * d, paint)

        val rulerH = h * 0.13f
        val vTop = rulerH + 2f * d
        val vBot = vTop + h * 0.42f
        val barTop = vBot + 3f * d
        val laneH = h * 0.09f
        val audioTop = barTop + laneH * 2f + 5f * d
        val audioH = h * 0.065f

        // régua
        val steps = longArrayOf(500L, 1000L, 2000L, 5000L, 10000L, 30000L, 60000L)
        var step = steps[steps.size - 1]
        for (s in steps) {
            if (s * ppm >= 56f * d) {
                step = s
                break
            }
        }
        val firstMs = max(0L, (floor(((posMs - cx / ppm) / step).toDouble()) * step).toLong())
        val lastMs = posMs + (cx / ppm).toLong() + step
        paint.color = 0x66FFFFFF
        paint.strokeWidth = 1f * d
        txt.color = 0xFF8FA3BF.toInt()
        var m = firstMs
        while (m <= lastMs) {
            val x = cx + (m - posMs) * ppm
            canvas.drawLine(x, rulerH - 5f * d, x, rulerH, paint)
            canvas.drawText(fmtRuler(m), x + 3f * d, rulerH - 6f * d, txt)
            m += step
        }
        txt.color = 0xFFFFFFFF.toInt()

        // clipes de vídeo
        var start = 0L
        for ((i, ci) in clips.withIndex()) {
            val x0 = cx + (start - posMs) * ppm
            val x1 = cx + (start + ci.effMs - posMs) * ppm
            start += ci.effMs
            if (x1 >= 0f && x0 <= w) {
                rect.set(x0 + 1f, vTop, x1 - 1f, vBot)
                clipPath.reset()
                clipPath.addRoundRect(rect, 8f * d, 8f * d, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)
                paint.style = Paint.Style.FILL
                paint.color = 0xFF162844.toInt()
                canvas.drawRect(rect, paint)
                val th = ci.thumb
                if (th != null) {
                    val tileW = (vBot - vTop) * th.width / th.height
                    var tx = x0
                    if (tx < 0f) tx += floor((-tx / tileW).toDouble()).toFloat() * tileW
                    val limit = min(x1, w)
                    while (tx < limit) {
                        dst.set(tx, vTop, tx + tileW, vBot)
                        canvas.drawBitmap(th, null, dst, bmpPaint)
                        tx += tileW
                    }
                }
                val wd = ci.wave
                if (wd != null) {
                    val base = vBot - 14f * d
                    val maxH = min((vBot - vTop - 14f * d) * 0.7f, 30f * d)
                    paint.color = 0x66000000
                    canvas.drawRect(rect.left, base - maxH - 2f * d, rect.right, base, paint)
                    drawWave(canvas, wd, x0, x1, base, maxH, false, ppm, ci.srcStartMs, ci.speed, 0xDD2DE2C8.toInt(), w)
                }
                paint.color = 0x88000000.toInt()
                canvas.drawRect(rect.left, vBot - 14f * d, rect.right, vBot, paint)
                canvas.drawText(ci.label, max(x0, 0f) + 6f * d, vBot - 4f * d, txt)
                canvas.restore()
                if (i == selected) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.5f * d
                    paint.color = 0xFF2DE2C8.toInt()
                    canvas.drawRoundRect(rect, 8f * d, 8f * d, paint)
                    paint.style = Paint.Style.FILL
                }
            }
            if (transOn && i < clips.size - 1 && x1 >= -20f && x1 <= w + 20f) {
                val cy = (vTop + vBot) / 2f
                paint.style = Paint.Style.FILL
                paint.color = 0xF0FFFFFF.toInt()
                canvas.drawCircle(x1, cy, 9f * d, paint)
                paint.color = 0xFF2DE2C8.toInt()
                canvas.drawCircle(x1, cy, 4f * d, paint)
            }
        }

        // textos e adesivos (duas faixas)
        val laneEnd = longArrayOf(-1L, -1L)
        for (b in bars.sortedBy { it.startMs }) {
            val lane = if (laneEnd[0] <= b.startMs) 0 else 1
            laneEnd[lane] = b.endMs
            val x0 = cx + (b.startMs - posMs) * ppm
            val x1 = max(cx + (b.endMs - posMs) * ppm, x0 + 8f * d)
            if (x1 < 0f || x0 > w) continue
            val y = barTop + lane * (laneH + 2f * d)
            rect.set(x0, y, x1, y + laneH)
            paint.style = Paint.Style.FILL
            paint.color = b.color
            canvas.drawRoundRect(rect, 5f * d, 5f * d, paint)
            canvas.save()
            canvas.clipRect(rect)
            txt.textSize = 9f * d
            canvas.drawText(b.label, max(x0, 0f) + 4f * d, y + laneH - 3f * d, txt)
            txt.textSize = 10f * d
            canvas.restore()
        }

        // música e narração
        if (musicMs > 0L) {
            rect.set(cx - posMs * ppm, audioTop, cx + (musicMs - posMs) * ppm, audioTop + audioH)
            paint.style = Paint.Style.FILL
            paint.color = 0xCC2BB673.toInt()
            canvas.drawRoundRect(rect, 4f * d, 4f * d, paint)
            musicWave?.let {
                drawWave(canvas, it, rect.left, rect.right, (rect.top + rect.bottom) / 2f, (rect.bottom - rect.top) / 2f, true, ppm, 0L, 1f, 0xE6FFFFFF.toInt(), w)
            }
        }
        if (voiceMs > 0L) {
            val y = audioTop + audioH + 2f * d
            rect.set(cx - posMs * ppm, y, cx + (voiceMs - posMs) * ppm, y + audioH)
            paint.style = Paint.Style.FILL
            paint.color = 0xCCFF5C7A.toInt()
            canvas.drawRoundRect(rect, 4f * d, 4f * d, paint)
            voiceWave?.let {
                drawWave(canvas, it, rect.left, rect.right, (rect.top + rect.bottom) / 2f, (rect.bottom - rect.top) / 2f, true, ppm, 0L, 1f, 0xE6FFFFFF.toInt(), w)
            }
        }

        // agulha
        paint.style = Paint.Style.FILL
        paint.color = 0x552DE2C8
        canvas.drawRect(cx - 3f * d, 0f, cx + 3f * d, h, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(cx - 0.9f * d, 0f, cx + 0.9f * d, h, paint)
        canvas.drawCircle(cx, 3f * d, 4f * d, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (scaler.isInProgress) {
            moved = true
            return true
        }
        val ppm = pxPerSec / 1000f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x
                downPos = posMs
                moved = false
                snappedTo = -1L
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                if (!moved && abs(dx) > slop) {
                    moved = true
                    onDrag(true)
                }
                if (moved && e.pointerCount == 1) {
                    var t = (downPos - dx / ppm).toLong().coerceIn(0L, total())
                    // ímã: gruda nos cortes entre clipes (e nas pontas) a até 8 dp, com um toque de vibração
                    val pontos = ArrayList<Long>()
                    pontos.add(0L)
                    pontos.addAll(TimelineMath.boundaries(clips.map { it.effMs }))
                    pontos.add(total())
                    val alvo = TimelineMath.snap(t, pontos, (8f * d / ppm).toLong())
                    if (alvo != null) {
                        t = alvo
                        if (snappedTo != alvo) {
                            snappedTo = alvo
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    } else {
                        snappedTo = -1L
                    }
                    posMs = t
                    onSeek(t)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val t = posMs + ((e.x - width / 2f) / ppm).toLong()
                    var acc = 0L
                    var hit = false
                    for ((i, ci) in clips.withIndex()) {
                        if (t >= acc && t < acc + ci.effMs) {
                            onSelect(i)
                            hit = true
                            break
                        }
                        acc += ci.effMs
                    }
                    if (!hit) onSelect(-1) // toque fora dos clipes: solta a seleção
                } else {
                    onDrag(false)
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (moved) onDrag(false)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
