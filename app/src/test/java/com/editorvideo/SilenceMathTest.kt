package com.editorvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceMathTest {

    private fun peaks(vararg trechos: Pair<Int, Float>): FloatArray {
        val l = ArrayList<Float>()
        for ((n, v) in trechos) repeat(n) { l.add(v) }
        return l.toFloatArray()
    }

    // janelas de 50 ms; silêncio mínimo 500 ms; folga 100 ms; estalo < 100 ms; peça mínima 300 ms
    private fun run(p: FloatArray) = SilenceMath.voicedRanges(p, 50L, 0.06f, 500L, 100L, 100L, 300L)

    @Test
    fun dividePorSilencioLongo() {
        val r = run(peaks(10 to 1f, 20 to 0f, 10 to 1f))
        assertEquals(listOf(Pair(0L, 600L), Pair(1400L, 2000L)), r)
    }

    @Test
    fun silencioCurtoNaoCorta() {
        val r = run(peaks(10 to 1f, 4 to 0f, 10 to 1f))
        assertEquals(1, r.size)
        assertEquals(0L, r[0].first)
        assertEquals(1200L, r[0].second)
    }

    @Test
    fun tudoMudoNaoDevolveNada() {
        assertTrue(run(peaks(40 to 0f)).isEmpty())
        assertTrue(run(FloatArray(0)).isEmpty())
    }

    @Test
    fun estaloCurtoEDescartado() {
        val r = run(peaks(1 to 1f, 20 to 0f, 10 to 1f))
        assertEquals(1, r.size)
        assertEquals(950L, r[0].first)
    }

    @Test
    fun limiteEhRelativoAoPicoDoTrecho() {
        // ruído de fundo a 0,01 e fala a 0,5: o ruído fica abaixo de 6% do pico e conta como silêncio
        val r = run(peaks(10 to 0.5f, 20 to 0.01f, 10 to 0.5f))
        assertEquals(2, r.size)
    }

    @Test
    fun pecaCurtaCresceAteOMinimo() {
        // som de 100 ms no meio de silêncio: com folga dá 300 ms, que é o mínimo
        val r = run(peaks(20 to 0f, 2 to 1f, 20 to 0f))
        assertEquals(1, r.size)
        assertTrue(r[0].second - r[0].first >= 300L)
    }
}
