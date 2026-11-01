package com.editorvideo

import android.net.Uri
import org.json.JSONObject

/**
 * Conversão dos clipes e textos para JSON e de volta (saiu do MainActivity na v3.2.5).
 * Os nomes das chaves e os valores padrão são os mesmos dos projetos já salvos.
 */
internal object ProjectJson {

    fun clipToJson(c: Clip): JSONObject {
        val j = JSONObject()
        j.put("uri", c.uri.toString())
        j.put("name", c.name)
        j.put("dur", c.durationMs)
        j.put("w", c.w)
        j.put("h", c.h)
        j.put("start", c.startMs)
        j.put("end", c.endMs)
        j.put("speed", c.speed.toDouble())
        j.put("rot", c.rotation)
        j.put("flip", c.flip)
        j.put("filter", c.filter)
        j.put("bri", c.brightness)
        j.put("con", c.contrast)
        j.put("sat", c.sat)
        j.put("vol", c.vol)
        j.put("fin", c.fadeIn)
        j.put("fout", c.fadeOut)
        j.put("zoom", c.zoom)
        j.put("px", c.panX)
        j.put("py", c.panY)
        j.put("ken", c.ken)
        if (c.isImage) j.put("img", true)
        return j
    }

    /** [uri] já vem lida e conferida por quem chama (o arquivo pode ter sumido). */
    fun clipFromJson(j: JSONObject, uri: Uri): Clip {
        val c = Clip(uri, j.optString("name", "clipe"), j.getLong("dur"), j.getInt("w"), j.getInt("h"))
        c.startMs = j.optLong("start", 0L)
        c.endMs = j.optLong("end", c.durationMs)
        c.speed = j.optDouble("speed", 1.0).toFloat()
        c.rotation = j.optInt("rot", 0)
        c.flip = j.optBoolean("flip", false)
        c.filter = j.optInt("filter", 0)
        c.brightness = j.optInt("bri", 0)
        c.contrast = j.optInt("con", 0)
        c.sat = j.optInt("sat", 0)
        c.isImage = j.optBoolean("img", false)
        c.vol = j.optInt("vol", 100)
        c.fadeIn = j.optInt("fin", 0)
        c.fadeOut = j.optInt("fout", 0)
        c.zoom = j.optInt("zoom", 100)
        c.panX = j.optInt("px", 0)
        c.panY = j.optInt("py", 0)
        c.ken = j.optInt("ken", 0)
        return c
    }

    fun textToJson(t: TextItem): JSONObject {
        val j = JSONObject()
        j.put("text", t.text)
        j.put("start", t.startMs)
        j.put("end", t.endMs)
        j.put("pos", t.pos)
        j.put("size", t.size)
        j.put("color", t.color)
        j.put("posX", t.posX)
        j.put("sticker", t.sticker)
        return j
    }

    fun textFromJson(j: JSONObject): TextItem = TextItem(
        j.getString("text"), j.getLong("start"), j.getLong("end"),
        j.optInt("pos", 2), j.optInt("size", 1), j.optInt("color", 0xFFFFFFFF.toInt()),
        j.optInt("posX", 1), j.optBoolean("sticker", false)
    )
}
