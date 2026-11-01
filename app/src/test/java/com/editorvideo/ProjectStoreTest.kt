package com.editorvideo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectStoreTest {

    private fun store(): ProjectStore {
        val d = Files.createTempDirectory("projetos").toFile()
        d.deleteOnExit()
        return ProjectStore(d)
    }

    private fun json(name: String, updated: Long) =
        JSONObject().put("name", name).put("updated", updated).put("totalMs", 5000L).put("clipCount", 2).toString()

    @Test
    fun grava_e_le() {
        val s = store()
        val id = s.newId()
        assertTrue(s.write(id, json("A", 1L)))
        assertEquals("A", JSONObject(s.read(id)!!).getString("name"))
    }

    @Test
    fun lista_ordena_do_mais_recente() {
        val s = store()
        val a = s.newId(); s.write(a, json("Antigo", 100L))
        val b = s.newId(); s.write(b, json("Novo", 200L))
        val l = s.list()
        assertEquals(listOf("Novo", "Antigo"), l.map { it.name })
        assertEquals(2, l[0].clipCount)
        assertEquals(5000L, l[0].totalMs)
    }

    @Test
    fun duplicar_cria_copia_independente() {
        val s = store()
        val a = s.newId(); s.write(a, json("Meu", 1L))
        val b = s.duplicate(a)
        assertNotNull(b)
        assertNotEquals(a, b)
        assertEquals("Meu (cópia)", JSONObject(s.read(b!!)!!).getString("name"))
        assertEquals("Meu", JSONObject(s.read(a)!!).getString("name"))
    }

    @Test
    fun renomear_e_excluir() {
        val s = store()
        val a = s.newId(); s.write(a, json("Velho", 1L))
        assertTrue(s.rename(a, "Novo nome"))
        assertEquals("Novo nome", s.list()[0].name)
        s.delete(a)
        assertNull(s.read(a))
        assertTrue(s.isEmpty())
    }

    @Test
    fun arquivo_corrompido_nao_aparece_na_lista() {
        val s = store()
        val ok = s.newId(); s.write(ok, json("Bom", 1L))
        val ruim = s.newId(); s.write(ruim, "{isso não é json")
        assertEquals(listOf("Bom"), s.list().map { it.name })
    }

    @Test
    fun ids_sao_diferentes() {
        val s = store()
        val ids = (1..50).map { s.newId() }.toSet()
        assertEquals(50, ids.size)
        assertFalse(ids.any { it.isEmpty() })
    }
}
