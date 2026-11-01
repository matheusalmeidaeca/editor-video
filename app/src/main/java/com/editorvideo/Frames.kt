package com.editorvideo

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

// v3.3.12: quadro a quadro, quadro congelado, quadro como foto, divisão em partes iguais e legendas .srt.

/** Lê um quadro do vídeo no instante [atMs] (tempo do arquivo de origem). Devolve null se não der. */
internal fun MainActivity.grabFrame(uri: Uri, atMs: Long): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(this, uri)
        r.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (e: Exception) {
        null
    } finally {
        r.release()
    }
}

/** Anda a agulha alguns milissegundos dentro do clipe atual (33 ms é cerca de um quadro a 30 fps). */
internal fun MainActivity.stepFrame(dMs: Long) {
    if (clips.isEmpty() || player.mediaItemCount != clips.size) return
    player.pause()
    val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
    val c = clips[idx]
    val len = c.endMs - c.startMs
    val np = (player.currentPosition + dMs).coerceIn(0L, (len - 1L).coerceAtLeast(0L))
    player.seekTo(idx, np)
}

/** Divide o clipe selecionado em [n] partes iguais (a análise de silêncio não é usada aqui). */
internal fun MainActivity.splitEqual(n: Int) {
    val c = sel() ?: return
    val i = selected
    val parts = SplitMath.equalParts(c.endMs - c.startMs, n)
    if (parts.isEmpty()) {
        toast("O clipe é curto demais para dividir em $n partes.")
        return
    }
    pushUndo()
    clips.removeAt(i)
    var at = i
    for ((k, p) in parts.withIndex()) {
        val nc = c.copy()
        nc.startMs = c.startMs + p.first
        nc.endMs = c.startMs + p.second
        // os fades de áudio do clipe original ficam só nas pontas do conjunto
        if (k > 0) nc.fadeIn = 0
        if (k < parts.size - 1) nc.fadeOut = 0
        clips.add(at++, nc)
    }
    selected = i
    statusTv.text = "Clipe dividido em ${parts.size} partes iguais."
    afterEdit(i, 0L)
    for (k in i until i + parts.size) loadThumb(clips[k])
}

/**
 * Congela o quadro onde a agulha está: o clipe é dividido ali e entra uma foto de 3 s no meio,
 * com a mesma rotação, filtros e zoom do clipe. A duração da foto se ajusta em Cortar.
 */
internal fun MainActivity.freezeFrame() {
    if (clips.isEmpty() || player.mediaItemCount != clips.size) {
        toast("Adicione um vídeo primeiro.")
        return
    }
    val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
    val c = clips[idx]
    if (c.isImage) {
        toast("Isto já é uma foto.")
        return
    }
    val at = c.startMs + player.currentPosition
    if (at < c.startMs + 300L || at > c.endMs - 300L) {
        toast("Posicione o vídeo no ponto do quadro (longe das pontas) e toque em Congelar.")
        return
    }
    player.pause()
    val uri = c.uri
    val alvo = currentId
    val s0 = c.startMs
    val e0 = c.endMs
    statusTv.text = "Capturando o quadro…"
    bgExec.execute {
        var arquivo: File? = null
        var w = 0
        var h = 0
        try {
            val bmp = grabFrame(uri, at)
            if (bmp != null) {
                val dir = File(filesDir, "imports").apply { mkdirs() }
                val f = File(dir, "${System.currentTimeMillis()}_quadro.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                if (f.length() > 0L) {
                    arquivo = f
                    w = bmp.width
                    h = bmp.height
                }
            }
        } catch (e: Exception) {
            arquivo = null
        }
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            val f = arquivo
            if (f == null || w <= 0 || h <= 0) {
                statusTv.text = ""
                toast("Não consegui capturar o quadro.")
                return@runOnUiThread
            }
            val j = clips.indexOf(c)
            if (currentId != alvo || j < 0 || c.startMs != s0 || c.endMs != e0) {
                statusTv.text = ""
                toast("O clipe mudou durante a captura. Tente de novo.")
                return@runOnUiThread
            }
            pushUndo()
            val freeze = Clip(Uri.fromFile(f), "Quadro congelado", Clip.IMAGE_MAX_MS, w, h)
            freeze.isImage = true
            freeze.endMs = 3000L
            freeze.rotation = c.rotation
            freeze.flip = c.flip
            freeze.flipV = c.flipV
            freeze.filter = c.filter
            freeze.brightness = c.brightness
            freeze.contrast = c.contrast
            freeze.sat = c.sat
            freeze.temp = c.temp
            freeze.tint = c.tint
            freeze.zoom = c.zoom
            freeze.panX = c.panX
            freeze.panY = c.panY
            val second = c.copy()
            second.startMs = at
            second.fadeIn = 0
            c.endMs = at
            c.fadeOut = 0
            clips.add(j + 1, freeze)
            clips.add(j + 2, second)
            selected = j + 1
            statusTv.text = "Quadro congelado por 3 s. Ajuste o tempo em Cortar."
            afterEdit(j + 1, 0L)
            loadThumb(freeze)
            loadThumb(second)
        }
    }
}

/** Salva o quadro onde a agulha está como foto em Imagens/EditorVideo (a imagem original, sem filtros nem textos). */
internal fun MainActivity.saveFrameAsPhoto() {
    if (clips.isEmpty() || player.mediaItemCount != clips.size) {
        toast("Adicione um vídeo primeiro.")
        return
    }
    val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
    val c = clips[idx]
    val at = c.startMs + player.currentPosition
    player.pause()
    val uri = c.uri
    val ehFoto = c.isImage
    statusTv.text = "Salvando o quadro…"
    bgExec.execute {
        var ok = false
        try {
            val bmp: Bitmap? = if (ehFoto) {
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } else {
                grabFrame(uri, at)
            }
            if (bmp != null) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "quadro_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EditorVideo")
                }
                val out = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (out != null) {
                    contentResolver.openOutputStream(out)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    ok = true
                }
            }
        } catch (e: Exception) {
            ok = false
        }
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            if (ok) {
                statusTv.text = "Quadro salvo em Imagens/EditorVideo (sem filtros nem textos)."
            } else {
                statusTv.text = ""
                toast("Não consegui salvar o quadro.")
            }
        }
    }
}

/** Lê um arquivo .srt e cria um texto por legenda, na base da tela, no tempo da linha do tempo final. */
internal fun MainActivity.importSrt(uri: Uri) {
    fun ler(cs: Charset): String? = try {
        contentResolver.openInputStream(uri)?.bufferedReader(cs)?.use { it.readText() }
    } catch (e: Exception) {
        null
    }

    var txt = ler(Charsets.UTF_8)
    // arquivos antigos vêm em Latin-1: se o UTF-8 deu caracteres quebrados, lê de novo assim
    if (txt != null && txt.contains('\uFFFD')) txt = ler(Charsets.ISO_8859_1) ?: txt
    if (txt == null) {
        toast("Não consegui ler o arquivo.")
        return
    }
    val cues = SrtParser.parse(txt)
    if (cues.isEmpty()) {
        toast("Não encontrei legendas neste arquivo (precisa ser .srt).")
        return
    }
    val usadas = cues.take(500)
    pushUndo()
    for (q in usadas) texts.add(TextItem(q.text, q.startMs, q.endMs, 2, 0, Color.WHITE, 1, false))
    overlayKey = ""
    showTool(currentTool)
    toast(
        if (cues.size > usadas.size) "Importei ${usadas.size} das ${cues.size} legendas (limite de 500)."
        else "Importei ${usadas.size} legenda(s)."
    )
}
