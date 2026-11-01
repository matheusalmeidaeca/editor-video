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

// Execução da exportação e extração de áudio (saiu do MainActivity na v3.3.1).

internal fun MainActivity.exportar() {
    if (clips.isEmpty()) {
        toast("Adicione vídeos primeiro")
        return
    }
    if (extractingAudio || analysingSilence) {
        toast("Espere a extração ou a análise de áudio terminar.")
        return
    }
    if (recording) stopRecording()
    player.pause()

    val (sw, sh) = outputSize()
    val need = ExportHelpers.estimatedBytes(videoBitrate(sw, sh), totalMs())
    if (!ExportHelpers.hasRoom(cacheDir.usableSpace, need)) {
        toast("Pouco espaço livre. Libere cerca de ${need * 2 / 1_000_000 + 50} MB e tente de novo.")
        return
    }

    val (ow, oh) = outputSize()
    val composition = ExportComposition.build(clips, texts, exportSettings())

    val saida = File(cacheDir, "saida_${System.currentTimeMillis()}.mp4")

    val encoder = DefaultEncoderFactory.Builder(this)
        .setRequestedVideoEncoderSettings(
            VideoEncoderSettings.Builder().setBitrate(videoBitrate(ow, oh)).build()
        )
        .build()
    val t = Transformer.Builder(this)
        .setVideoMimeType(if (codecIdx == 1) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .setEncoderFactory(encoder)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                setExporting(false)
                val uri = salvarNaGaleria(saida)
                ExportService.finish(applicationContext, "Vídeo pronto em Filmes/EditorVideo.", uri)
                if (isDestroyed) return
                statusTv.text = "Pronto! Salvo em Filmes/EditorVideo."
                val fileUri = uri ?: Uri.fromFile(saida)
                showStyled(
                    AlertDialog.Builder(this@exportar)
                        .setTitle("Vídeo exportado")
                        .setMessage("Salvo em Filmes/EditorVideo.")
                        .setPositiveButton("Abrir") { _, _ ->
                            try {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(fileUri, "video/mp4")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            } catch (e: Exception) {
                                toast("Abra pela galeria: Filmes/EditorVideo")
                            }
                        }
                        .setNeutralButton("Compartilhar") { _, _ ->
                            if (uri == null) {
                                toast("Compartilhe pela galeria: Filmes/EditorVideo")
                            } else {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("video/mp4")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                startActivity(Intent.createChooser(send, "Compartilhar vídeo"))
                            }
                        }
                        .setNegativeButton("Fechar", null)
                )
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                setExporting(false)
                ExportService.finish(applicationContext, "A exportação falhou.", null)
                if (isDestroyed) return
                statusTv.text = "Erro ao exportar: " + ExportHelpers.friendlyError(exportException.errorCode)
            }
        })
        .build()
    transformer = t

    statusTv.text = "Exportando…"
    setExporting(true)
    ExportService.onCancel = { runOnUiThread { cancelExport() } }
    try {
        ExportService.start(this)
    } catch (e: Exception) {
        // sem serviço, a exportação ainda roda enquanto o app estiver aberto
    }
    t.start(composition, saida.absolutePath)
}

internal fun MainActivity.setExporting(on: Boolean) {
    exporting = on
    exportBtn.text = if (on) "Cancelar" else "Exportar"
    progressBar.visibility = if (on) View.VISIBLE else View.GONE
    progressBar.progress = 0
}

internal fun MainActivity.cancelExport() {
    transformer?.cancel()
    setExporting(false)
    ExportService.finish(applicationContext, "Exportação cancelada.", null)
    statusTv.text = "Exportação cancelada."
}

/**
 * Salva só o som do clipe (trecho cortado, velocidade, volume e fades do clipe) como .m4a em Música/EditorVideo.
 * Ignora "Remover áudio original": o pedido explícito de extrair vale mais.
 */
internal fun MainActivity.extrairAudio(c: Clip) {
    if (c.isImage) {
        toast("Fotos não têm áudio.")
        return
    }
    if (exporting || extractingAudio) {
        toast("Espere a exportação ou extração em andamento terminar.")
        return
    }
    player.pause()
    val clipping = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(c.startMs)
        .setEndPositionMs(c.endMs)
        .build()
    val mi = MediaItem.Builder().setUri(c.uri).setClippingConfiguration(clipping).build()
    val a = clipAudioProcessors(c)
    val item = EditedMediaItem.Builder(mi)
        .setRemoveVideo(true)
        .setEffects(Effects(a, emptyList<Effect>()))
        .build()

    val base = c.name.substringBeforeLast('.').ifBlank { "audio" }
        .replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
    val saida = File(cacheDir, "${base}_${System.currentTimeMillis()}.m4a")
    val t = Transformer.Builder(this)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                extractingAudio = false
                val ok = salvarAudio(saida)
                if (isDestroyed) return
                statusTv.text = if (ok) "Áudio salvo em Música/EditorVideo." else "Não consegui salvar o áudio."
                toast(statusTv.text.toString())
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                extractingAudio = false
                saida.delete()
                if (isDestroyed) return
                statusTv.text = "Erro ao extrair o áudio: " + ExportHelpers.friendlyError(exportException.errorCode)
            }
        })
        .build()
    extractingAudio = true
    statusTv.text = "Extraindo áudio…"
    try {
        t.start(item, saida.absolutePath)
    } catch (e: Exception) {
        extractingAudio = false
        statusTv.text = "Não consegui iniciar a extração do áudio."
    }
}

internal fun MainActivity.salvarAudio(arquivo: File): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, arquivo.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/EditorVideo")
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            false
        } else {
            contentResolver.openOutputStream(uri)?.use { out ->
                arquivo.inputStream().use { it.copyTo(out) }
            }
            arquivo.delete()
            true
        }
    } catch (e: Exception) {
        false
    }
}

internal fun MainActivity.salvarNaGaleria(arquivo: File): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, arquivo.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/EditorVideo")
    }
    val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    contentResolver.openOutputStream(uri)?.use { out ->
        arquivo.inputStream().use { it.copyTo(out) }
    }
    arquivo.delete()
    return uri
}
