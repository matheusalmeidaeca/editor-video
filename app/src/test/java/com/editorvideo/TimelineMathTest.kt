package com.editorvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMathTest {

    @Test
    fun effMs_aplicaVelocidade() {
        assertEquals(10_000L, TimelineMath.effMs(0, 10_000, 1f))
        assertEquals(5_000L, TimelineMath.effMs(0, 10_000, 2f))
        assertEquals(20_000L, TimelineMath.effMs(0, 10_000, 0.5f))
        assertEquals(4_000L, TimelineMath.effMs(2_000, 8_000, 1.5f))
    }

    @Test
    fun boundaries_somaAcumulada() {
        assertEquals(listOf(1_000L, 3_000L), TimelineMath.boundaries(listOf(1_000L, 2_000L, 3_000L)))
        assertTrue(TimelineMath.boundaries(listOf(5_000L)).isEmpty())
        assertTrue(TimelineMath.boundaries(emptyList()).isEmpty())
    }

    @Test
    fun timelineMs_somaClipesAnteriores() {
        val effs = listOf(1_000L, 2_000L, 3_000L)
        assertEquals(500L, TimelineMath.timelineMs(effs, 0, 500, 1f))
        assertEquals(3_000L + 1_000L, TimelineMath.timelineMs(effs, 2, 1_500, 1.5f))
        assertEquals(1_000L, TimelineMath.timelineMs(effs, 1, -50, 1f))
    }

    @Test
    fun outputSize_original_e_qualidade() {
        assertEquals(Pair(1920, 1080), TimelineMath.outputSize(1920, 1080, 0, null, null))
        assertEquals(Pair(1280, 720), TimelineMath.outputSize(1920, 1080, 0, null, 720))
    }

    @Test
    fun outputSize_rotacaoTrocaLados() {
        assertEquals(Pair(1080, 1920), TimelineMath.outputSize(1920, 1080, 90, null, null))
    }

    @Test
    fun outputSize_retrato_9_16() {
        assertEquals(Pair(1080, 1920), TimelineMath.outputSize(1920, 1080, 0, 9f / 16f, 1080))
    }

    @Test
    fun outputSize_sempreParEMinimo2() {
        val (w, h) = TimelineMath.outputSize(1000, 563, 0, null, null)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
        val (a, b) = TimelineMath.outputSize(1, 1, 0, null, null)
        assertTrue(a >= 2 && b >= 2)
    }

    @Test
    fun videoBitrate_valores_e_limites() {
        assertEquals(4_976_640.0, TimelineMath.videoBitrate(1920, 1080, 1, 0).toDouble(), 2.0)
        assertEquals(3_483_648.0, TimelineMath.videoBitrate(1920, 1080, 1, 1).toDouble(), 2.0)
        assertEquals(800_000, TimelineMath.videoBitrate(100, 100, 0, 0))
        assertEquals(40_000_000, TimelineMath.videoBitrate(7680, 4320, 2, 0))
    }

    @Test
    fun snap_pegaOPontoMaisProximoDentroDoLimite() {
        val pts = listOf(0L, 1_000L, 3_000L, 6_000L)
        assertEquals(1_000L, TimelineMath.snap(1_080L, pts, 100L))
        assertEquals(3_000L, TimelineMath.snap(2_950L, pts, 100L))
        assertEquals(null, TimelineMath.snap(2_000L, pts, 100L))
        assertEquals(0L, TimelineMath.snap(40L, pts, 100L))
        assertEquals(null, TimelineMath.snap(500L, emptyList(), 100L))
    }

    @Test
    fun snap_empateFicaComOPrimeiroMaisPerto() {
        assertEquals(1_000L, TimelineMath.snap(1_025L, listOf(1_000L, 1_050L), 100L))
    }

    @Test
    fun clipFades_mantemQuandoCabe() {
        assertEquals(Pair(500L, 700L), TimelineMath.clipFades(500, 700, 5_000L))
        assertEquals(Pair(0L, 0L), TimelineMath.clipFades(-100, 0, 5_000L))
    }

    @Test
    fun clipFades_encolheQuandoPassaDaDuracao() {
        val (fi, fo) = TimelineMath.clipFades(3_000, 3_000, 2_000L)
        assertEquals(1_000L, fi)
        assertEquals(1_000L, fo)
        val (a, b) = TimelineMath.clipFades(3_000, 1_000, 2_000L)
        assertTrue(a + b <= 2_000L)
        assertEquals(1_500L, a)
        assertEquals(500L, b)
        assertEquals(Pair(0L, 0L), TimelineMath.clipFades(500, 500, 0L))
    }
}
