package com.editorvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Extras13Test {

    // ---- SrtWriter

    @Test
    fun srt_tempo_formatado() {
        assertEquals("00:00:00,000", SrtWriter.time(0L))
        assertEquals("00:01:02,250", SrtWriter.time(62_250L))
        assertEquals("01:00:00,005", SrtWriter.time(3_600_005L))
        assertEquals("00:00:00,000", SrtWriter.time(-50L))
    }

    @Test
    fun srt_escreve_e_le_de_volta() {
        val cues = listOf(SrtCue(1_000L, 3_500L, "Olá, mundo"), SrtCue(62_250L, 64_000L, "Segunda\nlinha"))
        val txt = SrtWriter.write(cues)
        val back = SrtParser.parse(txt)
        assertEquals(2, back.size)
        assertEquals(1_000L, back[0].startMs)
        assertEquals(3_500L, back[0].endMs)
        assertEquals("Olá, mundo", back[0].text)
        assertEquals(62_250L, back[1].startMs)
        assertEquals("Segunda\nlinha", back[1].text)
    }

    @Test
    fun srt_lista_vazia_gera_texto_vazio() {
        assertEquals("", SrtWriter.write(emptyList()))
    }

    // ---- TextShift

    @Test
    fun deslocar_mantem_a_duracao() {
        val r = TextShift.shift(2_000L, 5_000L, 1_500L)
        assertEquals(3_500L, r.first)
        assertEquals(6_500L, r.second)
    }

    @Test
    fun deslocar_para_tras_nao_fica_negativo() {
        val r = TextShift.shift(1_000L, 4_000L, -3_000L)
        assertEquals(0L, r.first)
        assertEquals(3_000L, r.second)
    }

    // ---- SpeedRamp

    @Test
    fun rampas_tem_nomes_e_padroes_alinhados() {
        assertEquals(SpeedRamp.names.size, SpeedRamp.patterns.size)
        for (p in SpeedRamp.patterns) assertTrue(p.size >= 2)
    }

    @Test
    fun rampa_cobre_o_clipe_todo() {
        val p = SpeedRamp.parts(9_000L, SpeedRamp.patterns[0])
        assertEquals(3, p.size)
        assertEquals(0L, p.first().first)
        assertEquals(9_000L, p.last().second)
    }

    @Test
    fun rampa_recusa_clipe_curto() {
        assertTrue(SpeedRamp.parts(1_000L, SpeedRamp.patterns[3]).isEmpty())
    }

    // ---- StyleMath e BlurMath

    @Test
    fun estilo_inativo_so_sem_vinheta_e_sem_barras() {
        assertFalse(StyleMath.active(0, 0))
        assertTrue(StyleMath.active(1, 0))
        assertTrue(StyleMath.active(0, 2))
    }

    @Test
    fun barras_tem_altura_proporcional() {
        assertEquals(0, StyleMath.barHeight(1080, 0))
        assertEquals(86, StyleMath.barHeight(1080, 1))
        assertEquals(151, StyleMath.barHeight(1080, 2))
        assertEquals(151, StyleMath.barHeight(1080, 9))
    }

    @Test
    fun listas_de_estilo_tem_o_mesmo_tamanho() {
        assertEquals(StyleMath.vignetteNames.size, StyleMath.vignetteAlpha.size)
        assertEquals(StyleMath.barsNames.size, StyleMath.barsFrac.size)
        assertEquals(BlurMath.names.size, BlurMath.sigma.size)
        assertEquals(0f, BlurMath.sigma[0], 0f)
    }

    // ---- ProjectState: campos novos da v3.3.13

    @Test
    fun estado_novo_guarda_vinheta_barras_e_duck() {
        val s = ProjectState()
        assertEquals(0, s.vignette)
        assertEquals(0, s.bars)
        assertFalse(s.duck)
        s.vignette = 2
        s.bars = 1
        s.duck = true
        val snap = s.snapshot()
        s.reset()
        assertEquals(0, s.vignette)
        assertFalse(s.duck)
        s.restore(snap)
        assertEquals(2, s.vignette)
        assertEquals(1, s.bars)
        assertTrue(s.duck)
    }
}
