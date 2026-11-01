package com.editorvideo

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveMathTest {

    // janelas de 50 ms: [0,0.2,0.9,0.1]
    private val p = floatArrayOf(0f, 0.2f, 0.9f, 0.1f)

    @Test
    fun maiorPicoDentroDoIntervalo() {
        assertEquals(0.9f, WaveMath.maxIn(p, 50L, 0.0, 200.0), 0.0001f)
        assertEquals(0.2f, WaveMath.maxIn(p, 50L, 50.0, 99.0), 0.0001f)
    }

    @Test
    fun foraDoArquivoDevolveZero() {
        assertEquals(0f, WaveMath.maxIn(p, 50L, 1000.0, 1200.0), 0f)
        assertEquals(0f, WaveMath.maxIn(p, 50L, -300.0, -100.0), 0f)
        assertEquals(0f, WaveMath.maxIn(FloatArray(0), 50L, 0.0, 100.0), 0f)
    }

    @Test
    fun intervaloQueCruzaOInicioUsaOQueExiste() {
        assertEquals(0.2f, WaveMath.maxIn(p, 50L, -100.0, 60.0), 0.0001f)
    }

    @Test
    fun alturaRelativaRealcaSonsBaixos() {
        assertEquals(1f, WaveMath.level(0.9f, 0.9f), 0.0001f)
        assertEquals(0.5f, WaveMath.level(0.225f, 0.9f), 0.0001f)
        assertEquals(1f, WaveMath.level(2f, 0.9f), 0.0001f)
        assertEquals(0f, WaveMath.level(0f, 0.9f), 0f)
    }

    @Test
    fun janelaMaiorParaAudioLongo() {
        assertEquals(50L, WaveMath.bucketFor(10L * 60_000L))
        assertEquals(200L, WaveMath.bucketFor(31L * 60_000L))
    }

    @Test
    fun volumeSobeVozBaixa() {
        assertEquals(180, WaveMath.volumeFor(0.5f))
    }

    @Test
    fun volumeDesceAudioAlto() {
        assertEquals(90, WaveMath.volumeFor(1.0f))
    }

    @Test
    fun volumeRespeitaLimites() {
        assertEquals(200, WaveMath.volumeFor(0.2f))
        assertEquals(20, WaveMath.volumeFor(10f))
    }

    @Test
    fun trechoMudoNaoNormaliza() {
        assertEquals(null, WaveMath.volumeFor(0.005f))
    }
}
