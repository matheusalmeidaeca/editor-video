package com.editorvideo

import android.graphics.Bitmap
import android.net.Uri
import android.text.SpannableString
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import kotlin.math.abs
import kotlin.math.max

// Modelo do projeto e camadas de exportação (saíram do MainActivity na v3.2.4).

internal class Clip(val uri: Uri, val name: String, val durationMs: Long, val w: Int, val h: Int) {
    var startMs = 0L
    var endMs = durationMs
    var speed = 1f
    var rotation = 0
    var flip = false
    var filter = 0
    var brightness = 0
    var contrast = 0
    var sat = 0
    var vol = 100
    var fadeIn = 0   // fade de entrada do áudio do clipe, em ms
    var fadeOut = 0  // fade de saída do áudio do clipe, em ms
    var zoom = 100
    var panX = 0
    var panY = 0
    var ken = 0
    var temp = 0     // temperatura de cor: negativo esfria, positivo esquenta (v3.3.12)
    var tint = 0     // tonalidade: negativo esverdeia, positivo puxa para o magenta (v3.3.12)
    var flipV = false  // espelhado na vertical (v3.3.12)
    var thumb: Bitmap? = null
    var isImage = false  // foto: sem áudio nem velocidade; a duração é endMs - startMs (v3.3.11)

    val effMs: Long get() = if (isImage) endMs - startMs else TimelineMath.effMs(startMs, endMs, speed)

    companion object {
        const val IMAGE_MAX_MS = 60_000L      // limite do controle de duração da foto
        const val IMAGE_DEFAULT_MS = 4_000L   // duração inicial de uma foto na linha do tempo
    }

    fun copy(): Clip {
        val c = Clip(uri, name, durationMs, w, h)
        c.startMs = startMs
        c.endMs = endMs
        c.speed = speed
        c.rotation = rotation
        c.flip = flip
        c.filter = filter
        c.brightness = brightness
        c.contrast = contrast
        c.sat = sat
        c.vol = vol
        c.fadeIn = fadeIn
        c.fadeOut = fadeOut
        c.zoom = zoom
        c.panX = panX
        c.panY = panY
        c.ken = ken
        c.temp = temp
        c.tint = tint
        c.flipV = flipV
        c.thumb = thumb
        c.isImage = isImage
        return c
    }
}

internal class TextItem(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val pos: Int,
    val size: Int,
    val color: Int,
    val posX: Int = 1,
    val sticker: Boolean = false,
    val font: Int = 0,        // índice em TextStyleKit.families (v3.3.12)
    val italic: Boolean = false,
    val bg: Int = 0,          // 0 translúcido (padrão antigo), 1 sem fundo, 2 sólido
    val anim: Int = 0         // 0 nenhuma, 1 fade, 2 pop, 3 subir
)

internal class TimedText(
    val item: TextItem,
    val span: SpannableString,
    val shown: OverlaySettings,
    val hidden: OverlaySettings,
    val x: Float = 0f,
    val y: Float = 0f
) : TextOverlay() {
    override fun getText(presentationTimeUs: Long): SpannableString = span

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = presentationTimeUs / 1000
        if (item.anim == 0) {
            return if (ms >= item.startMs && ms <= item.endMs) shown else hidden
        }
        // com animação: opacidade, escala e deslocamento vertical mudam a cada quadro
        val f = TextAnim.frame(item.anim, ms, item.startMs, item.endMs)
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(x, (y + f[2]).coerceIn(-1f, 1f))
            .setAlphaScale(f[0])
            .setScale(f[1], f[1])
            .build()
    }
}

/** Camada preta de tela cheia cuja opacidade controla o fade de abertura e de final. */
internal class FadeOverlay(
    val bmp: Bitmap,
    val totalMs: Long,
    val inMs: Long,
    val outMs: Long
) : BitmapOverlay() {
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bmp

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = presentationTimeUs / 1000
        var a = 0f
        if (inMs > 0L && ms < inMs) a = 1f - ms.toFloat() / inMs
        if (outMs > 0L && ms > totalMs - outMs) {
            a = max(a, (ms - (totalMs - outMs)).toFloat() / outMs)
        }
        return StaticOverlaySettings.Builder()
            .setScale(1.02f, 1.02f)
            .setAlphaScale(a.coerceIn(0f, 1f))
            .build()
    }
}

/** Transição entre clipes: a tela vai à cor escolhida no corte e volta. */
internal class TransitionOverlay(
    val bmp: Bitmap,
    val bounds: List<Long>,
    val halfMs: Long
) : BitmapOverlay() {
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bmp

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = presentationTimeUs / 1000
        var a = 0f
        for (b in bounds) {
            val dist = abs(ms - b)
            if (dist < halfMs) a = max(a, 1f - dist.toFloat() / halfMs)
        }
        return StaticOverlaySettings.Builder()
            .setScale(1.02f, 1.02f)
            .setAlphaScale(a.coerceIn(0f, 1f))
            .build()
    }
}

internal class Snapshot(
    val clips: List<Clip>,
    val texts: List<TextItem>,
    val selected: Int,
    val musicUri: Uri?,
    val musicName: String,
    val musicDurMs: Long,
    val musicVol: Int,
    val voiceUri: Uri?,
    val voiceDurMs: Long,
    val voiceVol: Int,
    val muteOriginal: Boolean,
    val aspectIdx: Int,
    val qualityIdx: Int,
    val fitIdx: Int,
    val fadeInIdx: Int,
    val fadeOutIdx: Int,
    val transType: Int,
    val transIdx: Int,
    val logoUri: Uri? = null,
    val logoPos: Int = 3,
    val logoSize: Int = 1,
    val logoAlpha: Int = 0,
    val musicFadeIn: Int = 0,
    val musicFadeOut: Int = 0,
    val musicLoop: Boolean = false
)
