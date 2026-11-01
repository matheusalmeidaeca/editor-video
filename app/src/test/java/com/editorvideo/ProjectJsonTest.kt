package com.editorvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class ProjectJsonTest {

    @Test
    fun texto_ida_e_volta() {
        val t = TextItem("Olá", 1_000L, 4_000L, 0, 2, 0xFFFF0000.toInt(), 2, true)
        val r = ProjectJson.textFromJson(ProjectJson.textToJson(t))
        assertEquals("Olá", r.text)
        assertEquals(1_000L, r.startMs)
        assertEquals(4_000L, r.endMs)
        assertEquals(0, r.pos)
        assertEquals(2, r.size)
        assertEquals(0xFFFF0000.toInt(), r.color)
        assertEquals(2, r.posX)
        assertTrue(r.sticker)
    }

    @Test
    fun texto_antigo_sem_campos_opcionais_usa_padroes() {
        val j = JSONObject("""{"text":"x","start":0,"end":1500}""")
        val r = ProjectJson.textFromJson(j)
        assertEquals(2, r.pos)
        assertEquals(1, r.size)
        assertEquals(1, r.posX)
        assertFalse(r.sticker)
        assertEquals(0xFFFFFFFF.toInt(), r.color)
    }

    @Test
    fun texto_com_estilo_ida_e_volta() {
        val t = TextItem("Estilo", 0L, 2_000L, 2, 1, 0xFFFFFFFF.toInt(), 1, false, 3, true, 2, 2)
        val r = ProjectJson.textFromJson(ProjectJson.textToJson(t))
        assertEquals(3, r.font)
        assertTrue(r.italic)
        assertEquals(2, r.bg)
        assertEquals(2, r.anim)
    }

    @Test
    fun texto_sem_estilo_usa_o_visual_antigo() {
        val j = JSONObject("""{"text":"x","start":0,"end":1500}""")
        val r = ProjectJson.textFromJson(j)
        assertEquals(0, r.font)
        assertFalse(r.italic)
        assertEquals(0, r.bg)
        assertEquals(0, r.anim)
    }

    @Test
    fun texto_com_valores_fora_da_faixa_e_corrigido() {
        val j = JSONObject("""{"text":"x","start":0,"end":1500,"font":99,"bg":7,"anim":-3}""")
        val r = ProjectJson.textFromJson(j)
        assertEquals(4, r.font)
        assertEquals(2, r.bg)
        assertEquals(0, r.anim)
    }
}
