package com.editorvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasTest {

    // ---- SrtParser

    @Test
    fun srt_le_dois_blocos() {
        val srt = "1\n00:00:01,000 --> 00:00:03,500\nOlá, mundo\n\n2\n00:01:02,250 --> 00:01:04,000\nSegunda\nlinha\n"
        val c = SrtParser.parse(srt)
        assertEquals(2, c.size)
        assertEquals(1_000L, c[0].startMs)
        assertEquals(3_500L, c[0].endMs)
        assertEquals("Olá, mundo", c[0].text)
        assertEquals(62_250L, c[1].startMs)
        assertEquals("Segunda\nlinha", c[1].text)
    }

    @Test
    fun srt_aceita_windows_bom_e_tags() {
        val srt = "\uFEFF1\r\n00:00:00,500 --> 00:00:02,000\r\n<i>Oi</i> {\\an8}você\r\n\r\n"
        val c = SrtParser.parse(srt)
        assertEquals(1, c.size)
        assertEquals("Oi você", c[0].text)
    }

    @Test
    fun srt_ignora_bloco_quebrado_e_tempo_invertido() {
        val srt = "1\nsem tempo\ntexto\n\n2\n00:00:05,000 --> 00:00:04,000\nvolta\n\n3\n00:00:06,000 --> 00:00:07,000\nvale\n"
        val c = SrtParser.parse(srt)
        assertEquals(1, c.size)
        assertEquals("vale", c[0].text)
    }

    @Test
    fun srt_vazio_nao_gera_nada() {
        assertTrue(SrtParser.parse("").isEmpty())
        assertNull(SrtParser.parseTime("abc"))
    }

    @Test
    fun srt_horas_e_milissegundos_curtos() {
        assertEquals(3_600_000L + 5_000L + 500L, SrtParser.parseTime("01:00:05,5"))
    }

    // ---- TextAnim

    @Test
    fun anim_fora_do_tempo_e_invisivel() {
        assertEquals(0f, TextAnim.frame(1, 500L, 1_000L, 3_000L)[0], 0.0001f)
        assertEquals(0f, TextAnim.frame(2, 3_500L, 1_000L, 3_000L)[0], 0.0001f)
    }

    @Test
    fun anim_fade_entra_e_sai() {
        assertEquals(0f, TextAnim.frame(1, 1_000L, 1_000L, 3_000L)[0], 0.0001f)
        assertEquals(1f, TextAnim.frame(1, 2_000L, 1_000L, 3_000L)[0], 0.0001f)
        assertEquals(0f, TextAnim.frame(1, 3_000L, 1_000L, 3_000L)[0], 0.0001f)
    }

    @Test
    fun anim_pop_cresce_ate_o_tamanho_normal() {
        val a = TextAnim.frame(2, 1_000L, 1_000L, 3_000L)
        assertEquals(0.6f, a[1], 0.0001f)
        val b = TextAnim.frame(2, 2_000L, 1_000L, 3_000L)
        assertEquals(1f, b[1], 0.0001f)
        assertEquals(1f, b[0], 0.0001f)
    }

    @Test
    fun anim_subir_comeca_abaixo() {
        val a = TextAnim.frame(3, 1_000L, 1_000L, 3_000L)
        assertTrue(a[2] < 0f)
        assertEquals(0f, TextAnim.frame(3, 2_000L, 1_000L, 3_000L)[2], 0.0001f)
    }

    @Test
    fun anim_nenhuma_fica_inteira() {
        val a = TextAnim.frame(0, 1_500L, 1_000L, 3_000L)
        assertEquals(1f, a[0], 0.0001f)
        assertEquals(1f, a[1], 0.0001f)
    }

    // ---- LogoMath

    @Test
    fun logo_canto_inferior_direito_fica_dentro_do_quadro() {
        val r = LogoMath.rect(1080, 1920, 400, 200, 3, 1)
        assertTrue(r[0] > 0f && r[1] > 0f)
        assertTrue(r[2] < 1080f && r[3] < 1920f)
        assertTrue(r[2] > 1080f / 2 && r[3] > 1920f / 2)
        assertEquals(2f, (r[2] - r[0]) / (r[3] - r[1]), 0.001f)
    }

    @Test
    fun logo_no_centro_e_centralizado() {
        val r = LogoMath.rect(1920, 1080, 300, 300, 4, 0)
        assertEquals((1920f - (r[2] - r[0])) / 2f, r[0], 0.01f)
        assertEquals((1080f - (r[3] - r[1])) / 2f, r[1], 0.01f)
    }

    @Test
    fun logo_muito_alto_e_limitado_a_metade_da_altura() {
        val r = LogoMath.rect(1920, 1080, 100, 2000, 0, 2)
        assertTrue(r[3] - r[1] <= 1080f * 0.5f + 0.01f)
    }

    // ---- SplitMath

    @Test
    fun divisao_em_partes_iguais_cobre_o_clipe_todo() {
        val p = SplitMath.equalParts(10_000L, 3)
        assertEquals(3, p.size)
        assertEquals(0L, p[0].first)
        assertEquals(10_000L, p[2].second)
        for (k in 1 until p.size) assertEquals(p[k - 1].second, p[k].first)
    }

    @Test
    fun divisao_recusa_partes_curtas_demais() {
        assertTrue(SplitMath.equalParts(1_000L, 6).isEmpty())
        assertTrue(SplitMath.equalParts(10_000L, 1).isEmpty())
        assertTrue(SplitMath.equalParts(0L, 3).isEmpty())
    }
}
