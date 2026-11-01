package com.editorvideo

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import androidx.media3.effect.OverlaySettings
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
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    // ------------------------------------------------------------------ modelo

    private class Clip(val uri: Uri, val name: String, val durationMs: Long, val w: Int, val h: Int) {
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
        var zoom = 100
        var panX = 0
        var panY = 0
        var ken = 0
        var thumb: Bitmap? = null

        val effMs: Long get() = ((endMs - startMs) / speed).toLong()

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
            c.zoom = zoom
            c.panX = panX
            c.panY = panY
            c.ken = ken
            c.thumb = thumb
            return c
        }
    }

    private class TextItem(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val pos: Int,
        val size: Int,
        val color: Int,
        val posX: Int = 1,
        val sticker: Boolean = false
    )

    private class TimedText(
        val item: TextItem,
        val span: SpannableString,
        val shown: OverlaySettings,
        val hidden: OverlaySettings
    ) : TextOverlay() {
        override fun getText(presentationTimeUs: Long): SpannableString = span

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
            val ms = presentationTimeUs / 1000
            return if (ms >= item.startMs && ms <= item.endMs) shown else hidden
        }
    }

    /** Camada preta de tela cheia cuja opacidade controla o fade de abertura e de final. */
    private class FadeOverlay(
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
            return OverlaySettings.Builder()
                .setScale(1.02f, 1.02f)
                .setAlphaScale(a.coerceIn(0f, 1f))
                .build()
        }
    }

    private class Snapshot(
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
        val fadeOutIdx: Int
    )

    // ------------------------------------------------------------------ estado

    private val clips = mutableListOf<Clip>()
    private val texts = mutableListOf<TextItem>()
    private var selected = 0
    private var currentTool = 0
    private var musicUri: Uri? = null
    private var musicName = ""
    private var musicDurMs = 0L
    private var musicVol = 100
    private var voiceUri: Uri? = null
    private var voiceDurMs = 0L
    private var voiceVol = 100
    private var muteOriginal = false
    private var aspectIdx = 0
    private var qualityIdx = 0
    private var fitIdx = 0
    private var fadeInIdx = 0
    private var fadeOutIdx = 0
    private var exporting = false
    private var dragging = false
    private var recording = false
    private var previewFx = true
    private var overlayKey = ""

    private val undoStack = mutableListOf<Snapshot>()
    private val redoStack = mutableListOf<Snapshot>()

    private val aspectNames = listOf("Original", "9:16", "16:9", "1:1", "4:5")
    private val aspectRatios = listOf<Float?>(null, 9f / 16f, 16f / 9f, 1f, 4f / 5f)
    private val qualityNames = listOf("Original", "1080p", "720p", "480p")
    private val qualityShort = listOf<Int?>(null, 1080, 720, 480)
    private val fitNames = listOf("Preencher (corta bordas)", "Ajustar (barras pretas)")
    private val fadeNames = listOf("Sem", "0,5 s", "1 s", "2 s")
    private val fadeMs = listOf(0L, 500L, 1000L, 2000L)
    private val filterNames = listOf(
        "Normal", "P&B", "Vívido", "Quente", "Frio", "Invertido",
        "Sépia", "Cinema", "Vintage", "Dramático", "Noir", "Esmaecido"
    )
    private val kenNames = listOf("Nenhum", "Aproximar", "Afastar", "Panorâmica →", "Panorâmica ←")
    private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
    private val toolNames = listOf("Cortar", "Velocidade", "Filtros", "Transformar", "Texto", "Adesivos", "Áudio", "Formato")
    private val toolIcons = listOf("✂️", "⏩", "🎨", "🔄", "🔤", "😀", "🔊", "📐")
    private val posNames = listOf("Topo", "Centro", "Base")
    private val posXNames = listOf("Esquerda", "Centro", "Direita")
    private val posY = listOf(0.8f, 0f, -0.8f)
    private val posXv = listOf(-0.6f, 0f, 0.6f)
    private val sizeNames = listOf("Pequeno", "Médio", "Grande")
    private val sizeFactor = listOf(0.045f, 0.07f, 0.10f)
    private val stickerFactor = listOf(0.09f, 0.15f, 0.24f)
    private val colorNames = listOf("Branco", "Amarelo", "Vermelho", "Ciano", "Preto")
    private val colorValues = listOf(Color.WHITE, Color.YELLOW, Color.RED, Color.CYAN, Color.BLACK)
    private val emojis = listOf(
        "😀", "😂", "😍", "😎", "🥳", "🔥", "❤️", "⭐", "✨", "🎉",
        "👍", "👏", "💯", "🎵", "🌈", "💥", "👑", "🚀", "🌸", "🐶"
    )

    private val cNavy = Color.parseColor("#04111F")
    private val cText = Color.parseColor("#EAF6FF")
    private val cGrey = Color.parseColor("#8FA3BF")
    private val cAccent = Color.parseColor("#2DE2C8")
    private val cAccent2 = Color.parseColor("#6C9CFF")
    private val cGlass = 0x1AFFFFFF
    private val cGlassStroke = 0x33FFFFFF
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var previewBox: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var overlayLayer: FrameLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var playBtn: Button
    private lateinit var undoBtn: Button
    private lateinit var redoBtn: Button
    private lateinit var timeTv: TextView
    private lateinit var timeSeek: SeekBar
    private lateinit var timelineBox: LinearLayout
    private lateinit var toolsRow: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var exportBtn: Button
    private lateinit var statusTv: TextView
    private lateinit var progressBar: LiquidProgress
    private lateinit var player: ExoPlayer

    private var pulse: ObjectAnimator? = null
    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progressHolder = ProgressHolder()
    private val bgExec = Executors.newSingleThreadExecutor()
    private val saveRunnable = Runnable { saveProject() }

    private val ticker = object : Runnable {
        override fun run() {
            if (clips.isNotEmpty() && player.mediaItemCount == clips.size) {
                val pos = currentTimelineMs()
                if (!dragging) {
                    timeSeek.progress = pos.toInt().coerceAtMost(timeSeek.max)
                    timeTv.text = "${fmt(pos)} / ${fmt(totalMs())}"
                }
                refreshOverlay(pos)
            } else {
                refreshOverlay(-1L)
            }
            if (exporting) {
                val t = transformer
                if (t != null && t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progressBar.progress = progressHolder.progress
                    statusTv.text = "Exportando… ${progressHolder.progress}%"
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    private val pickVideos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
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
        }

    private val pickMusic =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(this, uri)
                    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    pushUndo()
                    keepAccess(uri)
                    musicDurMs = d
                    musicUri = uri
                    musicName = queryName(uri)
                } catch (e: Exception) {
                    toast("Não consegui ler esse áudio")
                } finally {
                    r.release()
                }
                showTool(currentTool)
            }
        }

    private val askMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startRecording() else toast("Sem permissão do microfone")
        }

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#050B18")
        window.navigationBarColor = Color.parseColor("#050B18")

        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyPlaybackSpeed()
                applyVolume()
                applyPreviewEffects()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                fitSurface(videoSize)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.text = if (isPlaying) "❚❚" else "▶"
            }

            override fun onPlayerError(error: PlaybackException) {
                if (previewFx) {
                    previewFx = false
                    try {
                        player.setVideoEffects(emptyList())
                    } catch (e: Exception) {
                        // ignora
                    }
                    player.prepare()
                    toast("Prévia simples ativada. O vídeo exportado mantém os efeitos.")
                } else {
                    toast("Erro na prévia: ${error.errorCodeName}")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (recording) stopRecording()
                    player.seekTo(0, 0L)
                    player.pause()
                }
            }
        })

        buildUi()
        handler.post(ticker)
        loadProject()
    }

    override fun onStop() {
        player.pause()
        if (recording) stopRecording()
        saveProject()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(saveRunnable)
        pulse?.cancel()
        transformer?.cancel()
        player.release()
        bgExec.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ helpers de UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun glass(radius: Int = 14, fill: Int = cGlass, stroke: Int = cGlassStroke): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun gradient(radius: Int = 14): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cAccent, cAccent2)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun gradientOval(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(cAccent, cAccent2)).apply {
            shape = GradientDrawable.OVAL
        }

    private fun lpm(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    private fun tv(text: String, size: Float = 14f, color: Int = cText) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun section(text: String) = tv(text.uppercase(), 11f, cAccent).apply {
        letterSpacing = 0.12f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun note(text: String) = tv(text, 11f, cGrey).apply { setLineSpacing(0f, 1.15f) }

    private fun press(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f)
                    .setInterpolator(DecelerateInterpolator()).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(2.5f)).setDuration(220).start()
            }
            false
        }
    }

    private fun styleBtn(b: Button, on: Boolean) {
        if (on) {
            b.background = gradient(14)
            b.setTextColor(cNavy)
            b.typeface = Typeface.DEFAULT_BOLD
        } else {
            b.background = glass(14)
            b.setTextColor(cText)
            b.typeface = Typeface.DEFAULT
        }
    }

    private fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        stateListAnimator = null
        elevation = 0f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = dp(40)
        setPadding(dp(14), 0, dp(14), 0)
        styleBtn(this, false)
        press(this)
        setOnClickListener { onClick() }
    }

    private fun iconBtn(symbol: String, onClick: () -> Unit) = btn(symbol, onClick).apply {
        textSize = 17f
        background = glass(21)
        minimumHeight = dp(42)
        minimumWidth = dp(42)
        setPadding(0, 0, 0, 0)
    }

    private fun vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun hbox() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun hscroll(content: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(content)
    }

    private fun hint() = tv("Adicione um vídeo para usar esta ferramenta.", 13f, cGrey)

    private fun styleSeek(s: SeekBar) {
        s.progressTintList = ColorStateList.valueOf(cAccent)
        s.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        s.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
    }

    private fun seek(
        max: Int,
        value: Int,
        onChange: (Int, Boolean) -> Unit,
        onStart: () -> Unit,
        onStop: () -> Unit
    ) = SeekBar(this).apply {
        this.max = max
        progress = value
        styleSeek(this)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                onChange(p, fromUser)
            }

            override fun onStartTrackingTouch(s: SeekBar) {
                onStart()
            }

            override fun onStopTrackingTouch(s: SeekBar) {
                onStop()
            }
        })
    }

    private fun labeledSeek(
        box: LinearLayout,
        label: (Int) -> String,
        min: Int,
        max: Int,
        value: Int,
        onChange: (Int) -> Unit,
        onStart: () -> Unit = {},
        onStop: () -> Unit = {}
    ) {
        val l = tv(label(value), 13f)
        box.addView(l, lpm(WRAP, WRAP, t = 8))
        box.addView(
            seek(max - min, value - min, { p, u ->
                if (u) {
                    val v = p + min
                    onChange(v)
                    l.text = label(v)
                }
            }, onStart, onStop)
        )
    }

    private fun chipRow(labels: List<String>, current: Int, onPick: (Int) -> Unit): View {
        val r = hbox()
        labels.forEachIndexed { i, n ->
            val b = btn(n) { onPick(i) }
            styleBtn(b, i == current)
            r.addView(b, lpm(WRAP, WRAP, t = 6, r = 6))
        }
        return hscroll(r)
    }

    private fun showStyled(b: AlertDialog.Builder): AlertDialog {
        val d = b.create()
        d.window?.setBackgroundDrawable(glass(24, Color.parseColor("#F2081428"), 0x33FFFFFF))
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cAccent)
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(cAccent2)
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(cGrey)
        return d
    }

    private fun fmt(ms: Long): String {
        val m = ms / 60000
        val s = (ms % 60000) / 1000.0
        return "%d:%04.1f".format(m, s)
    }

    private fun fmtSpeed(s: Float): String = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()

    private fun parseMs(s: String): Long? {
        val v = s.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return (v * 1000).toLong()
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return "arquivo"
    }

    private fun keepAccess(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // sem permissão persistente, tudo bem
        }
    }

    // ------------------------------------------------------------------ interface

    private fun buildUi() {
        val frame = FrameLayout(this)
        frame.addView(LiquidBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val root = vbox().apply { setPadding(dp(14), dp(34), dp(14), dp(40)) }

        val top = hbox()
        val title = tv("Editor de Vídeo", 18f).apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        title.paint.shader = LinearGradient(
            0f, 0f, dp(170).toFloat(), 0f,
            intArrayOf(Color.WHITE, cAccent), null, Shader.TileMode.CLAMP
        )
        top.addView(title, lpm(0, WRAP, weight = 1f))
        undoBtn = iconBtn("↩") { undo() }
        redoBtn = iconBtn("↪") { redo() }
        top.addView(undoBtn, lpm(dp(42), dp(42), l = 4))
        top.addView(redoBtn, lpm(dp(42), dp(42), l = 4))
        top.addView(iconBtn("+") { pickVideos.launch(arrayOf("video/*")) }, lpm(dp(42), dp(42), l = 4))
        exportBtn = btn("Exportar") { if (exporting) cancelExport() else exportar() }
        exportBtn.background = gradient(21)
        exportBtn.setTextColor(cNavy)
        exportBtn.typeface = Typeface.DEFAULT_BOLD
        top.addView(exportBtn, lpm(WRAP, dp(42), l = 6))
        root.addView(top)

        progressBar = LiquidProgress(this).apply { visibility = View.GONE }
        root.addView(progressBar, lpm(MATCH, dp(10), t = 8))
        statusTv = tv("", 12f, cGrey)
        root.addView(statusTv)

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
        emptyState.addView(tv("Corte, filtros, textos, adesivos e música", 12f, cGrey), lpm(WRAP, WRAP, t = 2))
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
        root.addView(previewBox, lpm(MATCH, dp(240), t = 10))
        player.setVideoSurfaceView(surface)

        val transport = hbox()
        playBtn = btn("▶") { togglePlay() }
        playBtn.background = gradientOval()
        playBtn.setTextColor(cNavy)
        playBtn.setPadding(0, 0, 0, 0)
        transport.addView(playBtn, lpm(dp(46), dp(46)))
        timeSeek = SeekBar(this).apply {
            max = 1
            styleSeek(this)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        seekToTimeline(p.toLong())
                        timeTv.text = "${fmt(p.toLong())} / ${fmt(totalMs())}"
                    }
                }

                override fun onStartTrackingTouch(s: SeekBar) {
                    dragging = true
                }

                override fun onStopTrackingTouch(s: SeekBar) {
                    dragging = false
                    if (clips.isNotEmpty()) selectOnly(player.currentMediaItemIndex)
                }
            })
        }
        transport.addView(timeSeek, lpm(0, WRAP, l = 8, weight = 1f))
        root.addView(transport, lpm(MATCH, WRAP, t = 10))
        timeTv = tv("0:00.0 / 0:00.0", 12f, cGrey)
        root.addView(timeTv, lpm(WRAP, WRAP, l = 54))

        timelineBox = hbox().apply {
            clipChildren = false
            clipToPadding = false
        }
        root.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                clipToPadding = false
                setPadding(dp(4), dp(8), dp(4), dp(12))
                addView(timelineBox)
            },
            lpm(MATCH, WRAP, t = 4)
        )

        val actions = hbox()
        actions.addView(btn("✂ Dividir") { splitClip() }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("⧉ Duplicar") { dupClip() }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("🗑 Excluir") { delClip() }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("◀ Mover") { moveClip(-1) }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("Mover ▶") { moveClip(1) }, lpm(WRAP, WRAP, r = 6))
        root.addView(hscroll(actions), lpm(MATCH, WRAP, t = 2))

        toolsRow = hbox()
        root.addView(hscroll(toolsRow), lpm(MATCH, WRAP, t = 14))

        panel = vbox().apply {
            background = glass(22, 0x14FFFFFF, 0x26FFFFFF)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        root.addView(panel, lpm(MATCH, WRAP, t = 12))

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root)
        }
        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(frame)

        buildToolTabs()
        refreshTimeline()
        showTool(0, false)
        updateUndoButtons()

        for (k in 0 until root.childCount) {
            val ch = root.getChildAt(k)
            ch.alpha = 0f
            ch.translationY = dp(18).toFloat()
            ch.animate().alpha(1f).translationY(0f).setStartDelay(k * 70L).setDuration(420)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun buildToolTabs() {
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
            }
            press(tab)
            toolsRow.addView(tab, lpm(WRAP, WRAP, r = 8))
        }
    }

    private fun showTool(i: Int, animate: Boolean = false) {
        panel.removeAllViews()
        val v = when (i) {
            0 -> panelTrim()
            1 -> panelSpeed()
            2 -> panelFilters()
            3 -> panelTransform()
            4 -> panelText(false)
            5 -> panelText(true)
            6 -> panelAudio()
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

    private fun refreshTimeline() {
        timelineBox.removeAllViews()
        emptyState.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        if (clips.isEmpty()) {
            timelineBox.addView(tv("Sua linha do tempo aparece aqui", 13f, cGrey))
            return
        }
        clips.forEachIndexed { i, c ->
            val on = i == selected
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(5), dp(5), dp(5), dp(6))
                background = glass(16, if (on) 0x2600E5C8 else cGlass, if (on) cAccent else cGlassStroke)
                if (on) {
                    elevation = dp(6).toFloat()
                    outlineAmbientShadowColor = cAccent
                    outlineSpotShadowColor = cAccent
                }
                setOnClickListener { selectClip(i) }
            }
            press(card)
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#162844"))
                    cornerRadius = dp(12).toFloat()
                }
                clipToOutline = true
                val th = c.thumb
                if (th != null) setImageBitmap(th)
            }
            card.addView(img, LinearLayout.LayoutParams(dp(100), dp(64)))
            var extra = ""
            if (c.speed != 1f) extra += " · ${fmtSpeed(c.speed)}x"
            if (c.filter != 0 || c.brightness != 0 || c.contrast != 0 || c.sat != 0) extra += " 🎨"
            if (c.vol != 100) extra += " 🔊"
            card.addView(tv("${i + 1} · ${fmt(c.effMs)}$extra", 11f), lpm(WRAP, WRAP, t = 4))
            timelineBox.addView(card, lpm(WRAP, WRAP, r = 8))
        }
    }

    // ------------------------------------------------------------------ desfazer / refazer

    private fun snapshot() = Snapshot(
        clips.map { it.copy() }, texts.toList(), selected,
        musicUri, musicName, musicDurMs, musicVol,
        voiceUri, voiceDurMs, voiceVol,
        muteOriginal, aspectIdx, qualityIdx, fitIdx, fadeInIdx, fadeOutIdx
    )

    private fun restore(s: Snapshot) {
        clips.clear()
        clips.addAll(s.clips.map { it.copy() })
        texts.clear()
        texts.addAll(s.texts)
        selected = s.selected.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
        musicUri = s.musicUri
        musicName = s.musicName
        musicDurMs = s.musicDurMs
        musicVol = s.musicVol
        voiceUri = s.voiceUri
        voiceDurMs = s.voiceDurMs
        voiceVol = s.voiceVol
        muteOriginal = s.muteOriginal
        aspectIdx = s.aspectIdx
        qualityIdx = s.qualityIdx
        fitIdx = s.fitIdx
        fadeInIdx = s.fadeInIdx
        fadeOutIdx = s.fadeOutIdx
    }

    private fun pushUndo() {
        undoStack.add(snapshot())
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
        updateUndoButtons()
        scheduleSave()
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(snapshot())
        restore(undoStack.removeAt(undoStack.size - 1))
        afterEdit(selected, 0L)
        updateUndoButtons()
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(snapshot())
        restore(redoStack.removeAt(redoStack.size - 1))
        afterEdit(selected, 0L)
        updateUndoButtons()
    }

    private fun updateUndoButtons() {
        undoBtn.alpha = if (undoStack.isEmpty()) 0.35f else 1f
        redoBtn.alpha = if (redoStack.isEmpty()) 0.35f else 1f
    }

    // ------------------------------------------------------------------ painéis das ferramentas

    private fun sel(): Clip? = clips.getOrNull(selected)

    private fun panelTrim(): View {
        val c = sel() ?: return hint()
        val box = vbox()
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
        box.addView(
            note("Arraste os controles ou pause no ponto certo e toque em “Início aqui” / “Fim aqui”. Para cortar no meio, use ✂ Dividir."),
            lpm(MATCH, WRAP, t = 8)
        )
        return box
    }

    private fun trimHere(isEnd: Boolean) {
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

    private fun panelSpeed(): View {
        val c = sel() ?: return hint()
        val box = vbox()
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

    private fun panelFilters(): View {
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

    private fun panelTransform(): View {
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

    private fun panelText(sticker: Boolean): View {
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

    private fun addSticker(e: String) {
        pushUndo()
        val now = currentTimelineMs()
        texts.add(TextItem(e, now, now + 3000L, 1, 1, Color.WHITE, 1, true))
        overlayKey = ""
        showTool(currentTool)
    }

    private fun panelAudio(): View {
        val box = vbox()
        box.addView(section("Clipe selecionado"))
        val c = sel()
        if (c == null) {
            box.addView(hint())
        } else {
            labeledSeek(box, { "Volume: $it%" }, 0, 200, c.vol, { c.vol = it }, { pushUndo() }, {
                applyVolume()
                refreshTimeline()
                scheduleSave()
            })
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

    private fun panelFormat(): View {
        val box = vbox()
        box.addView(section("Proporção"))
        box.addView(chipRow(aspectNames, aspectIdx) { i ->
            pushUndo()
            aspectIdx = i
            showTool(currentTool)
        })
        box.addView(note("9:16 Reels/Shorts/TikTok · 16:9 YouTube · 4:5 feed"))
        box.addView(section("Qualidade"), lpm(WRAP, WRAP, t = 12))
        box.addView(chipRow(qualityNames, qualityIdx) { i ->
            pushUndo()
            qualityIdx = i
            showTool(currentTool)
        })
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
        box.addView(btn("Novo projeto (limpar tudo)") {
            showStyled(
                AlertDialog.Builder(this)
                    .setTitle("Novo projeto")
                    .setMessage("Remover todos os clipes, textos e áudios? Dá para desfazer.")
                    .setPositiveButton("Limpar") { _, _ -> newProject() }
                    .setNegativeButton("Cancelar", null)
            )
        }, lpm(WRAP, WRAP, t = 18))
        box.addView(note("Vale para o vídeo exportado. Qualidade menor exporta mais rápido."), lpm(MATCH, WRAP, t = 8))
        return box
    }

    private fun newProject() {
        pushUndo()
        clips.clear()
        texts.clear()
        selected = 0
        musicUri = null
        voiceUri = null
        muteOriginal = false
        fadeInIdx = 0
        fadeOutIdx = 0
        overlayKey = ""
        afterEdit(0, 0L)
    }

    private fun itemDialog(index: Int, sticker: Boolean, newText: String = "") {
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

    // ------------------------------------------------------------------ prévia de texto e adesivos

    private fun refreshOverlay(pos: Long) {
        val lw = overlayLayer.width
        val lh = overlayLayer.height
        val active = ArrayList<Int>()
        if (pos >= 0L) texts.forEachIndexed { i, t -> if (pos >= t.startMs && pos <= t.endMs) active.add(i) }
        val key = active.joinToString(",") { System.identityHashCode(texts[it]).toString() } + "|$lw|$lh"
        if (key == overlayKey) return
        overlayKey = key
        overlayLayer.removeAllViews()
        if (lw <= 0 || lh <= 0) return
        val shortSide = min(lw, lh).toFloat()
        for (i in active) {
            val t = texts[i]
            val view = TextView(this)
            view.text = t.text
            view.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                shortSide * (if (t.sticker) stickerFactor[t.size] else sizeFactor[t.size])
            )
            view.setTextColor(t.color)
            if (!t.sticker) {
                view.setTypeface(view.typeface, Typeface.BOLD)
                view.setBackgroundColor(if (t.color == Color.BLACK) 0x99FFFFFF.toInt() else 0x66000000)
                view.setPadding(dp(4), 0, dp(4), 0)
            }
            val gv = when (t.pos) {
                0 -> Gravity.TOP
                1 -> Gravity.CENTER_VERTICAL
                else -> Gravity.BOTTOM
            }
            val gh = when (t.posX) {
                0 -> Gravity.START
                1 -> Gravity.CENTER_HORIZONTAL
                else -> Gravity.END
            }
            val lp = FrameLayout.LayoutParams(WRAP, WRAP, gv or gh)
            lp.setMargins((lw * 0.06f).toInt(), (lh * 0.06f).toInt(), (lw * 0.06f).toInt(), (lh * 0.06f).toInt())
            overlayLayer.addView(view, lp)
        }
    }

    // ------------------------------------------------------------------ edição da linha do tempo

    private fun totalMs(): Long = clips.sumOf { it.effMs }

    private fun updateSeekMax() {
        timeSeek.max = totalMs().toInt().coerceAtLeast(1)
        if (clips.isEmpty()) {
            timeSeek.progress = 0
            timeTv.text = "0:00.0 / 0:00.0"
        }
    }

    private fun currentTimelineMs(): Long {
        if (clips.isEmpty() || player.mediaItemCount != clips.size) return 0L
        val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
        var cum = 0L
        for (k in 0 until idx) cum += clips[k].effMs
        return cum + (player.currentPosition.coerceAtLeast(0L) / clips[idx].speed).toLong()
    }

    private fun rebuildPlayer(index: Int, posMs: Long) {
        if (clips.isEmpty()) {
            player.clearMediaItems()
            return
        }
        val items = clips.map { c ->
            MediaItem.Builder()
                .setUri(c.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(c.startMs)
                        .setEndPositionMs(c.endMs)
                        .build()
                )
                .build()
        }
        val i = index.coerceIn(0, clips.size - 1)
        player.playWhenReady = false
        applyPreviewEffects(i)
        player.setMediaItems(items, i, posMs)
        player.prepare()
        player.setPlaybackSpeed(clips[i].speed)
        player.volume = clipVolume(clips[i])
    }

    private fun afterEdit(playIndex: Int, posMs: Long) {
        overlayKey = ""
        rebuildPlayer(playIndex, posMs)
        refreshTimeline()
        updateSeekMax()
        showTool(currentTool)
        scheduleSave()
    }

    private fun applyPlaybackSpeed() {
        val i = player.currentMediaItemIndex
        if (i in clips.indices) player.setPlaybackSpeed(clips[i].speed)
    }

    private fun clipVolume(c: Clip?): Float {
        if (muteOriginal || recording) return 0f
        if (c == null) return 1f
        return (c.vol / 100f).coerceAtMost(1f)
    }

    private fun applyVolume() {
        player.volume = clipVolume(clips.getOrNull(player.currentMediaItemIndex))
    }

    private fun applyPreviewEffects(index: Int = player.currentMediaItemIndex) {
        val c = clips.getOrNull(index)
        try {
            player.setVideoEffects(if (c == null || !previewFx) emptyList() else videoEffects(c, false))
        } catch (e: Exception) {
            previewFx = false
        }
    }

    private fun selectClip(i: Int) {
        selected = i
        refreshTimeline()
        showTool(currentTool)
        if (player.mediaItemCount == clips.size && i in clips.indices) player.seekTo(i, 0L)
    }

    private fun selectOnly(i: Int) {
        if (i !in clips.indices || i == selected) return
        selected = i
        refreshTimeline()
        showTool(currentTool)
    }

    private fun seekToTimeline(pos: Long) {
        var rem = pos
        for ((i, c) in clips.withIndex()) {
            if (rem <= c.effMs || i == clips.size - 1) {
                val src = (rem * c.speed).toLong().coerceIn(0L, c.endMs - c.startMs)
                player.seekTo(i, src)
                return
            }
            rem -= c.effMs
        }
    }

    private fun togglePlay() {
        if (clips.isEmpty()) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L)
            player.play()
        }
    }

    private fun trimDone(isEnd: Boolean) {
        val c = sel() ?: return
        val len = c.endMs - c.startMs
        afterEdit(selected, if (isEnd) (len - 1500L).coerceAtLeast(0L) else 0L)
        loadThumb(c)
    }

    private fun splitClip() {
        if (clips.isEmpty() || player.mediaItemCount != clips.size) return
        val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
        val c = clips[idx]
        val at = c.startMs + player.currentPosition
        if (at < c.startMs + 300L || at > c.endMs - 300L) {
            toast("Posicione o vídeo no ponto do corte (longe das pontas) e toque em Dividir.")
            return
        }
        pushUndo()
        val second = c.copy()
        second.startMs = at
        c.endMs = at
        clips.add(idx + 1, second)
        selected = idx + 1
        loadThumb(second)
        afterEdit(idx + 1, 0L)
    }

    private fun dupClip() {
        val c = sel() ?: return
        pushUndo()
        clips.add(selected + 1, c.copy())
        selected += 1
        afterEdit(selected, 0L)
    }

    private fun delClip() {
        if (sel() == null) return
        pushUndo()
        clips.removeAt(selected)
        selected = selected.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
        afterEdit(selected, 0L)
    }

    private fun moveClip(d: Int) {
        val j = selected + d
        if (sel() == null || j !in clips.indices) return
        pushUndo()
        val c = clips.removeAt(selected)
        clips.add(j, c)
        selected = j
        afterEdit(j, 0L)
    }

    private fun addClip(uri: Uri) {
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

    private fun loadThumb(c: Clip) {
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

    private fun fitSurface(v: VideoSize) {
        previewBox.post {
            val vw = v.width * v.pixelWidthHeightRatio
            val vh = v.height.toFloat()
            val bw = previewBox.width.toFloat()
            val bh = previewBox.height.toFloat()
            if (vw <= 0f || vh <= 0f || bw <= 0f || bh <= 0f) return@post
            val scale = min(bw / vw, bh / vh)
            val w = (vw * scale).toInt()
            val h = (vh * scale).toInt()
            surface.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
            overlayLayer.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
        }
    }

    // ------------------------------------------------------------------ narração

    private fun requestRecord() {
        if (clips.isEmpty()) {
            toast("Adicione vídeos primeiro")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    private fun startRecording() {
        val f = File(filesDir, "narracao_${System.currentTimeMillis()}.m4a")
        var r: MediaRecorder? = null
        try {
            r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            voiceFile = f
            recording = true
            player.volume = 0f
            player.seekTo(0, 0L)
            player.play()
            showTool(currentTool)
        } catch (e: Exception) {
            try {
                r?.release()
            } catch (e2: Exception) {
                // ignora
            }
            recorder = null
            recording = false
            toast("Não consegui gravar: ${e.message}")
        }
    }

    private fun stopRecording() {
        val r = recorder ?: return
        recording = false
        recorder = null
        var ok = true
        try {
            r.stop()
        } catch (e: Exception) {
            ok = false
        }
        r.release()
        player.pause()
        applyVolume()
        val f = voiceFile
        if (ok && f != null && f.exists()) {
            pushUndo()
            val mr = MediaMetadataRetriever()
            try {
                mr.setDataSource(f.absolutePath)
                voiceDurMs = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                voiceUri = Uri.fromFile(f)
            } catch (e: Exception) {
                toast("Não consegui ler a gravação")
            } finally {
                mr.release()
            }
        } else {
            toast("Gravação muito curta.")
        }
        showTool(currentTool)
        scheduleSave()
    }

    // ------------------------------------------------------------------ projeto (salvar e restaurar)

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 700)
    }

    private fun saveProject() {
        try {
            val o = JSONObject()
            val ca = JSONArray()
            for (c in clips) {
                val j = JSONObject()
                j.put("uri", c.uri.toString())
                j.put("name", c.name)
                j.put("dur", c.durationMs)
                j.put("w", c.w)
                j.put("h", c.h)
                j.put("start", c.startMs)
                j.put("end", c.endMs)
                j.put("speed", c.speed.toDouble())
                j.put("rot", c.rotation)
                j.put("flip", c.flip)
                j.put("filter", c.filter)
                j.put("bri", c.brightness)
                j.put("con", c.contrast)
                j.put("sat", c.sat)
                j.put("vol", c.vol)
                j.put("zoom", c.zoom)
                j.put("px", c.panX)
                j.put("py", c.panY)
                j.put("ken", c.ken)
                ca.put(j)
            }
            o.put("clips", ca)
            val ta = JSONArray()
            for (t in texts) {
                val j = JSONObject()
                j.put("text", t.text)
                j.put("start", t.startMs)
                j.put("end", t.endMs)
                j.put("pos", t.pos)
                j.put("size", t.size)
                j.put("color", t.color)
                j.put("posX", t.posX)
                j.put("sticker", t.sticker)
                ta.put(j)
            }
            o.put("texts", ta)
            o.put("selected", selected)
            o.put("musicUri", musicUri?.toString() ?: "")
            o.put("musicName", musicName)
            o.put("musicDur", musicDurMs)
            o.put("musicVol", musicVol)
            o.put("voiceUri", voiceUri?.toString() ?: "")
            o.put("voiceDur", voiceDurMs)
            o.put("voiceVol", voiceVol)
            o.put("mute", muteOriginal)
            o.put("aspect", aspectIdx)
            o.put("quality", qualityIdx)
            o.put("fit", fitIdx)
            o.put("fadeIn", fadeInIdx)
            o.put("fadeOut", fadeOutIdx)
            getSharedPreferences("editor", MODE_PRIVATE).edit().putString("project", o.toString()).apply()
        } catch (e: Exception) {
            // não conseguiu salvar, segue o jogo
        }
    }

    private fun canRead(uri: Uri): Boolean = try {
        contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    private fun loadProject() {
        val s = getSharedPreferences("editor", MODE_PRIVATE).getString("project", null) ?: return
        try {
            val o = JSONObject(s)
            val ca = o.optJSONArray("clips") ?: return
            for (i in 0 until ca.length()) {
                val j = ca.getJSONObject(i)
                val uri = Uri.parse(j.getString("uri"))
                if (!canRead(uri)) continue
                val c = Clip(uri, j.optString("name", "clipe"), j.getLong("dur"), j.getInt("w"), j.getInt("h"))
                c.startMs = j.optLong("start", 0L)
                c.endMs = j.optLong("end", c.durationMs)
                c.speed = j.optDouble("speed", 1.0).toFloat()
                c.rotation = j.optInt("rot", 0)
                c.flip = j.optBoolean("flip", false)
                c.filter = j.optInt("filter", 0)
                c.brightness = j.optInt("bri", 0)
                c.contrast = j.optInt("con", 0)
                c.sat = j.optInt("sat", 0)
                c.vol = j.optInt("vol", 100)
                c.zoom = j.optInt("zoom", 100)
                c.panX = j.optInt("px", 0)
                c.panY = j.optInt("py", 0)
                c.ken = j.optInt("ken", 0)
                clips.add(c)
                loadThumb(c)
            }
            val ta = o.optJSONArray("texts")
            if (ta != null) {
                for (i in 0 until ta.length()) {
                    val j = ta.getJSONObject(i)
                    texts.add(
                        TextItem(
                            j.getString("text"), j.getLong("start"), j.getLong("end"),
                            j.optInt("pos", 2), j.optInt("size", 1), j.optInt("color", Color.WHITE),
                            j.optInt("posX", 1), j.optBoolean("sticker", false)
                        )
                    )
                }
            }
            val mu = o.optString("musicUri", "")
            if (mu.isNotEmpty() && canRead(Uri.parse(mu))) {
                musicUri = Uri.parse(mu)
                musicName = o.optString("musicName", "")
                musicDurMs = o.optLong("musicDur", 0L)
            }
            musicVol = o.optInt("musicVol", 100)
            val vu = o.optString("voiceUri", "")
            if (vu.isNotEmpty() && canRead(Uri.parse(vu))) {
                voiceUri = Uri.parse(vu)
                voiceDurMs = o.optLong("voiceDur", 0L)
            }
            voiceVol = o.optInt("voiceVol", 100)
            muteOriginal = o.optBoolean("mute", false)
            aspectIdx = o.optInt("aspect", 0).coerceIn(0, aspectNames.size - 1)
            qualityIdx = o.optInt("quality", 0).coerceIn(0, qualityNames.size - 1)
            fitIdx = o.optInt("fit", 0).coerceIn(0, fitNames.size - 1)
            fadeInIdx = o.optInt("fadeIn", 0).coerceIn(0, fadeNames.size - 1)
            fadeOutIdx = o.optInt("fadeOut", 0).coerceIn(0, fadeNames.size - 1)
            if (clips.isNotEmpty()) {
                selected = o.optInt("selected", 0).coerceIn(0, clips.size - 1)
                afterEdit(selected, 0L)
                toast("Projeto anterior restaurado")
            }
        } catch (e: Exception) {
            clips.clear()
            texts.clear()
        }
    }

    // ------------------------------------------------------------------ efeitos

    private fun filterEffects(i: Int): List<Effect> = when (i) {
        1 -> listOf(RgbFilter.createGrayscaleFilter())
        2 -> listOf(HslAdjustment.Builder().adjustSaturation(35f).build(), Contrast(0.15f))
        3 -> listOf(RgbAdjustment.Builder().setRedScale(1.15f).setGreenScale(1.0f).setBlueScale(0.88f).build())
        4 -> listOf(RgbAdjustment.Builder().setRedScale(0.88f).setGreenScale(1.0f).setBlueScale(1.15f).build())
        5 -> listOf(RgbFilter.createInvertedFilter())
        6 -> listOf(sepiaEffect())
        7 -> listOf(
            HslAdjustment.Builder().adjustSaturation(-15f).build(),
            RgbAdjustment.Builder().setRedScale(1.06f).setGreenScale(0.98f).setBlueScale(1.06f).build(),
            Contrast(0.3f)
        )
        8 -> listOf(
            RgbAdjustment.Builder().setRedScale(1.1f).setGreenScale(1.0f).setBlueScale(0.8f).build(),
            HslAdjustment.Builder().adjustSaturation(-20f).build(),
            Contrast(-0.15f)
        )
        9 -> listOf(HslAdjustment.Builder().adjustSaturation(-30f).build(), Contrast(0.4f))
        10 -> listOf(RgbFilter.createGrayscaleFilter(), Contrast(0.4f))
        11 -> listOf(Contrast(-0.3f), Brightness(0.08f))
        else -> emptyList()
    }

    private fun sepiaEffect(): Effect = object : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = floatArrayOf(
            0.393f, 0.349f, 0.272f, 0f,
            0.769f, 0.686f, 0.534f, 0f,
            0.189f, 0.168f, 0.131f, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun zoomEffect(c: Clip, export: Boolean): Effect {
        val base = c.zoom / 100f
        val px = c.panX / 100f
        val py = c.panY / 100f
        val ken = if (export) c.ken else 0
        val durUs = ((c.endMs - c.startMs) * 1000L).coerceAtLeast(1L).toFloat()
        return object : MatrixTransformation {
            override fun getMatrix(presentationTimeUs: Long): Matrix {
                val p = (presentationTimeUs / durUs).coerceIn(0f, 1f)
                var s = base
                when (ken) {
                    1 -> s = base * (1f + 0.3f * p)
                    2 -> s = base * (1.3f - 0.3f * p)
                    3, 4 -> s = base * 1.25f
                }
                val room = s - 1f
                var tx = -px * room
                val ty = py * room
                if (ken == 3) tx = room * (1f - 2f * p)
                if (ken == 4) tx = room * (2f * p - 1f)
                val m = Matrix()
                m.postScale(s, s)
                m.postTranslate(tx, ty)
                return m
            }
        }
    }

    private fun videoEffects(c: Clip, export: Boolean): List<Effect> {
        val v = ArrayList<Effect>()
        if (c.rotation != 0 || c.flip) {
            v.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(-c.rotation.toFloat())
                    .setScale(if (c.flip) -1f else 1f, 1f)
                    .build()
            )
        }
        if (c.zoom != 100 || c.panX != 0 || c.panY != 0 || (export && c.ken != 0)) {
            v.add(zoomEffect(c, export))
        }
        v.addAll(filterEffects(c.filter))
        if (c.sat != 0) v.add(HslAdjustment.Builder().adjustSaturation(c.sat.toFloat()).build())
        if (c.brightness != 0) v.add(Brightness(c.brightness / 100f))
        if (c.contrast != 0) v.add(Contrast(c.contrast / 100f))
        return v
    }

    // ------------------------------------------------------------------ exportação

    private fun outputSize(): Pair<Int, Int> {
        val first = clips.first()
        var w = first.w
        var h = first.h
        if (first.rotation == 90 || first.rotation == 270) {
            val t = w
            w = h
            h = t
        }
        val ratio = aspectRatios[aspectIdx] ?: (w.toFloat() / h)
        val shortSide = qualityShort[qualityIdx] ?: min(w, h)
        val ow: Int
        val oh: Int
        if (ratio >= 1f) {
            oh = shortSide
            ow = (shortSide * ratio).roundToInt()
        } else {
            ow = shortSide
            oh = (shortSide / ratio).roundToInt()
        }
        return Pair((ow - ow % 2).coerceAtLeast(2), (oh - oh % 2).coerceAtLeast(2))
    }

    private fun audioItem(uri: Uri, durMs: Long, vol: Int): EditedMediaItem {
        val cb = MediaItem.ClippingConfiguration.Builder()
        if (durMs > 0L) cb.setEndPositionMs(min(durMs, totalMs()))
        val media = MediaItem.Builder().setUri(uri).setClippingConfiguration(cb.build()).build()
        val ap = ArrayList<AudioProcessor>()
        if (vol != 100) ap.add(GainProcessor(vol / 100f))
        return EditedMediaItem.Builder(media)
            .setRemoveVideo(true)
            .setEffects(Effects(ap, emptyList<Effect>()))
            .build()
    }

    private fun exportar() {
        if (clips.isEmpty()) {
            toast("Adicione vídeos primeiro")
            return
        }
        if (recording) stopRecording()
        player.pause()

        val items = ArrayList<EditedMediaItem>()
        for (c in clips) {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(c.startMs)
                .setEndPositionMs(c.endMs)
                .build()
            val mi = MediaItem.Builder().setUri(c.uri).setClippingConfiguration(clipping).build()

            val v = ArrayList<Effect>(videoEffects(c, true))
            val a = ArrayList<AudioProcessor>()
            if (c.speed != 1f) {
                v.add(SpeedChangeEffect(c.speed))
                if (!muteOriginal) {
                    val sonic = SonicAudioProcessor()
                    sonic.setSpeed(c.speed)
                    a.add(sonic)
                }
            }
            if (!muteOriginal && c.vol != 100) a.add(GainProcessor(c.vol / 100f))

            items.add(
                EditedMediaItem.Builder(mi)
                    .setRemoveAudio(muteOriginal)
                    .setEffects(Effects(a, v))
                    .build()
            )
        }

        val (ow, oh) = outputSize()
        val layout = if (fitIdx == 0) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
        val compEffects = ArrayList<Effect>()
        compEffects.add(Presentation.createForWidthAndHeight(ow, oh, layout))

        val shortOut = min(ow, oh)
        val overlays = ArrayList<TextureOverlay>()
        for (t in texts) {
            val sp = SpannableString(t.text)
            val len = sp.length
            val flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            val factor = if (t.sticker) stickerFactor[t.size] else sizeFactor[t.size]
            sp.setSpan(AbsoluteSizeSpan((shortOut * factor).toInt()), 0, len, flags)
            if (!t.sticker) {
                sp.setSpan(ForegroundColorSpan(t.color), 0, len, flags)
                sp.setSpan(StyleSpan(Typeface.BOLD), 0, len, flags)
                val bgColor = if (t.color == Color.BLACK) 0x99FFFFFF.toInt() else 0x66000000
                sp.setSpan(BackgroundColorSpan(bgColor), 0, len, flags)
            }
            val y = posY[t.pos]
            val x = posXv[t.posX]
            val shown = OverlaySettings.Builder().setBackgroundFrameAnchor(x, y).build()
            val hidden = OverlaySettings.Builder().setBackgroundFrameAnchor(x, y).setAlphaScale(0f).build()
            overlays.add(TimedText(t, sp, shown, hidden))
        }
        if (overlays.isNotEmpty()) compEffects.add(OverlayEffect(ImmutableList.copyOf(overlays)))

        val fi = fadeMs[fadeInIdx]
        val fo = fadeMs[fadeOutIdx]
        val compAudio = ArrayList<AudioProcessor>()
        if (fi > 0L || fo > 0L) {
            val black = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            black.eraseColor(Color.BLACK)
            val fadeList = ArrayList<TextureOverlay>()
            fadeList.add(FadeOverlay(black, totalMs(), fi, fo))
            compEffects.add(OverlayEffect(ImmutableList.copyOf(fadeList)))
            compAudio.add(FadeProcessor(fi, fo, totalMs()))
        }

        val seqs = ArrayList<EditedMediaItemSequence>()
        seqs.add(EditedMediaItemSequence(items))
        val mu = musicUri
        if (mu != null) seqs.add(EditedMediaItemSequence(listOf(audioItem(mu, musicDurMs, musicVol))))
        val vu = voiceUri
        if (vu != null) seqs.add(EditedMediaItemSequence(listOf(audioItem(vu, voiceDurMs, voiceVol))))

        val composition = Composition.Builder(seqs)
            .setEffects(Effects(compAudio, compEffects))
            .build()

        val saida = File(cacheDir, "saida_${System.currentTimeMillis()}.mp4")

        val t = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    setExporting(false)
                    val uri = salvarNaGaleria(saida)
                    statusTv.text = "Pronto! Salvo em Filmes/EditorVideo."
                    val fileUri = uri ?: Uri.fromFile(saida)
                    showStyled(
                        AlertDialog.Builder(this@MainActivity)
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
                    statusTv.text = "Erro ao exportar: ${exportException.message}"
                }
            })
            .build()
        transformer = t

        statusTv.text = "Exportando…"
        setExporting(true)
        t.start(composition, saida.absolutePath)
    }

    private fun setExporting(on: Boolean) {
        exporting = on
        exportBtn.text = if (on) "Cancelar" else "Exportar"
        progressBar.visibility = if (on) View.VISIBLE else View.GONE
        progressBar.progress = 0
    }

    private fun cancelExport() {
        transformer?.cancel()
        setExporting(false)
        statusTv.text = "Exportação cancelada."
    }

    private fun salvarNaGaleria(arquivo: File): Uri? {
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
}
