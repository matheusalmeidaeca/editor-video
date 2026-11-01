package com.editorvideo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExportHelpersTest {

    @Test
    fun predefinicoes_tem_valores_validos() {
        assertTrue(ExportPresets.all.isNotEmpty())
        for (p in ExportPresets.all) {
            assertTrue(p.name, p.codecIdx in 0..1)
            assertTrue(p.name, p.qualityIdx in 0..3)
            assertTrue(p.name, p.bitrateIdx in 0..2)
        }
    }

    @Test
    fun erro_amigavel_por_faixa_e_com_codigo() {
        assertTrue(ExportHelpers.friendlyError(2004).contains("arquivos"))
        assertTrue(ExportHelpers.friendlyError(4001).contains("H.264"))
        assertTrue(ExportHelpers.friendlyError(7001).contains("espaço"))
        assertTrue(ExportHelpers.friendlyError(9).contains("(código 9)"))
    }

    @Test
    fun estimativa_e_folga_de_espaco() {
        // 8 Mbps de vídeo + 128 kbps de áudio por 10 s
        assertEquals(10_160_000L, ExportHelpers.estimatedBytes(8_000_000, 10_000L))
        assertTrue(ExportHelpers.hasRoom(500_000_000L, 100_000_000L))
        assertFalse(ExportHelpers.hasRoom(120_000_000L, 100_000_000L))
        // vídeo curtíssimo não zera a estimativa
        assertTrue(ExportHelpers.estimatedBytes(1_000_000, 100L) > 0L)
    }

    @Test
    fun purge_remove_so_o_que_nenhum_projeto_usa() {
        val raiz = Files.createTempDirectory("proj").toFile()
        val imports = Files.createTempDirectory("imp").toFile()
        val s = ProjectStore(raiz)
        val usado = java.io.File(imports, "111_usado.mp4").also { it.writeText("x") }
        val solto = java.io.File(imports, "222_solto.mp4").also { it.writeText("x") }
        val j = JSONObject().put("name", "P").put("uri", "file://" + usado.absolutePath).toString()
        s.write(s.newId(), j)
        assertEquals(1, s.purgeUnreferenced(imports))
        assertTrue(usado.exists())
        assertFalse(solto.exists())
    }
}
