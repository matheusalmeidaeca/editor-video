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
import android.media.ExifInterface
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

// Importação de vídeos: seletor, Compartilhar e cópia para imports/ (saiu do MainActivity na v3.3.2).

internal fun MainActivity.addClipsFrom(uris: List<Uri>) {
    if (uris.isEmpty()) return
    pushUndo()
    val firstNew = clips.size
    uris.forEach {
        keepAccess(it)
        addClip(it)
    }
    if (clips.size > firstNew) {
        selected = firstNew
        afterEdit(firstNew, 0L)
    }
}

/** Vídeos enviados por "Compartilhar" de outro app viram um projeto novo. */
@Suppress("DEPRECATION")
internal fun MainActivity.handleShare(i: Intent?) {
    if (i == null) return
    if ((i.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
    val uris = ArrayList<Uri>()
    when (i.action) {
        Intent.ACTION_SEND -> {
            val u = if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (u != null) uris.add(u)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val l = if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (l != null) uris.addAll(l)
        }
        else -> return
    }
    setIntent(Intent(this, MainActivity::class.java))
    if (uris.isNotEmpty()) importShared(uris)
}

/**
 * O acesso a um vídeo compartilhado é temporário, então o app guarda uma cópia em `imports/`
 * para o projeto continuar abrindo depois. Ocupa espaço; a cópia some quando o projeto é excluído.
 */
internal fun MainActivity.importShared(uris: List<Uri>) {
    startNewProject("Compartilhado ${store.list().size + 1}", 0, false)
    copyAndAdd(uris)
}

/** Copia os vídeos para `imports/` em segundo plano e os adiciona ao projeto aberto. */
internal fun MainActivity.copyAndAdd(uris: List<Uri>) {
    toast("Importando ${uris.size} arquivo(s)…")
    val dir = File(filesDir, "imports").apply { mkdirs() }
    val alvo = currentId
    bgExec.execute {
        val copias = ArrayList<Uri>()
        uris.forEachIndexed { n, u ->
            try {
                val nome = queryName(u).replace(Regex("[^A-Za-z0-9._-]"), "_")
                val dest = File(dir, "${System.currentTimeMillis()}${n}_$nome")
                contentResolver.openInputStream(u)?.use { ins -> dest.outputStream().use { ins.copyTo(it) } }
                if (dest.length() > 0L) copias.add(Uri.fromFile(dest))
            } catch (e: Exception) {
                // esse vídeo ficou de fora
            }
        }
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            if (copias.isEmpty()) {
                toast("Não consegui importar o arquivo")
            } else if (currentId != alvo) {
                toast("Importação terminou, mas você já mudou de projeto")
            } else {
                addClipsFrom(copias)
            }
        }
    }
}

/** Foto ou vídeo? Usa o tipo informado pelo sistema e, sem ele (arquivo copiado), a extensão. */
internal fun MainActivity.isImageUri(uri: Uri): Boolean {
    val t = try {
        contentResolver.getType(uri)
    } catch (e: Exception) {
        null
    }
    if (t != null) return t.startsWith("image/")
    val ext = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase()
    return ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
}

/** Lê o tamanho de uma foto sem carregá-la inteira. A orientação do EXIF troca largura e altura. */
internal fun MainActivity.imageSize(uri: Uri): Pair<Int, Int> {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    } catch (e: Exception) {
        return Pair(0, 0)
    }
    val ori = try {
        contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val troca = ori == ExifInterface.ORIENTATION_ROTATE_90 || ori == ExifInterface.ORIENTATION_ROTATE_270 ||
        ori == ExifInterface.ORIENTATION_TRANSPOSE || ori == ExifInterface.ORIENTATION_TRANSVERSE
    return if (troca) Pair(o.outHeight, o.outWidth) else Pair(o.outWidth, o.outHeight)
}

internal fun MainActivity.addClip(uri: Uri) {
    if (isImageUri(uri)) {
        val (iw, ih) = imageSize(uri)
        if (iw <= 0 || ih <= 0) {
            toast("Não consegui abrir essa foto")
            return
        }
        val c = Clip(uri, queryName(uri), Clip.IMAGE_MAX_MS, iw, ih)
        c.isImage = true
        c.endMs = Clip.IMAGE_DEFAULT_MS
        clips.add(c)
        loadThumb(c)
        return
    }
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(this, uri)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) {
            val t = w
            w = h
            h = t
        }
        if (dur <= 0L || w <= 0 || h <= 0) {
            toast("Não consegui ler: ${queryName(uri)}")
            return
        }
        val c = Clip(uri, queryName(uri), dur, w, h)
        clips.add(c)
        loadThumb(c)
    } catch (e: Exception) {
        toast("Não consegui abrir esse vídeo")
    } finally {
        r.release()
    }
}

internal fun MainActivity.loadThumb(c: Clip) {
    if (c.isImage) {
        bgExec.execute {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(c.uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                val o2 = BitmapFactory.Options().apply { inSampleSize = (o.outHeight / 256).coerceAtLeast(1) }
                val bmp = contentResolver.openInputStream(c.uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                if (bmp != null) {
                    val wTh = (bmp.width * 128f / bmp.height).toInt().coerceAtLeast(1)
                    val th = Bitmap.createScaledBitmap(bmp, wTh, 128, true)
                    runOnUiThread {
                        c.thumb = th
                        refreshTimeline()
                    }
                }
            } catch (e: Exception) {
                // sem miniatura, tudo bem
            }
        }
        return
    }
    bgExec.execute {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(this, c.uri)
            val bmp = r.getFrameAtTime(c.startMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bmp != null) {
                val wTh = (bmp.width * 128f / bmp.height).toInt().coerceAtLeast(1)
                val th = Bitmap.createScaledBitmap(bmp, wTh, 128, true)
                runOnUiThread {
                    c.thumb = th
                    refreshTimeline()
                }
            }
        } catch (e: Exception) {
            // sem miniatura, tudo bem
        } finally {
            r.release()
        }
    }
}

internal fun MainActivity.askNotifThenExport() {
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        exportar()
    }
}
