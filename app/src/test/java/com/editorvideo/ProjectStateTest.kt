package com.editorvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStateTest {

    @Test
    fun novo_projeto_tem_os_padroes() {
        val s = ProjectState()
        assertTrue(s.clips.isEmpty())
        assertTrue(s.texts.isEmpty())
        assertEquals(0, s.selected)
        assertEquals(100, s.musicVol)
        assertEquals(100, s.voiceVol)
        assertEquals(1, s.transIdx)
        assertEquals("Meu projeto", s.projectName)
        assertNull(s.musicUri)
    }

    @Test
    fun reset_volta_ao_padrao() {
        val s = ProjectState()
        s.texts.add(TextItem("a", 0L, 1000L, 0, 1, 0))
        s.selected = 3
        s.musicVol = 40
        s.muteOriginal = true
        s.aspectIdx = 2
        s.transType = 1
        s.transIdx = 3
        s.projectName = "Outro"
        s.reset()
        assertTrue(s.texts.isEmpty())
        assertEquals(0, s.selected)
        assertEquals(100, s.musicVol)
        assertEquals(false, s.muteOriginal)
        assertEquals(0, s.aspectIdx)
        assertEquals(0, s.transType)
        assertEquals(1, s.transIdx)
        assertEquals("Meu projeto", s.projectName)
    }

    @Test
    fun snapshot_e_restore_ida_e_volta() {
        val s = ProjectState()
        s.texts.add(TextItem("oi", 0L, 2000L, 1, 1, 0))
        s.musicVol = 55
        s.voiceVol = 70
        s.fitIdx = 1
        s.fadeOutIdx = 2
        val snap = s.snapshot()
        s.reset()
        s.restore(snap)
        assertEquals(1, s.texts.size)
        assertEquals("oi", s.texts[0].text)
        assertEquals(55, s.musicVol)
        assertEquals(70, s.voiceVol)
        assertEquals(1, s.fitIdx)
        assertEquals(2, s.fadeOutIdx)
    }

    @Test
    fun restore_limita_selecao_a_lista_vazia() {
        val s = ProjectState()
        s.selected = 5
        s.restore(s.snapshot())
        assertEquals(0, s.selected)
    }

    @Test
    fun projeto_vazio_tem_duracao_zero() {
        val s = ProjectState()
        assertEquals(0L, s.totalMs())
        assertTrue(s.effs().isEmpty())
        assertEquals(TimelineMath.boundaries(emptyList()), s.bounds())
    }
}
