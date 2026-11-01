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

// Tela inicial, lista de projetos e menus (saíram do MainActivity na v3.3.1).

internal fun MainActivity.saveThumb(id: String) {
    val c = clips.firstOrNull() ?: return
    val th = c.thumb ?: return
    val key = id + c.uri
    if (key == thumbSavedFor) return
    try {
        store.thumbFile(id).outputStream().use { th.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        thumbSavedFor = key
    } catch (e: Exception) {
        // sem miniatura na lista, tudo bem
    }
}

internal fun MainActivity.fmtShort(ms: Long): String {
    val t = ms / 1000
    return "%d:%02d".format(t / 60, t % 60)
}

internal fun MainActivity.showHome() {
    onHome = true
    val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val frame = FrameLayout(this)
    frame.addView(LiquidBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))
    val outer = vbox().apply { setPadding(dp(16), dp(14), dp(16), dp(8)) }

    val top = hbox()
    val title = tv("Editor de Vídeo", 22f).apply { setTypeface(typeface, Typeface.BOLD) }
    title.paint.shader = LinearGradient(
        0f, 0f, dp(190).toFloat(), 0f,
        intArrayOf(Color.WHITE, cAccent), null, Shader.TileMode.CLAMP
    )
    top.addView(title, lpm(0, WRAP, weight = 1f))
    top.addView(iconBtn("☰") { homeMenu() }, lpm(dp(42), dp(42)))
    outer.addView(top)

    val novo = btn("＋  Novo projeto") { projectDialog(true) }.apply {
        background = gradient(28)
        setTextColor(cNavy)
        typeface = Typeface.DEFAULT_BOLD
        textSize = 17f
    }
    outer.addView(novo, lpm(MATCH, dp(56), t = 14))
    val atalhos = hbox()
    atalhos.addView(btn("🎞️ Importar vídeo") {
        startNewProject("Meu projeto ${store.list().size + 1}", 0)
    }, lpm(WRAP, WRAP, r = 6))
    outer.addView(hscroll(atalhos), lpm(MATCH, WRAP, t = 8))

    outer.addView(section("Projetos recentes"), lpm(WRAP, WRAP, t = 16, b = 6))
    val lista = store.list()
    val col = vbox()
    if (lista.isEmpty()) {
        col.addView(note("Nenhum projeto ainda. Toque em Novo projeto para começar."))
    } else {
        val cols = if (landscape) 4 else 2
        val data = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
        lista.chunked(cols).forEach { linha ->
            val row = hbox()
            linha.forEach { m ->
                val card = vbox().apply {
                    background = glass(16)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    isClickable = true
                    setOnClickListener { openProject(m.id) }
                    setOnLongClickListener {
                        projectActions(m)
                        true
                    }
                }
                val img = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0x33000000)
                    val tf = store.thumbFile(m.id)
                    if (tf.exists()) setImageBitmap(BitmapFactory.decodeFile(tf.absolutePath))
                }
                card.addView(img, lpm(MATCH, dp(if (landscape) 80 else 104)))
                card.addView(tv(m.name, 14f).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, lpm(MATCH, WRAP, t = 6))
                val n = m.clipCount
                card.addView(
                    note("$n ${if (n == 1) "clipe" else "clipes"} · ${fmtShort(m.totalMs)} · ${data.format(Date(m.updatedMs))}"),
                    lpm(MATCH, WRAP, t = 2)
                )
                press(card)
                row.addView(card, lpm(0, WRAP, r = 8, b = 8, weight = 1f))
            }
            repeat(cols - linha.size) { row.addView(View(this), lpm(0, 1, r = 8, weight = 1f)) }
            col.addView(row)
        }
        col.addView(note("Toque longo no projeto: duplicar, renomear ou excluir."), lpm(MATCH, WRAP, t = 4))
    }
    val scroll = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(col)
    }
    outer.addView(scroll, lpm(MATCH, 0, weight = 1f))
    frame.addView(outer, FrameLayout.LayoutParams(MATCH, MATCH))
    setContentView(frame)
}

internal fun MainActivity.projectActions(m: ProjectMeta) {
    showStyled(
        AlertDialog.Builder(this)
            .setTitle(m.name)
            .setItems(arrayOf("Duplicar", "Renomear", "Excluir")) { _, which ->
                when (which) {
                    0 -> {
                        saveProject()
                        if (store.duplicate(m.id) != null) toast("Projeto duplicado") else toast("Não consegui duplicar")
                        showHome()
                    }
                    1 -> renameProject(m)
                    2 -> confirmDelete(m)
                }
            }
    )
}

internal fun MainActivity.renameProject(m: ProjectMeta) {
    val et = editField("Nome do projeto", m.name)
    val box = vbox().apply { setPadding(dp(20), dp(10), dp(20), 0) }
    box.addView(et)
    showStyled(
        AlertDialog.Builder(this)
            .setTitle("Renomear")
            .setView(box)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) {
                    if (m.id == currentId) {
                        saveProject()
                        projectName = n
                        nameTv.text = n
                    }
                    store.rename(m.id, n)
                    if (m.id == currentId) saveProject()
                    showHome()
                }
            }
            .setNegativeButton("Cancelar", null)
    )
}

internal fun MainActivity.confirmDelete(m: ProjectMeta) {
    showStyled(
        AlertDialog.Builder(this)
            .setTitle("Excluir projeto?")
            .setMessage("“${m.name}” será apagado. Os vídeos originais não são afetados.")
            .setPositiveButton("Excluir") { _, _ ->
                handler.removeCallbacks(saveRunnable)
                store.delete(m.id)
                store.purgeUnreferenced(File(filesDir, "imports"))
                if (m.id == currentId) {
                    currentId = null
                    resetState()
                }
                showHome()
            }
            .setNegativeButton("Cancelar", null)
    )
}

internal fun MainActivity.homeMenu() {
    showStyled(
        AlertDialog.Builder(this)
            .setItems(arrayOf("Novo projeto", "Limpar cache de exportação", "Sobre")) { _, which ->
                when (which) {
                    0 -> projectDialog(true)
                    1 -> clearExportCache()
                    2 -> aboutDialog()
                }
            }
    )
}

internal fun MainActivity.editorMenu() {
    showStyled(
        AlertDialog.Builder(this)
            .setTitle(projectName)
            .setItems(arrayOf("Projetos (tela inicial)", "Nome e proporção…", "Duplicar este projeto", "Salvar agora", "Exportar…")) { _, which ->
                when (which) {
                    0 -> goHome()
                    1 -> projectDialog(false)
                    2 -> {
                        saveProject()
                        val id = currentId
                        if (id != null && store.duplicate(id) != null) toast("Projeto duplicado") else toast("Nada para duplicar ainda")
                    }
                    3 -> {
                        saveProject()
                        toast("Projeto salvo")
                    }
                    4 -> if (exporting) cancelExport() else exportDialog()
                }
            }
    )
}

internal fun MainActivity.clearExportCache() {
    if (exporting) {
        toast("Espere a exportação terminar")
        return
    }
    var bytes = 0L
    cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("saida_") }?.forEach {
        bytes += it.length()
        it.delete()
    }
    toast("Liberados ${(bytes / 1_000_000.0).roundToInt()} MB")
}

internal fun MainActivity.aboutDialog() {
    val v = try {
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (e: Exception) {
        "?"
    }
    showStyled(
        AlertDialog.Builder(this)
            .setTitle("Editor de Vídeo")
            .setMessage("Versão $v\nSem IA, sem conta e sem marca d'água. Seus projetos ficam só neste aparelho.")
            .setPositiveButton("OK", null)
    )
}
