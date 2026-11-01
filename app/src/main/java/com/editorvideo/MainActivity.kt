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

class MainActivity : ComponentActivity() {

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
    private var transType = 0
    private var transIdx = 1
    private var codecIdx = 0
    private var bitrateIdx = 1
    private var projectName = "Meu projeto"
    private var exporting = false
    private var extractingAudio = false
    private var analysingSilence = false
    private var dragging = false
    private var recording = false
    private var previewFx = true
    private var overlayKey = ""
    private var currentId: String? = null
    private var onHome = true
    private var thumbSavedFor = ""
    private lateinit var store: ProjectStore

    private val undoStack = mutableListOf<Snapshot>()
    private val redoStack = mutableListOf<Snapshot>()

    private val aspectNames = listOf("Original", "16:9", "9:16", "1:1", "4:3", "3:4", "4:5", "2.35:1")
    private val aspectRatios = listOf<Float?>(null, 16f / 9f, 9f / 16f, 1f, 4f / 3f, 3f / 4f, 4f / 5f, 2.35f)
    private val transNames = listOf("Nenhuma", "Fade no preto", "Fade no branco")
    private val transDur = listOf(500L, 1000L, 1500L, 2000L)
    private val transDurNames = listOf("0,5 s", "1 s", "1,5 s", "2 s")
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
    private val toolNames = listOf("Cortar", "Velocidade", "Filtros", "Transformar", "Transição", "Texto", "Adesivos", "Mixer", "Formato")
    private val toolIcons = listOf("✂️", "⏩", "🎨", "🔄", "🔀", "🔤", "😀", "🔊", "📐")
    private val posNames = listOf("Topo", "Centro", "Base")
    private val posXNames = listOf("Esquerda", "Centro", "Direita")
    private val sizeNames = listOf("Pequeno", "Médio", "Grande")
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
    private lateinit var timeline: TimelineView
    private lateinit var nameTv: TextView
    private lateinit var rootFrame: FrameLayout
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
                    if (timeline.posMs != pos) timeline.posMs = pos
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
                    ExportService.progress(applicationContext, progressHolder.progress)
                    statusTv.text = "Exportando… ${progressHolder.progress}%"
                }
            }
            handler.postDelayed(this, 40)
        }
    }

    private val pickVideos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> addClipsFrom(uris) }

    // Galeria do sistema (Photo Picker): o acesso é temporário, então os vídeos são copiados para `imports/`.
    private val pickGallery =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
            if (uris.isNotEmpty()) copyAndAdd(uris)
        }

    private fun openGallery() {
        pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
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

    private val askNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> exportar() }

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

        store = ProjectStore(File(filesDir, "projects"))
        migrateLegacyProject()
        onBackPressedDispatcher.addCallback(this) {
            if (!onHome) {
                goHome()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        buildUi()
        handler.post(ticker)
        showHome()
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pulse?.cancel()
        buildUi()
        if (onHome) showHome()
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
        if (isFinishing) {
            transformer?.cancel()
            if (exporting) ExportService.finish(applicationContext, "Exportação cancelada.", null)
        }
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
        if (uri.scheme == "file") return File(uri.path ?: "").name.substringAfter('_', "arquivo")
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
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
            val root = vbox().apply { setPadding(dp(14), dp(34), dp(14), dp(40)) }
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
            val outer = vbox().apply { setPadding(dp(14), dp(12), dp(14), dp(8)) }
            outer.addView(top)
            outer.addView(progressBar, lpm(MATCH, dp(8), t = 4))
            outer.addView(statusTv)
            val split = hbox().apply { gravity = Gravity.TOP }
            val left = vbox()
            left.addView(previewBox, lpm(MATCH, 0, weight = 1f))
            left.addView(transport, lpm(MATCH, WRAP, t = 6))
            left.addView(timeline, lpm(MATCH, dp(112), t = 6))
            val right = vbox().apply { setPadding(0, 0, 0, dp(16)) }
            right.addView(hscroll(actions))
            right.addView(hscroll(toolsRow), lpm(MATCH, WRAP, t = 8))
            right.addView(panel, lpm(MATCH, WRAP, t = 8))
            val rs = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                addView(right)
            }
            split.addView(left, lpm(0, MATCH, weight = 1.15f))
            split.addView(rs, lpm(0, MATCH, l = 12, weight = 1f))
            outer.addView(split, lpm(MATCH, 0, t = 8, weight = 1f))
            frame.addView(outer, FrameLayout.LayoutParams(MATCH, MATCH))
            animRoot = outer
        }
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

    private fun chipGroup(labels: List<String>, current: Int, onPick: (Int) -> Unit): View {
        val r = hbox()
        val all = ArrayList<Button>()
        labels.forEachIndexed { i, n ->
            val b = btn(n) {
                all.forEachIndexed { k, bb -> styleBtn(bb, k == i) }
                onPick(i)
            }
            styleBtn(b, i == current)
            all.add(b)
            r.addView(b, lpm(WRAP, WRAP, t = 6, r = 6))
        }
        return hscroll(r)
    }

    private fun editField(h: String, value: String) = EditText(this).apply {
        hint = h
        setText(value)
        setTextColor(cText)
        setHintTextColor(cGrey)
        backgroundTintList = ColorStateList.valueOf(cAccent)
    }

    private fun projectDialog(isNew: Boolean) {
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
    private fun encoderWarning(w: Int, h: Int): String? {
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

    private fun videoBitrate(w: Int, h: Int): Int {
        return TimelineMath.videoBitrate(w, h, bitrateIdx, codecIdx)
    }

    private fun exportDialog() {
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

    private fun showRadialMenu() {
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

    private fun cutAtPlayhead(keepRight: Boolean) {
        if (clips.isEmpty() || player.mediaItemCount != clips.size) return
        val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
        val c = clips[idx]
        val at = c.startMs + player.currentPosition
        if (at < c.startMs + 300L || at > c.endMs - 300L) {
            toast("Posicione a agulha dentro do clipe, longe das pontas.")
            return
        }
        pushUndo()
        selected = idx
        if (keepRight) c.startMs = at else c.endMs = at
        afterEdit(idx, 0L)
        loadThumb(c)
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
        emptyState.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        timeline.clips = clips.mapIndexed { i, c ->
            val extra = (if (c.speed != 1f) " ${fmtSpeed(c.speed)}x" else "") + (if (c.filter != 0) " 🎨" else "")
            TimelineView.ClipInfo(c.effMs, c.thumb, "${i + 1} · ${fmt(c.effMs)}$extra", waveFor(c.uri, c.durationMs), c.startMs, c.speed)
        }
        timeline.selected = if (clips.isEmpty()) -1 else selected
        timeline.bars = texts.map {
            TimelineView.Bar(it.startMs, it.endMs, it.text, if (it.sticker) 0xFFFF6FB5.toInt() else 0xFF7C5CFF.toInt())
        }
        val tot = totalMs()
        timeline.musicMs = if (musicUri != null) (if (musicDurMs > 0L) min(musicDurMs, tot) else tot) else 0L
        timeline.voiceMs = if (voiceUri != null) min(voiceDurMs, tot) else 0L
        timeline.musicWave = musicUri?.let { waveFor(it, musicDurMs) }
        timeline.voiceWave = voiceUri?.let { waveFor(it, voiceDurMs) }
        timeline.transOn = transType != 0 && clips.size > 1
        timeline.invalidate()
        if (selected !in clips.indices) clipPicked = false
        clipGroup?.visibility = if (clipPicked) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------ forma de onda (v3.2.7)

    private val waves = HashMap<String, WaveData>()
    private val waveBusy = HashSet<String>()
    private val waveExec = Executors.newSingleThreadExecutor { r ->
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }
    }

    /**
     * Forma de onda do arquivo inteiro, ou null enquanto ainda não existe (a análise roda em segundo plano
     * e, ao terminar, redesenha a linha do tempo). Arquivo sem áudio ou que o aparelho não decodifica
     * fica marcado como vazio e não é analisado de novo. Só chamar na thread da tela.
     */
    private fun waveFor(uri: Uri, durMs: Long): WaveData? {
        val key = uri.toString()
        val got = waves[key]
        if (got != null) return if (got.peaks.isEmpty()) null else got
        if (durMs <= 0L || !waveBusy.add(key)) return null
        val bucket = WaveMath.bucketFor(durMs)
        waveExec.execute {
            val p = try {
                AudioPeaks.compute(applicationContext, uri, 0L, durMs + bucket, bucket)
            } catch (e: Throwable) {
                null
            }
            handler.post {
                waveBusy.remove(key)
                if (isDestroyed) return@post
                if (waves.size > 30) waves.clear()
                waves[key] = WaveData(p ?: FloatArray(0), bucket)
                refreshTimeline()
            }
        }
        return null
    }

    // ------------------------------------------------------------------ desfazer / refazer

    private fun snapshot() = Snapshot(
        clips.map { it.copy() }, texts.toList(), selected,
        musicUri, musicName, musicDurMs, musicVol,
        voiceUri, voiceDurMs, voiceVol,
        muteOriginal, aspectIdx, qualityIdx, fitIdx, fadeInIdx, fadeOutIdx, transType, transIdx
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
        transType = s.transType
        transIdx = s.transIdx
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
    private fun removerSilencios() {
        val c = sel() ?: return
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

    private fun aplicarSilencios(c: Clip, s0: Long, e0: Long, ranges: List<Pair<Long, Long>>?) {
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
            // fade de áudio por clipe, em passos de 0,1 s (0 a 3 s). Só vale na exportação; a prévia não mostra.
            labeledSeek(box, { "Fade de entrada do áudio: ${it / 10},${it % 10} s" }, 0, 30, c.fadeIn / 100,
                { c.fadeIn = it * 100 }, { pushUndo() }, { scheduleSave() })
            labeledSeek(box, { "Fade de saída do áudio: ${it / 10},${it % 10} s" }, 0, 30, c.fadeOut / 100,
                { c.fadeOut = it * 100 }, { pushUndo() }, { scheduleSave() })
            box.addView(tv("O fade de cada clipe é aplicado ao exportar; a prévia ainda não o mostra.", 12f, cGrey), lpm(WRAP, WRAP, t = 4))
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

    private fun panelTransition(): View {
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

    private fun panelFormat(): View {
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

    // ------------------------------------------------------------------ projetos e navegação

    /** Traz o projeto único da v3.1.x (SharedPreferences) para a pasta de projetos. */
    private fun migrateLegacyProject() {
        val prefs = getSharedPreferences("editor", MODE_PRIVATE)
        val old = prefs.getString("project", null) ?: return
        try {
            val o = JSONObject(old)
            val temConteudo = (o.optJSONArray("clips")?.length() ?: 0) > 0 ||
                (o.optJSONArray("texts")?.length() ?: 0) > 0
            if (!temConteudo) {
                prefs.edit().remove("project").apply()
                return
            }
            o.put("schema", 2)
            o.put("updated", System.currentTimeMillis())
            if (store.write(store.newId(), o.toString())) prefs.edit().remove("project").apply()
        } catch (e: Exception) {
            // mantém o antigo onde está; tenta de novo na próxima abertura
        }
    }

    /** Zera o que está na memória (não mexe em arquivos). */
    private fun resetState() {
        player.pause()
        clips.clear()
        texts.clear()
        selected = 0
        clipPicked = false
        musicUri = null
        musicName = ""
        musicDurMs = 0L
        musicVol = 100
        voiceUri = null
        voiceDurMs = 0L
        voiceVol = 100
        muteOriginal = false
        aspectIdx = 0
        qualityIdx = 0
        fitIdx = 0
        fadeInIdx = 0
        fadeOutIdx = 0
        transType = 0
        transIdx = 1
        projectName = "Meu projeto"
        nameTv.text = projectName
        undoStack.clear()
        redoStack.clear()
        overlayKey = ""
        thumbSavedFor = ""
        afterEdit(0, 0L)
        updateUndoButtons()
    }

    private fun startNewProject(name: String, asp: Int, pick: Boolean = true) {
        saveProject()
        resetState()
        currentId = store.newId()
        projectName = name
        nameTv.text = name
        aspectIdx = asp
        saveProject() // grava já, mesmo vazio, para o projeto aparecer na lista
        showEditor()
        if (pick) pickVideos.launch(arrayOf("video/*"))
    }

    private fun openProject(id: String) {
        val s = store.read(id)
        if (s == null) {
            toast("Projeto não encontrado")
            showHome()
            return
        }
        if (id != currentId) {
            saveProject()
            resetState()
            currentId = id
            loadFromJson(s)
        }
        showEditor()
    }

    private fun goHome() {
        handler.removeCallbacks(saveRunnable)
        player.pause()
        if (recording) stopRecording()
        saveProject()
        showHome()
    }

    private fun showEditor() {
        onHome = false
        setContentView(rootFrame)
        nameTv.text = projectName
        refreshTimeline()
        updateSeekMax()
        updateUndoButtons()
        showTool(currentTool)
        fitSurface(player.videoSize)
        exportBtn.text = if (exporting) "Cancelar" else "Exportar"
    }

    private fun saveThumb(id: String) {
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

    private fun fmtShort(ms: Long): String {
        val t = ms / 1000
        return "%d:%02d".format(t / 60, t % 60)
    }

    private fun showHome() {
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

    private fun projectActions(m: ProjectMeta) {
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

    private fun renameProject(m: ProjectMeta) {
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

    private fun confirmDelete(m: ProjectMeta) {
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

    private fun homeMenu() {
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

    private fun editorMenu() {
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

    private fun clearExportCache() {
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

    private fun aboutDialog() {
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

    private fun addClipsFrom(uris: List<Uri>) {
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
    private fun handleShare(i: Intent?) {
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
    private fun importShared(uris: List<Uri>) {
        startNewProject("Compartilhado ${store.list().size + 1}", 0, false)
        copyAndAdd(uris)
    }

    /** Copia os vídeos para `imports/` em segundo plano e os adiciona ao projeto aberto. */
    private fun copyAndAdd(uris: List<Uri>) {
        toast("Importando ${uris.size} vídeo(s)…")
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
                    toast("Não consegui importar o vídeo")
                } else if (currentId != alvo) {
                    toast("Importação terminou, mas você já mudou de projeto")
                } else {
                    addClipsFrom(copias)
                }
            }
        }
    }

    private fun askNotifThenExport() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            exportar()
        }
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
                shortSide * (if (t.sticker) TextLayout.stickerFactor[t.size] else TextLayout.sizeFactor[t.size])
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
        if (clips.isEmpty()) {
            timeline.posMs = 0L
            timeTv.text = "0:00.0 / 0:00.0"
        }
        timeline.invalidate()
    }

    private fun currentTimelineMs(): Long {
        if (clips.isEmpty() || player.mediaItemCount != clips.size) return 0L
        val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
        return TimelineMath.timelineMs(clips.map { it.effMs }, idx, player.currentPosition, clips[idx].speed)
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

    /** Clipe "escolhido" com um toque na linha do tempo: só então a barra de ações do clipe aparece. */
    private var clipPicked = false
    private var clipGroup: View? = null

    private fun selectClip(i: Int) {
        if (i !in clips.indices) { // toque fora dos clipes
            clipPicked = false
            refreshTimeline()
            return
        }
        clipPicked = !(clipPicked && i == selected) // tocar de novo no mesmo clipe solta
        selected = i
        refreshTimeline()
        showTool(currentTool)
        if (player.mediaItemCount == clips.size && i in clips.indices) player.seekTo(i, 0L)
    }

    private fun selectOnly(i: Int) {
        if (i !in clips.indices || i == selected) return
        clipPicked = false
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
        clipPicked = false
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
            for (c in clips) ca.put(ProjectJson.clipToJson(c))
            o.put("clips", ca)
            val ta = JSONArray()
            for (t in texts) ta.put(ProjectJson.textToJson(t))
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
            o.put("aspect2", aspectIdx)
            o.put("name", projectName)
            o.put("trans", transType)
            o.put("transIdx", transIdx)
            o.put("quality", qualityIdx)
            o.put("fit", fitIdx)
            o.put("fadeIn", fadeInIdx)
            o.put("fadeOut", fadeOutIdx)
            o.put("schema", 2)
            o.put("updated", System.currentTimeMillis())
            o.put("totalMs", totalMs())
            o.put("clipCount", clips.size)
            var id = currentId
            if (id == null) {
                if (clips.isEmpty() && texts.isEmpty()) return
                id = store.newId()
                currentId = id
            }
            if (store.write(id, o.toString())) saveThumb(id)
        } catch (e: Exception) {
            // não conseguiu salvar, segue o jogo
        }
    }

    private fun canRead(uri: Uri): Boolean = try {
        contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    private fun loadFromJson(s: String) {
        var missing = 0
        try {
            val o = JSONObject(s)
            val ca = o.optJSONArray("clips") ?: return
            for (i in 0 until ca.length()) {
                val j = ca.getJSONObject(i)
                val uri = Uri.parse(j.getString("uri"))
                if (!canRead(uri)) {
                    missing++
                    continue
                }
                val c = ProjectJson.clipFromJson(j, uri)
                clips.add(c)
                loadThumb(c)
            }
            if (missing > 0) toast("$missing vídeo(s) não foram encontrados e ficaram de fora")
            val ta = o.optJSONArray("texts")
            if (ta != null) {
                for (i in 0 until ta.length()) {
                    texts.add(ProjectJson.textFromJson(ta.getJSONObject(i)))
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
            aspectIdx = o.optInt("aspect2", 0).coerceIn(0, aspectNames.size - 1)
            projectName = o.optString("name", "Meu projeto")
            nameTv.text = projectName
            transType = o.optInt("trans", 0).coerceIn(0, transNames.size - 1)
            transIdx = o.optInt("transIdx", 1).coerceIn(0, transDur.size - 1)
            qualityIdx = o.optInt("quality", 0).coerceIn(0, qualityNames.size - 1)
            fitIdx = o.optInt("fit", 0).coerceIn(0, fitNames.size - 1)
            fadeInIdx = o.optInt("fadeIn", 0).coerceIn(0, fadeNames.size - 1)
            fadeOutIdx = o.optInt("fadeOut", 0).coerceIn(0, fadeNames.size - 1)
            if (clips.isNotEmpty()) {
                selected = o.optInt("selected", 0).coerceIn(0, clips.size - 1)
                afterEdit(selected, 0L)
            }
        } catch (e: Exception) {
            clips.clear()
            texts.clear()
            toast("Não consegui abrir este projeto por completo")
        }
    }

    // ------------------------------------------------------------------ exportação

    private fun exportSettings() = ExportSettings(
        aspectRatio = aspectRatios[aspectIdx],
        shortSide = qualityShort[qualityIdx],
        cropToFill = fitIdx == 0,
        muteOriginal = muteOriginal,
        fadeInMs = fadeMs[fadeInIdx],
        fadeOutMs = fadeMs[fadeOutIdx],
        transType = transType,
        transHalfMs = transDur[transIdx] / 2,
        music = musicUri?.let { ExtraAudio(it, musicDurMs, musicVol) },
        voice = voiceUri?.let { ExtraAudio(it, voiceDurMs, voiceVol) }
    )

    private fun outputSize(): Pair<Int, Int> = ExportComposition.outputSize(clips, exportSettings())

    private fun exportar() {
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

    private fun setExporting(on: Boolean) {
        exporting = on
        exportBtn.text = if (on) "Cancelar" else "Exportar"
        progressBar.visibility = if (on) View.VISIBLE else View.GONE
        progressBar.progress = 0
    }

    private fun cancelExport() {
        transformer?.cancel()
        setExporting(false)
        ExportService.finish(applicationContext, "Exportação cancelada.", null)
        statusTv.text = "Exportação cancelada."
    }

    /**
     * Salva só o som do clipe (trecho cortado, velocidade, volume e fades do clipe) como .m4a em Música/EditorVideo.
     * Ignora "Remover áudio original": o pedido explícito de extrair vale mais.
     */
    private fun extrairAudio(c: Clip) {
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

    private fun salvarAudio(arquivo: File): Boolean {
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
