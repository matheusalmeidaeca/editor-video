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

// Diálogos do projeto e da exportação e o menu radial (saíram do MainActivity na v3.3.1).

internal fun MainActivity.projectDialog(isNew: Boolean) {
    var asp = if (isNew) 0 else aspectIdx
    val et = editField("Nome do projeto", if (isNew) "Meu projeto ${store.list().size + 1}" else projectName)
    val box = vbox().apply { setPadding(dp(20), dp(10), dp(20), 0) }
    box.addView(et)
    box.addView(section("Proporção da tela"), lpm(WRAP, WRAP, t = 14))
    box.addView(chipGroup(aspectNames, asp) { asp = it })
    box.addView(note("16:9 YouTube · 9:16 Reels/Shorts/TikTok · 1:1 e 4:5 Instagram"), lpm(MATCH, WRAP, t = 8))
    showStyled(
        AlertDialog.Builder(this)
            .setTitle(if (isNew) "Novo projeto" else "Projeto")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(if (isNew) "Criar" else "OK") { _, _ ->
                val n = et.text.toString().trim()
                val nm = if (n.isEmpty()) "Meu projeto" else n
                if (isNew) {
                    startNewProject(nm, asp)
                } else {
                    if (asp != aspectIdx) pushUndo()
                    aspectIdx = asp
                    projectName = nm
                    nameTv.text = projectName
                    scheduleSave()
                    showTool(currentTool)
                }
            }
            .setNegativeButton("Cancelar", null)
    )
}

/** Avisa se o aparelho não tem codificador para o formato e tamanho escolhidos. */
internal fun MainActivity.encoderWarning(w: Int, h: Int): String? {
    val mime = if (codecIdx == 1) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
    return try {
        val ok = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            val type = info.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }
            info.isEncoder && type != null &&
                info.getCapabilitiesForType(type).videoCapabilities?.isSizeSupported(w, h) == true
        }
        if (ok) null
        else "Este aparelho pode não codificar ${w}×${h} neste formato. Use uma resolução menor ou H.264."
    } catch (e: Exception) {
        null
    }
}

internal fun MainActivity.exportDialog() {
    if (clips.isEmpty()) {
        toast("Adicione vídeos primeiro")
        return
    }
    val box = vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
    val info = tv("", 12f, cGrey)
    fun upd() {
        val (ow, oh) = outputSize()
        val br = videoBitrate(ow, oh)
        val mb = br / 8.0 * (totalMs() / 1000.0) / 1_000_000.0
        val base = "${ow}×${oh} · ${"%.1f".format(br / 1_000_000.0)} Mbps · cerca de ${mb.roundToInt().coerceAtLeast(1)} MB"
        val warn = encoderWarning(ow, oh)
            ?: if (max(ow, oh) > 2000) "Acima de 1080p pode ficar lento e esquentar o aparelho." else null
        info.text = if (warn != null) base + "\n" + warn else base
    }
    var dlg: AlertDialog? = null
    box.addView(section("Predefinição"))
    box.addView(chipGroup(ExportPresets.all.map { it.name }, -1) { i ->
        val p = ExportPresets.all[i]
        codecIdx = p.codecIdx
        qualityIdx = p.qualityIdx
        bitrateIdx = p.bitrateIdx
        toast(p.hint)
        dlg?.dismiss()
        exportDialog()
    })
    box.addView(section("Formato"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipGroup(listOf("Compatível (H.264)", "Menor arquivo (HEVC)"), codecIdx) { codecIdx = it; upd() })
    box.addView(section("Resolução"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipGroup(qualityNames, qualityIdx) { qualityIdx = it; upd() })
    box.addView(section("Qualidade"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipGroup(listOf("Baixa", "Média", "Alta"), bitrateIdx) { bitrateIdx = it; upd() })
    box.addView(section("Quadros por segundo"), lpm(WRAP, WRAP, t = 12))
    box.addView(chipGroup(ExportHelpers.fpsNames, fpsIdx) { fpsIdx = it })
    box.addView(tv("Só reduz: um vídeo de 24 fps continua em 24. Menos quadros deixam o arquivo menor.", 11f, cGrey), lpm(WRAP, WRAP, t = 4))
    box.addView(info, lpm(WRAP, WRAP, t = 14))
    upd()
    dlg = showStyled(
        AlertDialog.Builder(this)
            .setTitle("Salvar e compartilhar")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Salvar como vídeo") { _, _ -> askNotifThenExport() }
            .setNegativeButton("Cancelar", null)
    )
}

internal fun MainActivity.showRadialMenu() {
    val scrim = FrameLayout(this).apply {
        setBackgroundColor(Color.parseColor("#D9050B18"))
        isClickable = true
        alpha = 0f
    }
    fun close() {
        scrim.animate().alpha(0f).setDuration(160).withEndAction { rootFrame.removeView(scrim) }.start()
    }
    scrim.setOnClickListener { close() }
    val items: List<Triple<String, String, () -> Unit>> = listOf(
        Triple("🖼️", "Galeria", { openGallery() }),
        Triple("🎞️", "Arquivos", { pickVideos.launch(arrayOf("video/*")) }),
        Triple("🔤", "Texto", {
            currentTool = 5
            buildToolTabs()
            showTool(5, true)
            itemDialog(-1, false)
        }),
        Triple("🎵", "Áudio", { pickMusic.launch(arrayOf("audio/*")) }),
        Triple("🎙️", "REC", { requestRecord() })
    )
    val radius = dp(100).toFloat()
    items.forEachIndexed { i, it3 ->
        val ang = Math.toRadians(-90.0 + i * 360.0 / items.size)
        val dx = (radius * Math.cos(ang)).toFloat()
        val dy = (radius * Math.sin(ang)).toFloat()
        val b = vbox().apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
                setStroke(dp(1), cAccent)
            }
            scaleX = 0f
            scaleY = 0f
            setOnClickListener {
                close()
                it3.third()
            }
        }
        b.addView(tv(it3.first, 26f).apply { gravity = Gravity.CENTER })
        b.addView(tv(it3.second, 11f).apply { gravity = Gravity.CENTER })
        scrim.addView(b, FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        b.animate().translationX(dx).translationY(dy).scaleX(1f).scaleY(1f)
            .setStartDelay(i * 45L).setDuration(380)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
    }
    val closeBtn = TextView(this).apply {
        text = "✕"
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(cNavy)
        background = gradientOval()
        setOnClickListener { close() }
    }
    scrim.addView(closeBtn, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
    rootFrame.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
    scrim.animate().alpha(1f).setDuration(160).start()
}
