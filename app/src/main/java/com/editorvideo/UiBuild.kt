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

// Montagem da tela do editor e das abas de ferramentas (saiu do MainActivity na v3.3.2).

internal fun MainActivity.buildUi() {
    val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    toolSheet = null
    val frame = FrameLayout(this)
    rootFrame = frame
    frame.addView(LiquidBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

    // barra superior
    val top = hbox()
    val titleBox = vbox()
    val title = tv("Editor de Vídeo", 18f).apply {
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    title.paint.shader = LinearGradient(
        0f, 0f, dp(170).toFloat(), 0f,
        intArrayOf(Color.WHITE, cAccent), null, Shader.TileMode.CLAMP
    )
    nameTv = tv(projectName, 11f, cGrey).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setOnClickListener { projectDialog(false) }
    }
    titleBox.addView(title)
    titleBox.addView(nameTv)
    top.addView(iconBtn("☰") { editorMenu() }, lpm(dp(42), dp(42), r = 8))
    top.addView(titleBox, lpm(0, WRAP, weight = 1f))
    undoBtn = iconBtn("↩") { undo() }
    redoBtn = iconBtn("↪") { redo() }
    top.addView(undoBtn, lpm(dp(42), dp(42), l = 4))
    top.addView(redoBtn, lpm(dp(42), dp(42), l = 4))
    top.addView(iconBtn("+") { showRadialMenu() }, lpm(dp(42), dp(42), l = 4))
    exportBtn = btn("Exportar") { if (exporting) cancelExport() else exportDialog() }
    exportBtn.background = gradient(21)
    exportBtn.setTextColor(cNavy)
    exportBtn.typeface = Typeface.DEFAULT_BOLD
    top.addView(exportBtn, lpm(WRAP, dp(42), l = 6))

    progressBar = LiquidProgress(this).apply { visibility = if (exporting) View.VISIBLE else View.GONE }
    statusTv = tv("", 12f, cGrey)

    // prévia
    previewBox = FrameLayout(this).apply { background = glass(22, Color.BLACK, 0x33FFFFFF) }
    surface = SurfaceView(this)
    previewBox.addView(surface, FrameLayout.LayoutParams(MATCH, MATCH))
    overlayLayer = FrameLayout(this)
    previewBox.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

    emptyState = vbox().apply { gravity = Gravity.CENTER }
    val plus = TextView(this).apply {
        text = "+"
        textSize = 36f
        gravity = Gravity.CENTER
        setTextColor(cNavy)
        setTypeface(typeface, Typeface.BOLD)
        background = gradientOval()
        setOnClickListener { pickVideos.launch(arrayOf("video/*")) }
    }
    press(plus)
    emptyState.addView(plus, lpm(dp(84), dp(84)))
    emptyState.addView(tv("Toque para adicionar vídeos", 15f), lpm(WRAP, WRAP, t = 12))
    emptyState.addView(tv("Eles ficam guardados neste projeto", 12f, cGrey), lpm(WRAP, WRAP, t = 2))
    previewBox.addView(emptyState, FrameLayout.LayoutParams(MATCH, MATCH))
    pulse = ObjectAnimator.ofPropertyValuesHolder(
        plus,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.09f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.09f)
    ).apply {
        duration = 1500
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        start()
    }
    player.setVideoSurfaceView(surface)

    // transporte
    val transport = hbox()
    playBtn = btn("▶") { togglePlay() }
    playBtn.background = gradientOval()
    playBtn.setTextColor(cNavy)
    playBtn.setPadding(0, 0, 0, 0)
    transport.addView(playBtn, lpm(dp(46), dp(46)))
    timeTv = tv("0:00.0 / 0:00.0", 13f, cGrey)
    transport.addView(timeTv, lpm(0, WRAP, l = 12, weight = 1f))

    // linha do tempo
    timeline = TimelineView(this).apply {
        onSeek = { ms ->
            seekToTimeline(ms)
            timeTv.text = "${fmt(ms)} / ${fmt(totalMs())}"
        }
        onDrag = { on ->
            dragging = on
            if (!on && clips.isNotEmpty()) selectOnly(player.currentMediaItemIndex)
        }
        onSelect = { i -> selectClip(i) }
    }

    val actions = hbox()
    actions.addView(btn("✂ Dividir") { splitClip() }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("◧ Cortar à esquerda") { cutAtPlayhead(true) }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("Cortar à direita ◨") { cutAtPlayhead(false) }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("◂ 1 quadro") { stepFrame(-33L) }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("1 quadro ▸") { stepFrame(33L) }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("❄ Congelar quadro") { freezeFrame() }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("📷 Quadro → foto") { saveFrameAsPhoto() }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("⏮ Início") { goStart() }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("◁ Clipe") { jumpClip(-1) }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("Clipe ▷") { jumpClip(1) }, lpm(WRAP, WRAP, r = 6))
    actions.addView(btn("Fim ⏭") { goEnd() }, lpm(WRAP, WRAP, r = 6))
    // ações do clipe: só aparecem depois de tocar num clipe da linha do tempo (v3.2.9)
    val grupo = hbox()
    grupo.addView(btn("⧉ Duplicar") { dupClip() }, lpm(WRAP, WRAP, r = 6))
    grupo.addView(btn("🗑 Excluir") { delClip() }, lpm(WRAP, WRAP, r = 6))
    grupo.addView(btn("◀ Mover") { moveClip(-1) }, lpm(WRAP, WRAP, r = 6))
    grupo.addView(btn("Mover ▶") { moveClip(1) }, lpm(WRAP, WRAP, r = 6))
    grupo.visibility = View.GONE
    clipGroup = grupo
    actions.addView(grupo, lpm(WRAP, WRAP))

    toolsRow = hbox()
    panel = vbox().apply {
        background = glass(22, 0x14FFFFFF, 0x26FFFFFF)
        setPadding(dp(16), dp(14), dp(16), dp(16))
    }

    val animRoot: ViewGroup
    if (!landscape) {
        val root = vbox().apply { setPadding(dp(14), dp(8), dp(14), dp(16)) }
        root.addView(top)
        root.addView(progressBar, lpm(MATCH, dp(10), t = 8))
        root.addView(statusTv)
        val ph = (resources.displayMetrics.heightPixels * 0.34f).toInt().coerceIn(dp(190), dp(340))
        root.addView(previewBox, lpm(MATCH, ph, t = 10))
        root.addView(transport, lpm(MATCH, WRAP, t = 10))
        root.addView(timeline, lpm(MATCH, dp(160), t = 8))
        root.addView(hscroll(actions), lpm(MATCH, WRAP, t = 8))
        root.addView(hscroll(toolsRow), lpm(MATCH, WRAP, t = 14))
        root.addView(panel, lpm(MATCH, WRAP, t = 12))
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root)
        }
        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        animRoot = root
    } else {
        // v3.3.9: layout de paisagem no estilo do PowerDirector: barra à esquerda
        // (voltar, biblioteca, texto, desfazer, refazer, excluir), prévia e linha do tempo
        // no centro, faixa de ferramentas embaixo e barra à direita (exportar, menu, play).
        top.removeAllViews()
        titleBox.removeAllViews()
        transport.removeAllViews()
        val root = hbox().apply {
            gravity = Gravity.TOP
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        fun bar() = vbox().apply {
            background = glass(18)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        val leftBar = bar()
        leftBar.addView(iconBtn("←") { goHome() }, lpm(dp(42), dp(42), b = 6))
        leftBar.addView(iconBtn("🎞") { showMediaLibrary() }, lpm(dp(42), dp(42), b = 6))
        leftBar.addView(iconBtn("🔤") {
            currentTool = 5
            buildToolTabs()
            showTool(5, true)
            openToolSheet()
        }, lpm(dp(42), dp(42), b = 6))
        leftBar.addView(undoBtn, lpm(dp(42), dp(42), b = 6))
        leftBar.addView(redoBtn, lpm(dp(42), dp(42), b = 6))
        leftBar.addView(View(this), lpm(dp(1), 0, weight = 1f))
        leftBar.addView(iconBtn("🗑") { delClip() }, lpm(dp(42), dp(42)))

        val rightBar = bar()
        exportBtn.textSize = 11f
        exportBtn.setPadding(0, 0, 0, 0)
        rightBar.addView(exportBtn, lpm(dp(54), dp(42), b = 6))
        rightBar.addView(iconBtn("⋯") { editorMenu() }, lpm(dp(42), dp(42)))
        rightBar.addView(View(this), lpm(dp(1), 0, weight = 1f))
        rightBar.addView(playBtn, lpm(dp(46), dp(46)))

        // centro: prévia, estado, nome/tempo, linha do tempo e faixa de ferramentas
        val center = vbox()
        center.addView(previewBox, lpm(MATCH, 0, weight = 1f))
        center.addView(progressBar, lpm(MATCH, dp(6), t = 3))
        center.addView(statusTv)
        val info = hbox()
        info.addView(nameTv, lpm(0, WRAP, weight = 1f))
        info.addView(timeTv)
        center.addView(info, lpm(MATCH, WRAP, t = 2))
        center.addView(timeline, lpm(MATCH, dp(96), t = 3))
        val dividir = actions.getChildAt(0)
        actions.removeView(dividir)
        val strip = hbox()
        strip.addView(dividir, lpm(WRAP, WRAP, r = 6))
        strip.addView(toolsRow)
        strip.addView(actions)
        center.addView(hscroll(strip), lpm(MATCH, WRAP, t = 4))

        // as opções da ferramenta abrem numa folha por cima da linha do tempo
        val sheetH = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        val sheet = vbox().apply {
            background = glass(22, Color.parseColor("#F2071428"), 0x33FFFFFF)
            setPadding(dp(10), dp(6), dp(10), dp(8))
            visibility = View.GONE
            isClickable = true
        }
        val sheetTop = hbox()
        sheetTop.addView(iconBtn("⌄") { sheet.visibility = View.GONE }, lpm(dp(42), dp(42)))
        sheetTop.addView(tv("Opções da ferramenta", 13f, cGrey), lpm(WRAP, WRAP, l = 8))
        sheet.addView(sheetTop)
        sheet.addView(
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                addView(panel)
            },
            lpm(MATCH, 0, t = 4, weight = 1f)
        )
        toolSheet = sheet
        val centerFrame = FrameLayout(this)
        centerFrame.addView(center, FrameLayout.LayoutParams(MATCH, MATCH))
        centerFrame.addView(sheet, FrameLayout.LayoutParams(MATCH, sheetH, Gravity.BOTTOM))

        root.addView(leftBar, lpm(dp(54), MATCH))
        root.addView(centerFrame, lpm(0, MATCH, l = 6, r = 6, weight = 1f))
        root.addView(rightBar, lpm(dp(54), MATCH))
        frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        animRoot = root
    }
    applySystemInsets(frame)
    setContentView(frame)

    buildToolTabs()
    refreshTimeline()
    showTool(currentTool, false)
    updateUndoButtons()
    updateSeekMax()
    fitSurface(player.videoSize)
    exportBtn.text = if (exporting) "Cancelar" else "Exportar"

    for (k in 0 until animRoot.childCount) {
        val ch = animRoot.getChildAt(k)
        ch.alpha = 0f
        ch.translationY = dp(18).toFloat()
        ch.animate().alpha(1f).translationY(0f).setStartDelay(k * 70L).setDuration(420)
            .setInterpolator(DecelerateInterpolator()).start()
    }
}

internal fun MainActivity.buildToolTabs() {
    toolsRow.removeAllViews()
    toolNames.forEachIndexed { i, n ->
        val on = i == currentTool
        val tab = vbox().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = if (on) gradient(16) else glass(16)
        }
        tab.addView(tv(toolIcons[i], 20f).apply { gravity = Gravity.CENTER })
        tab.addView(tv(n, 11f, if (on) cNavy else cText).apply {
            if (on) setTypeface(typeface, Typeface.BOLD)
        })
        tab.setOnClickListener {
            currentTool = i
            buildToolTabs()
            showTool(i, true)
            openToolSheet()
        }
        press(tab)
        toolsRow.addView(tab, lpm(WRAP, WRAP, r = 8))
    }
}

internal fun MainActivity.showTool(i: Int, animate: Boolean = false) {
    refreshTimeline()
    panel.removeAllViews()
    val v = when (i) {
        0 -> panelTrim()
        1 -> panelSpeed()
        2 -> panelFilters()
        3 -> panelTransform()
        4 -> panelTransition()
        5 -> panelText(false)
        6 -> panelText(true)
        7 -> panelAudio()
        9 -> panelLogo()
        else -> panelFormat()
    }
    panel.addView(v)
    if (animate) {
        panel.alpha = 0f
        panel.translationY = dp(12).toFloat()
        panel.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
    }
}

/** Na paisagem, mostra a folha de opções da ferramenta; no retrato não faz nada. */
internal fun MainActivity.openToolSheet() {
    toolSheet?.visibility = View.VISIBLE
}
