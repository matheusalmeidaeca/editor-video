package com.editorvideo

import org.json.JSONObject
import java.io.File

/** Resumo de um projeto para a lista da tela inicial. */
class ProjectMeta(
    val id: String,
    val name: String,
    val updatedMs: Long,
    val totalMs: Long,
    val clipCount: Int
)

/**
 * Projetos como arquivos: `projects/<id>/project.json` (+ `thumb.jpg`).
 * Não depende de Android além de java.io e org.json, então roda em teste de JVM.
 */
class ProjectStore(private val root: File) {

    init {
        root.mkdirs()
    }

    private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun newId(): String {
        val tail = (1..6).map { alphabet.random() }.joinToString("")
        return System.currentTimeMillis().toString(36) + tail
    }

    fun dir(id: String) = File(root, id)
    fun jsonFile(id: String) = File(dir(id), "project.json")
    fun thumbFile(id: String) = File(dir(id), "thumb.jpg")

    fun read(id: String): String? = try {
        val f = jsonFile(id)
        if (f.exists()) f.readText() else null
    } catch (e: Exception) {
        null
    }

    /** Grava de forma segura: escreve num arquivo temporário e só então troca o definitivo. */
    fun write(id: String, json: String): Boolean = try {
        val d = dir(id)
        d.mkdirs()
        val tmp = File(d, "project.json.tmp")
        tmp.writeText(json)
        val dest = jsonFile(id)
        if (!tmp.renameTo(dest)) {
            dest.writeText(json)
            tmp.delete()
        }
        true
    } catch (e: Exception) {
        false
    }

    fun list(): List<ProjectMeta> {
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        val out = ArrayList<ProjectMeta>()
        for (d in dirs) {
            val s = read(d.name) ?: continue
            try {
                val o = JSONObject(s)
                out.add(
                    ProjectMeta(
                        d.name,
                        o.optString("name", "Meu projeto"),
                        o.optLong("updated", jsonFile(d.name).lastModified()),
                        o.optLong("totalMs", 0L),
                        o.optInt("clipCount", o.optJSONArray("clips")?.length() ?: 0)
                    )
                )
            } catch (e: Exception) {
                // arquivo corrompido: não aparece na lista
            }
        }
        return out.sortedByDescending { it.updatedMs }
    }

    fun rename(id: String, newName: String): Boolean {
        val s = read(id) ?: return false
        return try {
            val o = JSONObject(s)
            o.put("name", newName)
            o.put("updated", System.currentTimeMillis())
            write(id, o.toString())
        } catch (e: Exception) {
            false
        }
    }

    fun duplicate(id: String): String? {
        val s = read(id) ?: return null
        return try {
            val o = JSONObject(s)
            o.put("name", o.optString("name", "Meu projeto") + " (cópia)")
            o.put("updated", System.currentTimeMillis())
            val nid = newId()
            if (!write(nid, o.toString())) return null
            val t = thumbFile(id)
            if (t.exists()) t.copyTo(thumbFile(nid), overwrite = true)
            nid
        } catch (e: Exception) {
            null
        }
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    fun isEmpty(): Boolean = list().isEmpty()

    /**
     * Apaga de [dir] os arquivos cujo nome não aparece em nenhum projeto. Serve para liberar os vídeos
     * copiados do "Compartilhar" quando o projeto que os usava foi excluído.
     */
    fun purgeUnreferenced(dir: File): Int {
        val files = dir.listFiles { f -> f.isFile } ?: return 0
        val todos = StringBuilder()
        root.listFiles { f -> f.isDirectory }?.forEach { d -> read(d.name)?.let { todos.append(it) } }
        val texto = todos.toString()
        var n = 0
        for (f in files) {
            if (!texto.contains(f.name)) {
                if (f.delete()) n++
            }
        }
        return n
    }
}
