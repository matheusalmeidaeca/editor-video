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
}
