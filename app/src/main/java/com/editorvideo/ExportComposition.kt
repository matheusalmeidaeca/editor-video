package com.editorvideo

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import kotlin.math.min

// Montagem da composição de exportação (saiu do MainActivity na v3.2.5).

/** Posições e tamanhos do texto e dos adesivos. A prévia e a exportação leem daqui, para casar. */
internal object TextLayout {
    val posY = listOf(0.8f, 0f, -0.8f)
    val posXv = listOf(-0.6f, 0f, 0.6f)
    val sizeFactor = listOf(0.045f, 0.07f, 0.10f)
    val stickerFactor = listOf(0.09f, 0.15f, 0.24f)
}

/** Música ou narração que entra como trilha de áudio extra. */
internal class ExtraAudio(
    val uri: Uri,
    val durMs: Long,
    val vol: Int,
    val fadeInMs: Long = 0L,    // fade da música (v3.3.12); ignorado com repetição
    val fadeOutMs: Long = 0L,
    val loop: Boolean = false   // repete a música até o fim do vídeo
)

/** Tudo o que a exportação precisa saber além dos clipes e dos textos. */
internal class ExportSettings(
    val aspectRatio: Float?,
    val shortSide: Int?,
    val cropToFill: Boolean,
    val muteOriginal: Boolean,
    val fadeInMs: Long,
    val fadeOutMs: Long,
    val transType: Int,   // 0 nenhuma, 1 preto, 2 branco
    val transHalfMs: Long,
    val music: ExtraAudio?,
    val voice: ExtraAudio?,
    val maxFps: Int = 0,   // 0 = manter a original; senão limita (só reduz, nunca aumenta)
    val logo: Bitmap? = null,   // imagem do logo já lida (v3.3.12)
    val logoPos: Int = 3,
    val logoSize: Int = 1,
    val logoAlpha: Int = 255
)

internal object ExportComposition {

    fun outputSize(clips: List<Clip>, s: ExportSettings): Pair<Int, Int> {
        val first = clips.first()
        return TimelineMath.outputSize(first.w, first.h, first.rotation, s.aspectRatio, s.shortSide)
    }

    private fun audioItem(a: ExtraAudio, totalMs: Long): EditedMediaItem {
        val cb = MediaItem.ClippingConfiguration.Builder()
        // com repetição a música inteira entra e a faixa é repetida; sem ela, corta no fim do vídeo
        val playMs = if (a.durMs > 0L) (if (a.loop) a.durMs else min(a.durMs, totalMs)) else totalMs
        if (a.durMs > 0L) cb.setEndPositionMs(playMs)
        val media = MediaItem.Builder().setUri(a.uri).setClippingConfiguration(cb.build()).build()
        val ap = ArrayList<AudioProcessor>()
        if (a.vol != 100) ap.add(GainProcessor(a.vol / 100f))
        if (!a.loop && (a.fadeInMs > 0L || a.fadeOutMs > 0L)) ap.add(FadeProcessor(a.fadeInMs, a.fadeOutMs, playMs))
        return EditedMediaItem.Builder(media)
            .setRemoveVideo(true)
            .setEffects(Effects(ap, emptyList<Effect>()))
            .build()
    }

    fun build(clips: List<Clip>, texts: List<TextItem>, s: ExportSettings): Composition {
        val totalMs = clips.sumOf { it.effMs }

        val items = ArrayList<EditedMediaItem>()
        for (c in clips) {
            val v = ArrayList<Effect>(videoEffects(c, true))
            val a = ArrayList<AudioProcessor>()
            if (c.isImage) {
                // foto: duração fixa, sem áudio nem velocidade; 30 fps é o padrão recomendado para imagens
                val mi = MediaItem.Builder().setUri(c.uri).setImageDurationMs(c.endMs - c.startMs).build()
                items.add(
                    EditedMediaItem.Builder(mi)
                        .setFrameRate(30)
                        .setEffects(Effects(a, v))
                        .build()
                )
                continue
            }
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(c.startMs)
                .setEndPositionMs(c.endMs)
                .build()
            val mi = MediaItem.Builder().setUri(c.uri).setClippingConfiguration(clipping).build()
            if (c.speed != 1f) v.add(SpeedChangeEffect(c.speed))
            if (!s.muteOriginal) a.addAll(clipAudioProcessors(c))

            items.add(
                EditedMediaItem.Builder(mi)
                    .setRemoveAudio(s.muteOriginal)
                    .setEffects(Effects(a, v))
                    .build()
            )
        }

        val (ow, oh) = outputSize(clips, s)
        val layout = if (s.cropToFill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
        val compEffects = ArrayList<Effect>()
        compEffects.add(Presentation.createForWidthAndHeight(ow, oh, layout))

        val shortOut = min(ow, oh)
        val overlays = ArrayList<TextureOverlay>()
        for (t in texts) {
            val sp = SpannableString(t.text)
            val len = sp.length
            val flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            val factor = if (t.sticker) TextLayout.stickerFactor[t.size] else TextLayout.sizeFactor[t.size]
            sp.setSpan(AbsoluteSizeSpan((shortOut * factor).toInt()), 0, len, flags)
            if (!t.sticker) {
                sp.setSpan(ForegroundColorSpan(t.color), 0, len, flags)
                sp.setSpan(TypefaceSpan(TextStyleKit.typeface(t.font, t.italic)), 0, len, flags)
                val bgColor = TextStyleKit.backgroundColor(t.bg, t.color)
                if (bgColor != null) sp.setSpan(BackgroundColorSpan(bgColor), 0, len, flags)
            }
            val y = TextLayout.posY[t.pos]
            val x = TextLayout.posXv[t.posX]
            val shown = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(x, y).build()
            val hidden = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(x, y).setAlphaScale(0f).build()
            overlays.add(TimedText(t, sp, shown, hidden, x, y))
        }
        // logo/marca d'água: camada do tamanho do quadro, por baixo dos textos
        val logoSrc = s.logo
        if (logoSrc != null) {
            val layer = LogoLayer.compose(logoSrc, ow, oh, s.logoPos, s.logoSize, s.logoAlpha)
            val ll = ArrayList<TextureOverlay>()
            ll.add(StaticLogo(layer))
            compEffects.add(OverlayEffect(ImmutableList.copyOf(ll)))
        }
        if (overlays.isNotEmpty()) compEffects.add(OverlayEffect(ImmutableList.copyOf(overlays)))
        if (s.maxFps > 0) compEffects.add(FrameDropEffect.createDefaultFrameDropEffect(s.maxFps.toFloat()))

        val fi = s.fadeInMs
        val fo = s.fadeOutMs
        val compAudio = ArrayList<AudioProcessor>()
        if (fi > 0L || fo > 0L) {
            // bitmap mínimo: o overlay é esticado ao quadro, então não precisa ter o tamanho do vídeo
            val black = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            black.eraseColor(Color.BLACK)
            val fadeList = ArrayList<TextureOverlay>()
            fadeList.add(FadeOverlay(black, totalMs, fi, fo))
            compEffects.add(OverlayEffect(ImmutableList.copyOf(fadeList)))
            compAudio.add(FadeProcessor(fi, fo, totalMs))
        }

        if (s.transType != 0 && clips.size > 1) {
            val bounds = TimelineMath.boundaries(clips.map { it.effMs })
            val tb = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            tb.eraseColor(if (s.transType == 1) Color.BLACK else Color.WHITE)
            val tl = ArrayList<TextureOverlay>()
            tl.add(TransitionOverlay(tb, bounds, s.transHalfMs))
            compEffects.add(OverlayEffect(ImmutableList.copyOf(tl)))
        }

        val seqs = ArrayList<EditedMediaItemSequence>()
        seqs.add(seqOf(items, clips.any { it.isImage }))
        s.music?.let { seqs.add(seqOf(listOf(audioItem(it, totalMs)), false, it.loop && it.durMs > 0L)) }
        s.voice?.let { seqs.add(seqOf(listOf(audioItem(it, totalMs)))) }

        return Composition.Builder(seqs)
            .setEffects(Effects(compAudio, compEffects))
            .build()
    }
}

/** Monta uma faixa pelo Builder (os construtores diretos são obsoletos desde o Media3 1.5). */
internal fun seqOf(list: List<EditedMediaItem>, forceAudio: Boolean = false, loop: Boolean = false): EditedMediaItemSequence {
    val b = EditedMediaItemSequence.Builder()
    if (loop) b.setIsLooping(true)
    // com fotos na faixa, o primeiro item pode não ter áudio; a Media3 exige a faixa de áudio forçada
    if (forceAudio) b.experimentalSetForceAudioTrack(true)
    for (item in list) b.addItem(item)
    return b.build()
}
