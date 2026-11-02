package com.editorvideo

import android.app.AlertDialog
import android.content.ContentValues
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.content.res.ColorStateList

// v3.3.13: navegação, tempos digitados, rampas de velocidade, deslocar textos e exportar legendas (.srt).

/** Vai para o começo do projeto. */
internal fun MainActivity.goStart() {
    if (clips.isEmpty() || player.mediaItemCount != clips.size) return
    player.seekTo(0, 0L)
}

/** Vai para perto do fim do projeto (um pouco antes, para a prévia não reiniciar sozinha). */
internal fun MainActivity.goEnd() {
    if (clips.isEmpty() || player.mediaItemCount != clips.size) return
    val last = clips.size - 1
    val len = clips[last].endMs - clips[last].startMs
    player.seekTo(last, (len - 150L).coerceAtLeast(0L))
}

/** Pula para o começo do clipe anterior (d = -1) ou do próximo (d = 1). */
internal fun MainActivity.jumpClip(d: Int) {
    if (clips.isEmpty() || player.mediaItemCount != clips.size) return
    val cur = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
    var target = cur + d
    // ao voltar, se a agulha já passou do começo do clipe, o primeiro toque volta ao começo dele
    if (d < 0 && player.currentPosition > 800L) target = cur
    target = target.coerceIn(0, clips.size - 1)
    player.seekTo(target, 0L)
}

/** Diálogo para digitar o início e o fim do clipe em segundos (precisão que o controle não dá). */
internal fun MainActivity.trimTimesDialog() {
    val c = sel() ?: return
    if (c.isImage) return
    val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    fun field(h: String, ms: Long) = EditText(this).apply {
        hint = h
        setText("%.2f".format(ms / 1000.0))
        inputType = numType
        setTextColor(cText)
        setHintTextColor(cGrey)
        backgroundTintList = ColorStateList.valueOf(cAccent)
    }
    val a = field("Início (s)", c.startMs)
    val b = field("Fim (s)", c.endMs)
    val box = vbox().apply { setPadding(dp(20), dp(12), dp(20), 0) }
    box.addView(tv("O arquivo tem ${fmt(c.durationMs)} de duração.", 12f, cGrey))
    box.addView(a)
    box.addView(b)
    showStyled(
        AlertDialog.Builder(this)
            .setTitle("Digitar início e fim")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("OK") { _, _ ->
                val s = parseMs(a.text.toString())
                val e = parseMs(b.text.toString())
                if (s == null || e == null) {
                    toast("Digite os tempos em segundos, por exemplo 3,5.")
                } else {
                    val ns = s.coerceIn(0L, c.durationMs)
                    val ne = e.coerceIn(0L, c.durationMs)
                    if (ne - ns < 300L) {
                        toast("O fim precisa ficar pelo menos 0,3 s depois do início.")
                    } else {
                        pushUndo()
                        c.startMs = ns
                        c.endMs = ne
                        trimDone(false)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
    )
}

/** Aplica a duração da foto selecionada a todas as fotos do projeto (bom para apresentações de fotos). */
internal fun MainActivity.applyPhotoDurationToAll() {
    val c = sel() ?: return
    if (!c.isImage) return
    val len = c.endMs - c.startMs
    pushUndo()
    for (o in clips) {
        if (o.isImage) {
            o.startMs = 0L
            o.endMs = len
        }
    }
    afterEdit(selected, 0L)
    toast("Todas as fotos agora duram ${fmt(len)}.")
}

/** Divide o clipe selecionado em partes iguais e dá a cada parte a velocidade do padrão escolhido. */
internal fun MainActivity.applySpeedRamp(idx: Int) {
    val c = sel() ?: return
    if (c.isImage) return
    val speedsList = SpeedRamp.patterns.getOrNull(idx) ?: return
    val i = selected
    val parts = SpeedRamp.parts(c.endMs - c.startMs, speedsList)
    if (parts.isEmpty()) {
        toast("O clipe é curto demais para esta rampa de velocidade.")
        return
    }
    pushUndo()
    clips.removeAt(i)
    var at = i
    for ((k, p) in parts.withIndex()) {
        val nc = c.copy()
        nc.startMs = c.startMs + p.first
        nc.endMs = c.startMs + p.second
        nc.speed = speedsList[k]
        if (k > 0) nc.fadeIn = 0
        if (k < parts.size - 1) nc.fadeOut = 0
        clips.add(at++, nc)
    }
    selected = i
    statusTv.text = "Rampa aplicada: o clipe virou ${parts.size} partes com velocidades diferentes."
    afterEdit(i, 0L)
    for (k in i until i + parts.size) loadThumb(clips[k])
}

/** Diálogo que desloca todos os textos (ou todos os adesivos) por alguns segundos, para frente ou para trás. */
internal fun MainActivity.shiftTextsDialog(sticker: Boolean) {
    val et = EditText(this).apply {
        hint = "Segundos (negativo volta)"
        setText("0")
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        setTextColor(cText)
        setHintTextColor(cGrey)
        backgroundTintList = ColorStateList.valueOf(cAccent)
    }
    val box = vbox().apply { setPadding(dp(20), dp(12), dp(20), 0) }
    box.addView(tv("Soma este tempo ao início e ao fim de todos os itens da lista. Útil para acertar legendas importadas.", 12f, cGrey))
    box.addView(et)
    showStyled(
        AlertDialog.Builder(this)
            .setTitle("Deslocar tempos")
            .setView(box)
            .setPositiveButton("Aplicar") { _, _ ->
                val d = parseMs(et.text.toString())
                if (d == null || d == 0L) {
                    toast("Digite um valor em segundos, por exemplo 1,5 ou -2.")
                } else {
                    pushUndo()
                    for (k in texts.indices) {
                        val o = texts[k]
                        if (o.sticker != sticker) continue
                        val t = TextShift.shift(o.startMs, o.endMs, d)
                        texts[k] = TextItem(o.text, t.first, t.second, o.pos, o.size, o.color, o.posX, o.sticker, o.font, o.italic, o.bg, o.anim)
                    }
                    overlayKey = ""
                    showTool(currentTool)
                }
            }
            .setNegativeButton("Cancelar", null)
    )
}

/** Salva os textos (sem os adesivos) como legendas .srt em Downloads/EditorVideo. */
internal fun MainActivity.exportSrt() {
    val cues = texts.filter { !it.sticker }
        .sortedBy { it.startMs }
        .map { SrtCue(it.startMs, it.endMs, it.text) }
    if (cues.isEmpty()) {
        toast("Não há textos para exportar.")
        return
    }
    val conteudo = SrtWriter.write(cues)
    try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "legendas_${System.currentTimeMillis()}.srt")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/EditorVideo")
        }
        val out = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (out == null) {
            toast("Não consegui criar o arquivo de legendas.")
            return
        }
        contentResolver.openOutputStream(out)?.use { it.write(conteudo.toByteArray(Charsets.UTF_8)) }
        statusTv.text = "${cues.size} legenda(s) salvas em Download/EditorVideo."
    } catch (e: Exception) {
        toast("Não consegui salvar as legendas.")
    }
}

/** Vinheta e barras de cinema na prévia, com as mesmas contas da exportação. */
internal fun MainActivity.addStylePreview(lw: Int, lh: Int) {
    if (!StyleMath.active(vignette, bars)) return
    val bmp = StyleLayer.compose(lw, lh, vignette, bars)
    val iv = android.widget.ImageView(this)
    iv.setImageBitmap(bmp)
    iv.scaleType = android.widget.ImageView.ScaleType.FIT_XY
    overlayLayer.addView(iv, android.widget.FrameLayout.LayoutParams(lw, lh))
}
