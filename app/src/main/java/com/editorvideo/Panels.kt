package com.editorvideo

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Painéis das ferramentas (saíram do MainActivity na v3.3.1). Funções de extensão: o estado continua na tela.

internal fun MainActivity.panelTrim(): View {
    val c = sel() ?: return hint()
    val box = vbox()
    if (c.isImage) {
        box.addView(tv("Foto selecionada: escolha por quanto tempo ela aparece."))
        labeledSeek(box, { "Duração: ${it / 10},${it % 10} s" }, 5, (Clip.IMAGE_MAX_MS / 100).toInt(),
            ((c.endMs - c.startMs) / 100L).toInt(), { c.endMs = c.startMs + it * 100L }, { pushUndo() }, { afterEdit(selected, 0L) })
        return box
    }
    val l1 = tv("", 13f)
    val l2 = tv("", 13f)
    fun upd() {
        l1.text = "Início: ${fmt(c.startMs)}"
        l2.text = "Fim: ${fmt(c.endMs)}   (duração ${fmt(c.endMs - c.startMs)})"
    }
    upd()
    box.addView(l1)
    box.addView(
        seek(c.durationMs.toInt(), c.startMs.toInt(), { p, user ->
            if (user) {
                c.startMs = p.toLong().coerceAtMost(c.endMs - 300L).coerceAtLeast(0L)
                upd()
            }
        }, { pushUndo() }) { trimDone(false) }
    )
    box.addView(l2, lpm(WRAP, WRAP, t = 6))
    box.addView(
        seek(c.durationMs.toInt(), c.endMs.toInt(), { p, user ->
            if (user) {
                c.endMs = p.toLong().coerceAtLeast(c.startMs + 300L).coerceAtMost(c.durationMs)
                upd()
            }
        }, { pushUndo() }) { trimDone(true) }
    )
    val r = hbox()
    r.addView(btn("⇤ Início aqui") { trimHere(false) }, lpm(WRAP, WRAP, t = 10, r = 6))
    r.addView(btn("Fim aqui ⇥") { trimHere(true) }, lpm(WRAP, WRAP, t = 10))
    box.addView(hscroll(r))
    box.addView(btn("Remover silêncios") { removerSilencios() }, lpm(WRAP, WRAP, t = 10))
    box.addView(
        note("Arraste os controles ou pause no ponto certo e toque em “Início aqui” / “Fim aqui”. Para cortar no meio, use ✂ Dividir. “Remover silêncios” divide o clipe nos trechos com som e tira as pausas longas."),
        lpm(MATCH, WRAP, t = 8)
    )
    return box
}

/**
 * Divide o clipe selecionado nos trechos com som e descarta as pausas longas (500 ms ou mais).
 * A análise decodifica o áudio fora da thread da tela; o resultado vira vários clipes, com desfazer.
 */
internal fun MainActivity.removerSilencios() {
    val c = sel() ?: return
    if (c.isImage) {
        toast("Fotos não têm áudio.")
        return
    }
    if (analysingSilence || exporting || extractingAudio) {
        toast("Espere o processo em andamento terminar.")
        return
    }
    analysingSilence = true
    statusTv.text = "Procurando silêncios…"
    val uri = c.uri
    val s0 = c.startMs
    val e0 = c.endMs
    Thread {
        val bucket = 50L
        val peaks = AudioPeaks.compute(applicationContext, uri, s0, e0, bucket)
        val ranges = if (peaks == null) null
        else SilenceMath.voicedRanges(peaks, bucket, 0.06f, 500L, 120L, 100L, 300L)
        handler.post {
            analysingSilence = false
            if (!isDestroyed) aplicarSilencios(c, s0, e0, ranges)
        }
    }.start()
}

internal fun MainActivity.aplicarSilencios(c: Clip, s0: Long, e0: Long, ranges: List<Pair<Long, Long>>?) {
    val i = clips.indexOf(c)
    if (i < 0 || c.startMs != s0 || c.endMs != e0) {
        statusTv.text = ""
        toast("O clipe mudou durante a análise. Tente de novo.")
        return
    }
    if (ranges == null) {
        statusTv.text = "Não consegui analisar o áudio deste clipe (sem som ou formato não aceito)."
        return
    }
    if (ranges.isEmpty()) {
        statusTv.text = "Não encontrei fala ou som neste clipe."
        return
    }
    val total = e0 - s0
    val pieces = ranges.map { Pair(it.first.coerceAtMost(total), it.second.coerceAtMost(total)) }
        .filter { it.second - it.first >= 300L }
    val kept = pieces.sumOf { it.second - it.first }
    val removed = total - kept
    if (pieces.isEmpty() || removed < 500L) {
        statusTv.text = "Não encontrei silêncios longos para remover."
        return
    }
    pushUndo()
    clips.removeAt(i)
    var at = i
    for ((k, p) in pieces.withIndex()) {
        val n = c.copy()
        n.startMs = s0 + p.first
        n.endMs = s0 + p.second
        // os fades de áudio do clipe original ficam só nas pontas do conjunto
        if (k > 0) n.fadeIn = 0
        if (k < pieces.size - 1) n.fadeOut = 0
        clips.add(at++, n)
    }
    selected = i
    statusTv.text = "Removi ${fmt(removed)} de silêncio e o clipe virou ${pieces.size} trecho(s)."
    afterEdit(i, 0L)
    for (k in i until i + pieces.size) loadThumb(clips[k])
}

internal fun MainActivity.trimHere(isEnd: Boolean) {
    val c = sel() ?: return
    if (player.mediaItemCount != clips.size || player.currentMediaItemIndex != selected) {
        toast("Posicione o vídeo dentro do clipe selecionado.")
        return
    }
    val at = c.startMs + player.currentPosition
    if (isEnd) {
        if (at <= c.startMs + 300L) {
            toast("O fim precisa ficar depois do início.")
            return
        }
        pushUndo()
        c.endMs = at.coerceAtMost(c.durationMs)
    } else {
        if (at >= c.endMs - 300L) {
            toast("O início precisa ficar antes do fim.")
            return
        }
        pushUndo()
        c.startMs = at.coerceAtLeast(0L)
    }
    trimDone(isEnd)
}

internal fun MainActivity.panelSpeed(): View {
    val c = sel() ?: return hint()
    val box = vbox()
    if (c.isImage) {
        box.addView(tv("Fotos não têm velocidade. Mude o tempo de exibição em Cortar.", 13f, cGrey))
        return box
    }
    box.addView(tv("Velocidade do clipe selecionado (o áudio acompanha)."))
    box.addView(chipRow(speeds.map { "${fmtSpeed(it)}x" }, speeds.indexOf(c.speed)) { i ->
        pushUndo()
        c.speed = speeds[i]
        if (player.currentMediaItemIndex == selected) player.setPlaybackSpeed(c.speed)
        refreshTimeline()
        updateSeekMax()
        showTool(currentTool)
    })
    return box
}

internal fun MainActivity.panelFilters(): View {
    val c = sel() ?: return hint()
    val box = vbox()
    box.addView(chipRow(filterNames, c.filter) { i ->
        pushUndo()
        c.filter = i
        applyPreviewEffects()
        refreshTimeline()
        showTool(currentTool)
    })
    box.addView(section("Ajustes"), lpm(WRAP, WRAP, t = 12))
    val stop = {
        applyPreviewEffects()
        refreshTimeline()
        scheduleSave()
    }
    labeledSeek(box, { "Brilho: $it" }, -50, 50, c.brightness, { c.brightness = it }, { pushUndo() }, stop)
    labeledSeek(box, { "Contraste: $it" }, -50, 50, c.contrast, { c.contrast = it }, { pushUndo() }, stop)
    labeledSeek(box, { "Saturação: $it" }, -50, 50, c.sat, { c.sat = it }, { pushUndo() }, stop)
    val r = hbox()
    r.addView(btn("✨ Auto") {
        pushUndo()
        c.brightness = 4
        c.contrast = 12
        c.sat = 18
        applyPreviewEffects()
        refreshTimeline()
        showTool(currentTool)
    }, lpm(WRAP, WRAP, t = 12, r = 6))
    r.addView(btn("Redefinir") {
        pushUndo()
        c.filter = 0
        c.brightness = 0
        c.contrast = 0
        c.sat = 0
        applyPreviewEffects()
        refreshTimeline()
        showTool(currentTool)
    }, lpm(WRAP, WRAP, t = 12, r = 6))
    r.addView(btn("Aplicar a todos") {
        pushUndo()
        clips.forEach {
            it.filter = c.filter
            it.brightness = c.brightness
            it.contrast = c.contrast
            it.sat = c.sat
        }
        applyPreviewEffects()
        refreshTimeline()
        toast("Aplicado a todos os clipes")
    }, lpm(WRAP, WRAP, t = 12))
    box.addView(hscroll(r))
    box.addView(note("A prévia já mostra filtros e ajustes ao vivo."), lpm(MATCH, WRAP, t = 8))
    return box
}

internal fun MainActivity.panelTransform(): View {
    val c = sel() ?: return hint()
    val box = vbox()
    box.addView(tv("Rotação: ${c.rotation}°  ·  Espelhado: ${if (c.flip) "sim" else "não"}"))
    val r = hbox()
    r.addView(btn("↻ Girar 90°") {
        pushUndo()
        c.rotation = (c.rotation + 90) % 360
        applyPreviewEffects()
        showTool(currentTool)
    }, lpm(WRAP, WRAP, t = 6, r = 6))
    r.addView(btn("⇋ Espelhar") {
        pushUndo()
        c.flip = !c.flip
        applyPreviewEffects()
        showTool(currentTool)
    }, lpm(WRAP, WRAP, t = 6, r = 6))
    r.addView(btn("Redefinir") {
        pushUndo()
        c.rotation = 0
        c.flip = false
        c.zoom = 100
        c.panX = 0
        c.panY = 0
        c.ken = 0
        applyPreviewEffects()
        showTool(currentTool)
    }, lpm(WRAP, WRAP, t = 6))
    box.addView(hscroll(r))
    val stop = {
        applyPreviewEffects()
        scheduleSave()
    }
    labeledSeek(box, { "Zoom: $it%" }, 100, 300, c.zoom, { c.zoom = it }, { pushUndo() }, stop)
    labeledSeek(box, { "Posição horizontal: $it" }, -100, 100, c.panX, { c.panX = it }, { pushUndo() }, stop)
    labeledSeek(box, { "Posição vertical: $it" }, -100, 100, c.panY, { c.panY = it }, { pushUndo() }, stop)
    box.addView(section("Panorâmica e zoom animado"), lpm(WRAP, WRAP, t = 14))
    box.addView(chipRow(kenNames, c.ken) { i ->
        pushUndo()
        c.ken = i
        showTool(currentTool)
    })
    box.addView(
        note("Rotação, espelho e zoom aparecem na prévia. O movimento animado só aparece no vídeo exportado."),
        lpm(MATCH, WRAP, t = 8)
    )
    return box
}

internal fun MainActivity.panelText(sticker: Boolean): View {
    val box = vbox()
    if (sticker) {
        box.addView(tv("Toque num adesivo para colocá-lo na posição atual do vídeo."))
        val rows = listOf(emojis.subList(0, 10), emojis.subList(10, 20))
        for (row in rows) {
            val h = hbox()
            for (e in row) {
                val t = tv(e, 26f).apply {
                    gravity = Gravity.CENTER
                    background = glass(14)
                    setOnClickListener { addSticker(e) }
                }
                press(t)
                h.addView(t, lpm(dp(48), dp(48), t = 8, r = 6))
            }
            box.addView(hscroll(h))
        }
    }
    var any = false
    texts.forEachIndexed { i, t ->
        if (t.sticker == sticker) {
            any = true
            val r = hbox()
            r.addView(
                tv("${if (sticker) t.text else "“${t.text}”"}  ${fmt(t.startMs)} – ${fmt(t.endMs)}", 13f),
                lpm(0, WRAP, weight = 1f)
            )
            r.addView(btn("Editar") { itemDialog(i, sticker) }, lpm(WRAP, WRAP, r = 6))
            r.addView(btn("Remover") {
                pushUndo()
                texts.removeAt(i)
                overlayKey = ""
                showTool(currentTool)
            })
            box.addView(r, lpm(MATCH, WRAP, t = 10))
        }
    }
    if (!sticker) {
        box.addView(btn("+ Adicionar texto") { itemDialog(-1, false) }, lpm(WRAP, WRAP, t = if (any) 12 else 4))
    }
    box.addView(
        note("O tempo vale para o vídeo final (linha do tempo inteira). A prévia mostra uma aproximação."),
        lpm(MATCH, WRAP, t = 10)
    )
    return box
}

internal fun MainActivity.addSticker(e: String) {
    pushUndo()
    val now = currentTimelineMs()
    texts.add(TextItem(e, now, now + 3000L, 1, 1, Color.WHITE, 1, true))
    overlayKey = ""
    showTool(currentTool)
}

/**
 * Ajusta o volume do clipe para o pico do trecho cortado chegar a cerca de 90% (sobe voz baixa, desce áudio alto).
 * Usa a forma de onda já analisada; se ela ainda não existe, pede para tentar de novo.
 * Na prévia o volume não passa de 100%; o ganho acima disso vale na exportação.
 */
internal fun MainActivity.normalizarVolume(c: Clip) {
    if (c.isImage) {
        toast("Fotos não têm áudio.")
        return
    }
    val wd = waveFor(c.uri, c.durationMs)
    if (wd == null) {
        toast("A forma de onda ainda está sendo analisada, ou o clipe não tem som. Tente de novo em alguns segundos.")
        return
    }
    val pico = WaveMath.maxIn(wd.peaks, wd.bucketMs, c.startMs.toDouble(), c.endMs.toDouble())
    val novo = WaveMath.volumeFor(pico)
    if (novo == null) {
        statusTv.text = "Este trecho está praticamente mudo; não normalizei."
        return
    }
    if (novo == c.vol) {
        statusTv.text = "O volume já está no ponto (${c.vol}%)."
        return
    }
    pushUndo()
    c.vol = novo
    applyVolume()
    scheduleSave()
    statusTv.text = "Volume do clipe: $novo% (o pico original era ${Math.round(pico * 100)}%)."
    showTool(currentTool)
}

internal fun MainActivity.panelAudio(): View {
    val box = vbox()
    box.addView(section("Clipe selecionado"))
    val c = sel()
    if (c == null) {
        box.addView(hint())
    } else if (c.isImage) {
        box.addView(tv("Fotos não têm áudio. Use música ou narração no projeto.", 12f, cGrey))
    } else {
        labeledSeek(box, { "Volume: $it%" }, 0, 200, c.vol, { c.vol = it }, { pushUndo() }, {
            applyVolume()
            refreshTimeline()
            scheduleSave()
        })
        // fade de áudio por clipe, em passos de 0,1 s (0 a 3 s). Só vale na exportação; a prévia não mostra.
        labeledSeek(box, { "Fade de entrada do áudio: ${it / 10},${it % 10} s" }, 0, 30, c.fadeIn / 100,
            { c.fadeIn = it * 100 }, { pushUndo() }, { scheduleSave() })
        labeledSeek(box, { "Fade de saída do áudio: ${it / 10},${it % 10} s" }, 0, 30, c.fadeOut / 100,
            { c.fadeOut = it * 100 }, { pushUndo() }, { scheduleSave() })
        box.addView(tv("O fade de cada clipe é aplicado ao exportar; a prévia ainda não o mostra.", 12f, cGrey), lpm(WRAP, WRAP, t = 4))
        box.addView(btn("Normalizar volume do clipe") { normalizarVolume(c) }, lpm(WRAP, WRAP, t = 8))
        box.addView(btn("Extrair áudio deste clipe") { extrairAudio(c) }, lpm(WRAP, WRAP, t = 8))
    }
    val cb = CheckBox(this).apply {
        text = "Remover áudio original dos vídeos"
        setTextColor(cText)
        buttonTintList = ColorStateList.valueOf(cAccent)
        isChecked = muteOriginal
        setOnCheckedChangeListener { _, v ->
            pushUndo()
            muteOriginal = v
            applyVolume()
        }
    }
    box.addView(cb, lpm(WRAP, WRAP, t = 6))

    box.addView(section("Música de fundo"), lpm(WRAP, WRAP, t = 14))
    val mu = musicUri
    box.addView(
        tv(if (mu == null) "Nenhuma música adicionada." else "Música: $musicName (${fmt(musicDurMs)})", 13f),
        lpm(WRAP, WRAP, t = 4)
    )
    val r = hbox()
    r.addView(btn("🎵 Escolher música") { pickMusic.launch(arrayOf("audio/*")) }, lpm(WRAP, WRAP, t = 6, r = 6))
    if (mu != null) {
        r.addView(btn("Remover") {
            pushUndo()
            musicUri = null
            showTool(currentTool)
        }, lpm(WRAP, WRAP, t = 6))
    }
    box.addView(hscroll(r))
    if (mu != null) {
        labeledSeek(box, { "Volume da música: $it%" }, 0, 200, musicVol, { musicVol = it }, { pushUndo() }, { scheduleSave() })
    }

    box.addView(section("Narração"), lpm(WRAP, WRAP, t = 14))
    val vu = voiceUri
    if (recording) {
        box.addView(tv("● Gravando… fale agora. O vídeo toca do início.", 13f, Color.parseColor("#FF5C7A")), lpm(WRAP, WRAP, t = 4))
    } else {
        box.addView(
            tv(if (vu == null) "Nenhuma narração gravada." else "Narração gravada (${fmt(voiceDurMs)})", 13f),
            lpm(WRAP, WRAP, t = 4)
        )
    }
    val r2 = hbox()
    val rec = btn(if (recording) "⏹ Parar gravação" else "🎙 Gravar narração") {
        if (recording) stopRecording() else requestRecord()
    }
    styleBtn(rec, recording)
    r2.addView(rec, lpm(WRAP, WRAP, t = 6, r = 6))
    if (vu != null && !recording) {
        r2.addView(btn("Remover") {
            pushUndo()
            voiceUri = null
            showTool(currentTool)
        }, lpm(WRAP, WRAP, t = 6))
    }
    box.addView(hscroll(r2))
    if (vu != null && !recording) {
        labeledSeek(box, { "Volume da narração: $it%" }, 0, 200, voiceVol, { voiceVol = it }, { pushUndo() }, { scheduleSave() })
    }
    box.addView(
        note("Música e narração entram no começo do vídeo e são cortadas no fim. Aparecem só no vídeo exportado."),
        lpm(MATCH, WRAP, t = 10)
    )
    return box
}

internal fun MainActivity.panelTransition(): View {
    val box = vbox()
    box.addView(tv("Transição entre todos os clipes."))
    box.addView(section("Efeito"), lpm(WRAP, WRAP, t = 8))
    box.addView(chipRow(transNames, transType) { i ->
        pushUndo()
        transType = i
        showTool(currentTool)
    })
    box.addView(section("Duração"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipRow(transDurNames, transIdx) { i ->
        pushUndo()
        transIdx = i
        showTool(currentTool)
    })
    box.addView(
        note("Aparece só no vídeo exportado e precisa de 2 ou mais clipes. A bolinha na linha do tempo marca cada corte."),
        lpm(MATCH, WRAP, t = 8)
    )
    return box
}

internal fun MainActivity.panelFormat(): View {
    val box = vbox()
    box.addView(section("Proporção"))
    box.addView(chipRow(aspectNames, aspectIdx) { i ->
        pushUndo()
        aspectIdx = i
        showTool(currentTool)
    })
    box.addView(note("16:9 YouTube · 9:16 Reels/Shorts/TikTok · 4:5 feed"))
    box.addView(section("Enquadramento"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipRow(fitNames, fitIdx) { i ->
        pushUndo()
        fitIdx = i
        showTool(currentTool)
    })
    box.addView(section("Fade de abertura"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipRow(fadeNames, fadeInIdx) { i ->
        pushUndo()
        fadeInIdx = i
        showTool(currentTool)
    })
    box.addView(section("Fade final"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipRow(fadeNames, fadeOutIdx) { i ->
        pushUndo()
        fadeOutIdx = i
        showTool(currentTool)
    })
    val r = hbox()
    r.addView(btn("Nome e proporção…") { projectDialog(false) }, lpm(WRAP, WRAP, t = 16, r = 6))
    r.addView(btn("Novo projeto") { projectDialog(true) }, lpm(WRAP, WRAP, t = 16, r = 6))
    r.addView(btn("Tela inicial") { goHome() }, lpm(WRAP, WRAP, t = 16))
    box.addView(hscroll(r))
    box.addView(note("Resolução e qualidade ficam em Exportar."), lpm(MATCH, WRAP, t = 8))
    return box
}

internal fun MainActivity.itemDialog(index: Int, sticker: Boolean, newText: String = "") {
    val existing = texts.getOrNull(index)
    var pos = existing?.pos ?: (if (sticker) 1 else 2)
    var posX = existing?.posX ?: 1
    var size = existing?.size ?: 1
    var col = colorValues.indexOf(existing?.color ?: Color.WHITE).coerceAtLeast(0)
    val now = currentTimelineMs()
    val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

    fun field(h: String, value: String, numeric: Boolean) = EditText(this).apply {
        hint = h
        setText(value)
        if (numeric) inputType = numType
        setTextColor(cText)
        setHintTextColor(cGrey)
        backgroundTintList = ColorStateList.valueOf(cAccent)
    }

    val et = field("Texto", existing?.text ?: "", false)
    val st = field("Início (s)", "%.1f".format((existing?.startMs ?: now) / 1000.0), true)
    val en = field("Fim (s)", "%.1f".format((existing?.endMs ?: (now + 3000L)) / 1000.0), true)

    val bPos = btn("Vertical: ${posNames[pos]}") {}
    bPos.setOnClickListener {
        pos = (pos + 1) % posNames.size
        bPos.text = "Vertical: ${posNames[pos]}"
    }
    val bPosX = btn("Horizontal: ${posXNames[posX]}") {}
    bPosX.setOnClickListener {
        posX = (posX + 1) % posXNames.size
        bPosX.text = "Horizontal: ${posXNames[posX]}"
    }
    val bSize = btn("Tamanho: ${sizeNames[size]}") {}
    bSize.setOnClickListener {
        size = (size + 1) % sizeNames.size
        bSize.text = "Tamanho: ${sizeNames[size]}"
    }
    val bCol = btn("Cor: ${colorNames[col]}") {}
    bCol.setOnClickListener {
        col = (col + 1) % colorNames.size
        bCol.text = "Cor: ${colorNames[col]}"
    }

    val box = vbox().apply { setPadding(dp(20), dp(12), dp(20), 0) }
    if (!sticker) box.addView(et)
    box.addView(st)
    box.addView(en)
    box.addView(bPos, lpm(MATCH, WRAP, t = 8))
    box.addView(bPosX, lpm(MATCH, WRAP, t = 6))
    box.addView(bSize, lpm(MATCH, WRAP, t = 6))
    if (!sticker) box.addView(bCol, lpm(MATCH, WRAP, t = 6))

    showStyled(
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) (if (sticker) "Novo adesivo" else "Novo texto") else "Editar")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("OK") { _, _ ->
                val txt = if (sticker) (existing?.text ?: newText) else et.text.toString().trim()
                if (txt.isNotEmpty()) {
                    val s = parseMs(st.text.toString()) ?: 0L
                    var e = parseMs(en.text.toString()) ?: (s + 3000L)
                    if (e <= s) e = s + 1000L
                    pushUndo()
                    val item = TextItem(txt, s, e, pos, size, colorValues[col], posX, sticker)
                    if (existing == null) texts.add(item) else texts[index] = item
                    overlayKey = ""
                    showTool(currentTool)
                }
            }
            .setNegativeButton("Cancelar", null)
    )
}
