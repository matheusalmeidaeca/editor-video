package com.editorvideo

import kotlin.math.min
import kotlin.math.roundToInt

/** Matemática pura da linha do tempo e da exportação. Sem Android, testável na JVM. */
object TimelineMath {

    /** Duração do clipe na linha do tempo, já com a velocidade aplicada. */
    fun effMs(startMs: Long, endMs: Long, speed: Float): Long =
        ((endMs - startMs) / speed).toLong()

    /** Instantes de corte entre clipes (soma acumulada, sem o fim do último). */
    fun boundaries(effs: List<Long>): List<Long> {
        val out = ArrayList<Long>()
        var cum = 0L
        for (k in 0 until effs.size - 1) {
            cum += effs[k]
            out.add(cum)
        }
        return out
    }

    /** Posição na linha do tempo a partir do clipe atual e da posição dentro dele. */
    fun timelineMs(effs: List<Long>, index: Int, positionMs: Long, speed: Float): Long {
        var cum = 0L
        for (k in 0 until index) cum += effs[k]
        return cum + (positionMs.coerceAtLeast(0L) / speed).toLong()
    }

    /** Tamanho de saída (largura, altura), sempre par. [ratio] e [shortSide] nulos = usar o original. */
    fun outputSize(
        w0: Int,
        h0: Int,
        rotation: Int,
        ratio: Float?,
        shortSide: Int?
    ): Pair<Int, Int> {
        var w = w0
        var h = h0
        if (rotation == 90 || rotation == 270) {
            val t = w
            w = h
            h = t
        }
        val r = ratio ?: (w.toFloat() / h)
        val short = shortSide ?: min(w, h)
        val ow: Int
        val oh: Int
        if (r >= 1f) {
            oh = short
            ow = (short * r).roundToInt()
        } else {
            ow = short
            oh = (short / r).roundToInt()
        }
        return Pair((ow - ow % 2).coerceAtLeast(2), (oh - oh % 2).coerceAtLeast(2))
    }

    private val bitsPerPixel = listOf(0.05f, 0.08f, 0.13f)

    /** Taxa de bits do vídeo. [bitrateIdx] 0..2 (baixa, média, alta); [codecIdx] 1 = HEVC. */
    fun videoBitrate(w: Int, h: Int, bitrateIdx: Int, codecIdx: Int): Int {
        val bpp = bitsPerPixel[bitrateIdx]
        val f = if (codecIdx == 1) 0.7f else 1f
        return (w * h * 30f * bpp * f).toInt().coerceIn(800_000, 40_000_000)
    }
}
