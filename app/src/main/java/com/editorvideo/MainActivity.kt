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

    internal val state = ProjectState()
    internal val clips by state::clips
    internal val texts by state::texts
    internal var selected by state::selected
    internal var currentTool = 0
    internal var musicUri by state::musicUri
    internal var musicName by state::musicName
    internal var musicDurMs by state::musicDurMs
    internal var musicVol by state::musicVol
    internal var voiceUri by state::voiceUri
    internal var voiceDurMs by state::voiceDurMs
    internal var voiceVol by state::voiceVol
    internal var muteOriginal by state::muteOriginal
    internal var aspectIdx by state::aspectIdx
    internal var qualityIdx by state::qualityIdx
    internal var fitIdx by state::fitIdx
    internal var fadeInIdx by state::fadeInIdx
    internal var fadeOutIdx by state::fadeOutIdx
    internal var transType by state::transType
    internal var transIdx by state::transIdx
    internal var codecIdx = 0
    internal var bitrateIdx = 1
    internal var projectName by state::projectName
    internal var exporting = false
    internal var extractingAudio = false
    internal var analysingSilence = false
    internal var dragging = false
    internal var recording = false
    internal var previewFx = true
    internal var overlayKey = ""
    internal var currentId: String? = null
    internal var onHome = true
    internal var thumbSavedFor = ""
    internal lateinit var store: ProjectStore

    internal val undoStack = mutableListOf<Snapshot>()
    internal val redoStack = mutableListOf<Snapshot>()

    internal val aspectNames = listOf("Original", "16:9", "9:16", "1:1", "4:3", "3:4", "4:5", "2.35:1")
    internal val aspectRatios = listOf<Float?>(null, 16f / 9f, 9f / 16f, 1f, 4f / 3f, 3f / 4f, 4f / 5f, 2.35f)
    internal val transNames = listOf("Nenhuma", "Fade no preto", "Fade no branco")
    internal val transDur = listOf(500L, 1000L, 1500L, 2000L)
    internal val transDurNames = listOf("0,5 s", "1 s", "1,5 s", "2 s")
    internal val qualityNames = listOf("Original", "1080p", "720p", "480p")
    internal val qualityShort = listOf<Int?>(null, 1080, 720, 480)
    internal val fitNames = listOf("Preencher (corta bordas)", "Ajustar (barras pretas)")
    internal val fadeNames = listOf("Sem", "0,5 s", "1 s", "2 s")
    internal val fadeMs = listOf(0L, 500L, 1000L, 2000L)
    internal val filterNames = listOf(
        "Normal", "P&B", "Vívido", "Quente", "Frio", "Invertido",
        "Sépia", "Cinema", "Vintage", "Dramático", "Noir", "Esmaecido"
    )
    internal val kenNames = listOf("Nenhum", "Aproximar", "Afastar", "Panorâmica →", "Panorâmica ←")
    internal val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
    internal val toolNames = listOf("Cortar", "Velocidade", "Filtros", "Transformar", "Transição", "Texto", "Adesivos", "Mixer", "Formato")
    internal val toolIcons = listOf("✂️", "⏩", "🎨", "🔄", "🔀", "🔤", "😀", "🔊", "📐")
    internal val posNames = listOf("Topo", "Centro", "Base")
    internal val posXNames = listOf("Esquerda", "Centro", "Direita")
    internal val sizeNames = listOf("Pequeno", "Médio", "Grande")
    internal val colorNames = listOf("Branco", "Amarelo", "Vermelho", "Ciano", "Preto")
    internal val colorValues = listOf(Color.WHITE, Color.YELLOW, Color.RED, Color.CYAN, Color.BLACK)
    internal val emojis = listOf(
        "😀", "😂", "😍", "😎", "🥳", "🔥", "❤️", "⭐", "✨", "🎉",
        "👍", "👏", "💯", "🎵", "🌈", "💥", "👑", "🚀", "🌸", "🐶"
    )

    internal val cNavy = Color.parseColor("#04111F")
    internal val cText = Color.parseColor("#EAF6FF")
    internal val cGrey = Color.parseColor("#8FA3BF")
    internal val cAccent = Color.parseColor("#2DE2C8")
    internal val cAccent2 = Color.parseColor("#6C9CFF")
    internal val cGlass = 0x1AFFFFFF
    internal val cGlassStroke = 0x33FFFFFF
    internal val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    internal val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    internal lateinit var previewBox: FrameLayout
    internal lateinit var surface: SurfaceView
    internal lateinit var overlayLayer: FrameLayout
    internal lateinit var emptyState: LinearLayout
    internal lateinit var playBtn: Button
    internal lateinit var undoBtn: Button
    internal lateinit var redoBtn: Button
    internal lateinit var timeTv: TextView
    internal lateinit var timeline: TimelineView
    internal lateinit var nameTv: TextView
    internal lateinit var rootFrame: FrameLayout
    internal lateinit var toolsRow: LinearLayout
    internal lateinit var panel: LinearLayout
    internal lateinit var exportBtn: Button
    internal lateinit var statusTv: TextView
    internal lateinit var progressBar: LiquidProgress
    internal lateinit var player: ExoPlayer

    internal var pulse: ObjectAnimator? = null
    internal var recorder: MediaRecorder? = null
    internal var voiceFile: File? = null
    internal var transformer: Transformer? = null
    internal val handler = Handler(Looper.getMainLooper())
    internal val progressHolder = ProgressHolder()
    internal val bgExec = Executors.newSingleThreadExecutor()
    internal val saveRunnable = Runnable { saveProject() }

    internal val ticker = object : Runnable {
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

    internal val pickVideos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> addClipsFrom(uris) }

    // Galeria do sistema (Photo Picker): o acesso é temporário, então os vídeos são copiados para `imports/`.
    internal val pickGallery =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
            if (uris.isNotEmpty()) copyAndAdd(uris)
        }

    internal fun openGallery() {
        pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    internal val pickMusic =
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

    internal val askNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> exportar() }

    internal val askMic =
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

    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    internal fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    internal fun glass(radius: Int = 14, fill: Int = cGlass, stroke: Int = cGlassStroke): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    internal fun gradient(radius: Int = 14): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cAccent, cAccent2)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    internal fun gradientOval(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(cAccent, cAccent2)).apply {
            shape = GradientDrawable.OVAL
        }

    internal fun lpm(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    internal fun tv(text: String, size: Float = 14f, color: Int = cText) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    internal fun section(text: String) = tv(text.uppercase(), 11f, cAccent).apply {
        letterSpacing = 0.12f
        setTypeface(typeface, Typeface.BOLD)
    }

    internal fun note(text: String) = tv(text, 11f, cGrey).apply { setLineSpacing(0f, 1.15f) }

    internal fun press(v: View) {
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

    internal fun styleBtn(b: Button, on: Boolean) {
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

    internal fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
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

    internal fun iconBtn(symbol: String, onClick: () -> Unit) = btn(symbol, onClick).apply {
        textSize = 17f
        background = glass(21)
        minimumHeight = dp(42)
        minimumWidth = dp(42)
        setPadding(0, 0, 0, 0)
    }

    internal fun vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    internal fun hbox() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    internal fun hscroll(content: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(content)
    }

    internal fun hint() = tv("Adicione um vídeo para usar esta ferramenta.", 13f, cGrey)

    internal fun styleSeek(s: SeekBar) {
        s.progressTintList = ColorStateList.valueOf(cAccent)
        s.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        s.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
    }

    internal fun seek(
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

    internal fun labeledSeek(
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

    internal fun chipRow(labels: List<String>, current: Int, onPick: (Int) -> Unit): View {
        val r = hbox()
        labels.forEachIndexed { i, n ->
            val b = btn(n) { onPick(i) }
            styleBtn(b, i == current)
            r.addView(b, lpm(WRAP, WRAP, t = 6, r = 6))
        }
        return hscroll(r)
    }

    internal fun showStyled(b: AlertDialog.Builder): AlertDialog {
        val d = b.create()
        d.window?.setBackgroundDrawable(glass(24, Color.parseColor("#F2081428"), 0x33FFFFFF))
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cAccent)
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(cAccent2)
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(cGrey)
        return d
    }

    internal fun fmt(ms: Long): String {
        val m = ms / 60000
        val s = (ms % 60000) / 1000.0
        return "%d:%04.1f".format(m, s)
    }

    internal fun fmtSpeed(s: Float): String = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()

    internal fun parseMs(s: String): Long? {
        val v = s.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return (v * 1000).toLong()
    }

    internal fun queryName(uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path ?: "").name.substringAfter('_', "arquivo")
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return "arquivo"
    }

    internal fun keepAccess(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // sem permissão persistente, tudo bem
        }
    }

    // ------------------------------------------------------------------ interface

    internal fun chipGroup(labels: List<String>, current: Int, onPick: (Int) -> Unit): View {
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

    internal fun editField(h: String, value: String) = EditText(this).apply {
        hint = h
        setText(value)
        setTextColor(cText)
        setHintTextColor(cGrey)
        backgroundTintList = ColorStateList.valueOf(cAccent)
    }

    internal fun videoBitrate(w: Int, h: Int): Int {
        return TimelineMath.videoBitrate(w, h, bitrateIdx, codecIdx)
    }

    internal fun cutAtPlayhead(keepRight: Boolean) {
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

    internal fun refreshTimeline() {
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

    internal val waves = HashMap<String, WaveData>()
    internal val waveBusy = HashSet<String>()
    internal val waveExec = Executors.newSingleThreadExecutor { r ->
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
    internal fun waveFor(uri: Uri, durMs: Long): WaveData? {
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

    internal fun snapshot() = state.snapshot()

    internal fun restore(s: Snapshot) = state.restore(s)

    internal fun pushUndo() {
        undoStack.add(snapshot())
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
        updateUndoButtons()
        scheduleSave()
    }

    internal fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(snapshot())
        restore(undoStack.removeAt(undoStack.size - 1))
        afterEdit(selected, 0L)
        updateUndoButtons()
    }

    internal fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(snapshot())
        restore(redoStack.removeAt(redoStack.size - 1))
        afterEdit(selected, 0L)
        updateUndoButtons()
    }

    internal fun updateUndoButtons() {
        undoBtn.alpha = if (undoStack.isEmpty()) 0.35f else 1f
        redoBtn.alpha = if (redoStack.isEmpty()) 0.35f else 1f
    }

    // ------------------------------------------------------------------ painéis das ferramentas

    internal fun sel(): Clip? = clips.getOrNull(selected)

    // ------------------------------------------------------------------ projetos e navegação

    /** Traz o projeto único da v3.1.x (SharedPreferences) para a pasta de projetos. */
    internal fun migrateLegacyProject() {
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
    internal fun resetState() {
        player.pause()
        state.reset()
        clipPicked = false
        nameTv.text = projectName
        undoStack.clear()
        redoStack.clear()
        overlayKey = ""
        thumbSavedFor = ""
        afterEdit(0, 0L)
        updateUndoButtons()
    }

    internal fun startNewProject(name: String, asp: Int, pick: Boolean = true) {
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

    internal fun openProject(id: String) {
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

    internal fun goHome() {
        handler.removeCallbacks(saveRunnable)
        player.pause()
        if (recording) stopRecording()
        saveProject()
        showHome()
    }

    internal fun showEditor() {
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

    // ------------------------------------------------------------------ prévia de texto e adesivos

    // ------------------------------------------------------------------ edição da linha do tempo

    internal fun totalMs(): Long = state.totalMs()

    internal fun updateSeekMax() {
        if (clips.isEmpty()) {
            timeline.posMs = 0L
            timeTv.text = "0:00.0 / 0:00.0"
        }
        timeline.invalidate()
    }

    internal fun currentTimelineMs(): Long {
        if (clips.isEmpty() || player.mediaItemCount != clips.size) return 0L
        val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
        return TimelineMath.timelineMs(clips.map { it.effMs }, idx, player.currentPosition, clips[idx].speed)
    }

    internal fun rebuildPlayer(index: Int, posMs: Long) {
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

    internal fun afterEdit(playIndex: Int, posMs: Long) {
        overlayKey = ""
        rebuildPlayer(playIndex, posMs)
        refreshTimeline()
        updateSeekMax()
        showTool(currentTool)
        scheduleSave()
    }

    internal fun applyPlaybackSpeed() {
        val i = player.currentMediaItemIndex
        if (i in clips.indices) player.setPlaybackSpeed(clips[i].speed)
    }

    internal fun clipVolume(c: Clip?): Float {
        if (muteOriginal || recording) return 0f
        if (c == null) return 1f
        return (c.vol / 100f).coerceAtMost(1f)
    }

    internal fun applyVolume() {
        player.volume = clipVolume(clips.getOrNull(player.currentMediaItemIndex))
    }

    internal fun applyPreviewEffects(index: Int = player.currentMediaItemIndex) {
        val c = clips.getOrNull(index)
        try {
            player.setVideoEffects(if (c == null || !previewFx) emptyList() else videoEffects(c, false))
        } catch (e: Exception) {
            previewFx = false
        }
    }

    /** Clipe "escolhido" com um toque na linha do tempo: só então a barra de ações do clipe aparece. */
    internal var clipPicked = false
    internal var clipGroup: View? = null

    internal fun selectClip(i: Int) {
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

    internal fun selectOnly(i: Int) {
        if (i !in clips.indices || i == selected) return
        clipPicked = false
        selected = i
        refreshTimeline()
        showTool(currentTool)
    }

    internal fun seekToTimeline(pos: Long) {
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

    internal fun togglePlay() {
        if (clips.isEmpty()) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L)
            player.play()
        }
    }

    internal fun trimDone(isEnd: Boolean) {
        val c = sel() ?: return
        val len = c.endMs - c.startMs
        afterEdit(selected, if (isEnd) (len - 1500L).coerceAtLeast(0L) else 0L)
        loadThumb(c)
    }

    internal fun splitClip() {
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

    internal fun dupClip() {
        val c = sel() ?: return
        pushUndo()
        clips.add(selected + 1, c.copy())
        selected += 1
        afterEdit(selected, 0L)
    }

    internal fun delClip() {
        if (sel() == null) return
        pushUndo()
        clipPicked = false
        clips.removeAt(selected)
        selected = selected.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
        afterEdit(selected, 0L)
    }

    internal fun moveClip(d: Int) {
        val j = selected + d
        if (sel() == null || j !in clips.indices) return
        pushUndo()
        val c = clips.removeAt(selected)
        clips.add(j, c)
        selected = j
        afterEdit(j, 0L)
    }

    internal fun fitSurface(v: VideoSize) {
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

    @Suppress("DEPRECATION")
    internal fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    // ------------------------------------------------------------------ projeto (salvar e restaurar)

    internal fun canRead(uri: Uri): Boolean = try {
        contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------------ exportação

    internal fun exportSettings() = ExportSettings(
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

    internal fun outputSize(): Pair<Int, Int> = ExportComposition.outputSize(clips, exportSettings())

}
