package com.editorvideo

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
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
import java.io.File
import java.util.concurrent.Executors
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
        val color: Int
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

    // ------------------------------------------------------------------ estado

    private val clips = mutableListOf<Clip>()
    private val texts = mutableListOf<TextItem>()
    private var selected = 0
    private var currentTool = 0
    private var musicUri: Uri? = null
    private var musicName = ""
    private var musicDurMs = 0L
    private var muteOriginal = false
    private var aspectIdx = 0
    private var qualityIdx = 0
    private var fitIdx = 0
    private var exporting = false
    private var dragging = false

    private val aspectNames = listOf("Original", "9:16 (Reels/Shorts/TikTok)", "16:9 (YouTube)", "1:1 (quadrado)", "4:5 (feed)")
    private val aspectRatios = listOf<Float?>(null, 9f / 16f, 16f / 9f, 1f, 4f / 5f)
    private val qualityNames = listOf("Original", "1080p", "720p", "480p")
    private val qualityShort = listOf<Int?>(null, 1080, 720, 480)
    private val fitNames = listOf("Preencher (corta as bordas)", "Ajustar (barras pretas)")
    private val filterNames = listOf("Normal", "P&B", "Vívido", "Quente", "Frio", "Invertido")
    private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
    private val toolNames = listOf("Cortar", "Velocidade", "Filtros", "Girar", "Texto", "Música", "Formato")
    private val posNames = listOf("Topo", "Centro", "Base")
    private val posY = listOf(0.8f, 0f, -0.8f)
    private val sizeNames = listOf("Pequeno", "Médio", "Grande")
    private val sizeFactor = listOf(0.045f, 0.07f, 0.10f)
    private val colorNames = listOf("Branco", "Amarelo", "Vermelho", "Ciano", "Preto")
    private val colorValues = listOf(Color.WHITE, Color.YELLOW, Color.RED, Color.CYAN, Color.BLACK)

    private val cBg = Color.parseColor("#14141C")
    private val cChip = Color.parseColor("#2B2B3A")
    private val cAccent = Color.parseColor("#7C4DFF")
    private val cGrey = Color.parseColor("#9A9AAA")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var previewBox: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var playBtn: Button
    private lateinit var timeTv: TextView
    private lateinit var timeSeek: SeekBar
    private lateinit var timelineBox: LinearLayout
    private lateinit var toolsRow: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var exportBtn: Button
    private lateinit var statusTv: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var player: ExoPlayer

    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progressHolder = ProgressHolder()
    private val bg = Executors.newSingleThreadExecutor()

    private val ticker = object : Runnable {
        override fun run() {
            if (!dragging && clips.isNotEmpty() && player.mediaItemCount == clips.size) {
                val idx = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
                var cum = 0L
                for (k in 0 until idx) cum += clips[k].effMs
                val pos = cum + (player.currentPosition.coerceAtLeast(0L) / clips[idx].speed).toLong()
                timeSeek.progress = pos.toInt().coerceAtMost(timeSeek.max)
                timeTv.text = "${fmt(pos)} / ${fmt(totalMs())}"
            }
            if (exporting) {
                val t = transformer
                if (t != null && t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progressBar.progress = progressHolder.progress
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    private val pickVideos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val firstNew = clips.size
            uris.forEach { addClip(it) }
            if (clips.size > firstNew) {
                selected = firstNew
                afterEdit(firstNew, 0L)
            }
        }

    private val pickMusic =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(this, uri)
                    musicDurMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
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

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyPlaybackSpeed()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                fitSurface(videoSize)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.text = if (isPlaying) "❚❚" else "▶"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(0, 0L)
                    player.pause()
                }
            }
        })

        buildUi()
        handler.post(ticker)
    }

    override fun onStop() {
        player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        transformer?.cancel()
        player.release()
        bg.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ helpers de UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun rounded(color: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
        if (stroke != 0) setStroke(dp(2), stroke)
    }

    private fun lpm(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    private fun tv(text: String, size: Float = 14f, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        background = rounded(cChip)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = dp(40)
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
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

    private fun seek(max: Int, value: Int, onChange: (Int, Boolean) -> Unit, onStop: () -> Unit) =
        SeekBar(this).apply {
            this.max = max
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    onChange(p, fromUser)
                }

                override fun onStartTrackingTouch(s: SeekBar) {}

                override fun onStopTrackingTouch(s: SeekBar) {
                    onStop()
                }
            })
        }

    private fun pick(title: String, options: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, i -> onPick(i) }
            .show()
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

    // ------------------------------------------------------------------ interface

    private fun buildUi() {
        val root = vbox().apply {
            setBackgroundColor(cBg)
            setPadding(dp(12), dp(28), dp(12), dp(28))
        }

        val top = hbox()
        top.addView(
            tv("Editor de Vídeo", 20f).apply { setTypeface(typeface, Typeface.BOLD) },
            lpm(0, WRAP, weight = 1f)
        )
        top.addView(btn("+ Vídeos") { pickVideos.launch(arrayOf("video/*")) })
        exportBtn = btn("Exportar") { if (exporting) cancelExport() else exportar() }
        exportBtn.background = rounded(cAccent)
        top.addView(exportBtn, lpm(WRAP, WRAP, l = 8))
        root.addView(top)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar)
        statusTv = tv("", 12f, cGrey)
        root.addView(statusTv)

        previewBox = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surface = SurfaceView(this)
        previewBox.addView(surface, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(previewBox, lpm(MATCH, dp(240), t = 8))
        player.setVideoSurfaceView(surface)

        val transport = hbox()
        playBtn = btn("▶") { togglePlay() }
        transport.addView(playBtn)
        timeSeek = SeekBar(this).apply {
            max = 1
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
        transport.addView(timeSeek, lpm(0, WRAP, l = 6, weight = 1f))
        root.addView(transport, lpm(MATCH, WRAP, t = 6))
        timeTv = tv("0:00.0 / 0:00.0", 12f, cGrey)
        root.addView(timeTv)

        timelineBox = hbox()
        root.addView(
            HorizontalScrollView(this).apply { addView(timelineBox) },
            lpm(MATCH, WRAP, t = 8)
        )

        val actions = hbox()
        actions.addView(btn("✂ Dividir") { splitClip() }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("Duplicar") { dupClip() }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("Excluir") { delClip() }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("◀ Mover") { moveClip(-1) }, lpm(WRAP, WRAP, r = 6))
        actions.addView(btn("Mover ▶") { moveClip(1) }, lpm(WRAP, WRAP, r = 6))
        root.addView(hscroll(actions), lpm(MATCH, WRAP, t = 8))

        toolsRow = hbox()
        root.addView(hscroll(toolsRow), lpm(MATCH, WRAP, t = 12))

        panel = vbox().apply { setPadding(0, dp(10), 0, dp(10)) }
        root.addView(panel)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(cBg)
            addView(root)
        })

        buildToolTabs()
        refreshTimeline()
        showTool(0)
    }

    private fun buildToolTabs() {
        toolsRow.removeAllViews()
        toolNames.forEachIndexed { i, n ->
            val b = btn(n) {
                currentTool = i
                buildToolTabs()
                showTool(i)
            }
            b.background = rounded(if (i == currentTool) cAccent else cChip)
            toolsRow.addView(b, lpm(WRAP, WRAP, r = 6))
        }
    }

    private fun showTool(i: Int) {
        panel.removeAllViews()
        val v = when (i) {
            0 -> panelTrim()
            1 -> panelSpeed()
            2 -> panelFilters()
            3 -> panelRotate()
            4 -> panelText()
            5 -> panelMusic()
            else -> panelFormat()
        }
        panel.addView(v)
    }

    private fun refreshTimeline() {
        timelineBox.removeAllViews()
        if (clips.isEmpty()) {
            timelineBox.addView(tv("Toque em “+ Vídeos” para começar", 13f, cGrey))
            return
        }
        clips.forEachIndexed { i, c ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = rounded(cChip, if (i == selected) cAccent else 0)
                setOnClickListener { selectClip(i) }
            }
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
                val th = c.thumb
                if (th != null) setImageBitmap(th)
            }
            card.addView(img, LinearLayout.LayoutParams(dp(96), dp(64)))
            val extra = if (c.speed != 1f) " · ${fmtSpeed(c.speed)}x" else ""
            card.addView(tv("${i + 1} · ${fmt(c.effMs)}$extra", 11f))
            timelineBox.addView(card, lpm(WRAP, WRAP, r = 6))
        }
    }

    // ------------------------------------------------------------------ painéis das ferramentas

    private fun sel(): Clip? = clips.getOrNull(selected)

    private fun panelTrim(): View {
        val c = sel() ?: return hint()
        val box = vbox()
        val l1 = tv("")
        val l2 = tv("")
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
            }) { trimDone(false) }
        )
        box.addView(l2)
        box.addView(
            seek(c.durationMs.toInt(), c.endMs.toInt(), { p, user ->
                if (user) {
                    c.endMs = p.toLong().coerceAtLeast(c.startMs + 300L).coerceAtMost(c.durationMs)
                    upd()
                }
            }) { trimDone(true) }
        )
        box.addView(tv("Arraste os controles para cortar o começo e o fim. Para cortar no meio: posicione o vídeo e use ✂ Dividir.", 11f, cGrey))
        return box
    }

    private fun panelSpeed(): View {
        val c = sel() ?: return hint()
        val box = vbox()
        box.addView(tv("Velocidade do clip selecionado (o áudio acompanha)."))
        val r = hbox()
        speeds.forEach { s ->
            val b = btn("${fmtSpeed(s)}x") {
                c.speed = s
                if (player.currentMediaItemIndex == selected) player.setPlaybackSpeed(s)
                refreshTimeline()
                updateSeekMax()
                showTool(currentTool)
            }
            b.background = rounded(if (c.speed == s) cAccent else cChip)
            r.addView(b, lpm(WRAP, WRAP, t = 6, r = 6))
        }
        box.addView(hscroll(r))
        return box
    }

    private fun panelFilters(): View {
        val c = sel() ?: return hint()
        val box = vbox()
        val r = hbox()
        filterNames.forEachIndexed { i, n ->
            val b = btn(n) {
                c.filter = i
                showTool(currentTool)
            }
            b.background = rounded(if (c.filter == i) cAccent else cChip)
            r.addView(b, lpm(WRAP, WRAP, r = 6))
        }
        box.addView(hscroll(r))
        val lb = tv("")
        val lc = tv("")
        fun lbl() {
            lb.text = "Brilho: ${c.brightness}"
            lc.text = "Contraste: ${c.contrast}"
        }
        lbl()
        box.addView(lb, lpm(WRAP, WRAP, t = 8))
        box.addView(seek(100, c.brightness + 50, { p, u -> if (u) { c.brightness = p - 50; lbl() } }) {})
        box.addView(lc)
        box.addView(seek(100, c.contrast + 50, { p, u -> if (u) { c.contrast = p - 50; lbl() } }) {})
        box.addView(tv("Os filtros aparecem no vídeo exportado (a prévia mostra o original).", 11f, cGrey))
        return box
    }

    private fun panelRotate(): View {
        val c = sel() ?: return hint()
        val box = vbox()
        box.addView(tv("Rotação: ${c.rotation}°  ·  Espelhado: ${if (c.flip) "sim" else "não"}"))
        val r = hbox()
        r.addView(btn("↻ Girar 90°") {
            c.rotation = (c.rotation + 90) % 360
            showTool(currentTool)
        }, lpm(WRAP, WRAP, t = 6, r = 6))
        r.addView(btn("⇋ Espelhar") {
            c.flip = !c.flip
            showTool(currentTool)
        }, lpm(WRAP, WRAP, t = 6, r = 6))
        r.addView(btn("Redefinir") {
            c.rotation = 0
            c.flip = false
            showTool(currentTool)
        }, lpm(WRAP, WRAP, t = 6, r = 6))
        box.addView(hscroll(r))
        box.addView(tv("O resultado aparece no vídeo exportado.", 11f, cGrey))
        return box
    }

    private fun panelText(): View {
        val box = vbox()
        texts.forEachIndexed { i, t ->
            val r = hbox()
            r.addView(tv("“${t.text}”  ${fmt(t.startMs)} – ${fmt(t.endMs)}", 13f), lpm(0, WRAP, weight = 1f))
            r.addView(btn("Editar") { textDialog(i) }, lpm(WRAP, WRAP, r = 6))
            r.addView(btn("Remover") {
                texts.removeAt(i)
                showTool(currentTool)
            })
            box.addView(r, lpm(MATCH, WRAP, b = 6))
        }
        box.addView(btn("+ Adicionar texto") { textDialog(-1) }, lpm(WRAP, WRAP, t = 4))
        box.addView(tv("O tempo do texto vale para o vídeo final (linha do tempo inteira).", 11f, cGrey), lpm(MATCH, WRAP, t = 6))
        return box
    }

    private fun panelMusic(): View {
        val box = vbox()
        val mu = musicUri
        box.addView(tv(if (mu == null) "Nenhuma música adicionada." else "Música: $musicName (${fmt(musicDurMs)})"))
        val r = hbox()
        r.addView(btn("Escolher música") { pickMusic.launch(arrayOf("audio/*")) }, lpm(WRAP, WRAP, t = 6, r = 6))
        if (mu != null) {
            r.addView(btn("Remover música") {
                musicUri = null
                showTool(currentTool)
            }, lpm(WRAP, WRAP, t = 6))
        }
        box.addView(hscroll(r))
        val cb = CheckBox(this).apply {
            text = "Remover áudio original dos vídeos"
            setTextColor(Color.WHITE)
            isChecked = muteOriginal
            setOnCheckedChangeListener { _, v ->
                muteOriginal = v
                player.volume = if (v) 0f else 1f
            }
        }
        box.addView(cb, lpm(WRAP, WRAP, t = 6))
        box.addView(tv("A música entra no começo e é cortada no fim do vídeo. Só aparece no vídeo exportado.", 11f, cGrey))
        return box
    }

    private fun panelFormat(): View {
        val box = vbox()
        box.addView(btn("Proporção: ${aspectNames[aspectIdx]}") {
            pick("Proporção", aspectNames) { aspectIdx = it; showTool(currentTool) }
        }, lpm(MATCH, WRAP, b = 6))
        box.addView(btn("Qualidade: ${qualityNames[qualityIdx]}") {
            pick("Qualidade (lado menor)", qualityNames) { qualityIdx = it; showTool(currentTool) }
        }, lpm(MATCH, WRAP, b = 6))
        box.addView(btn("Enquadramento: ${fitNames[fitIdx]}") {
            pick("Enquadramento", fitNames) { fitIdx = it; showTool(currentTool) }
        }, lpm(MATCH, WRAP, b = 6))
        box.addView(tv("Vale para o vídeo exportado. Qualidade menor exporta mais rápido.", 11f, cGrey))
        return box
    }

    private fun textDialog(index: Int) {
        val existing = texts.getOrNull(index)
        var pos = existing?.pos ?: 2
        var size = existing?.size ?: 1
        var col = colorValues.indexOf(existing?.color ?: Color.WHITE).coerceAtLeast(0)
        val now = currentTimelineMs()

        val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val et = EditText(this).apply {
            hint = "Texto"
            setText(existing?.text ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val st = EditText(this).apply {
            hint = "Início (s)"
            inputType = numType
            setText("%.1f".format((existing?.startMs ?: now) / 1000.0))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val en = EditText(this).apply {
            hint = "Fim (s)"
            inputType = numType
            setText("%.1f".format((existing?.endMs ?: (now + 3000L)) / 1000.0))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val bPos = btn("Posição: ${posNames[pos]}") {}
        bPos.setOnClickListener {
            pos = (pos + 1) % posNames.size
            bPos.text = "Posição: ${posNames[pos]}"
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
        box.addView(et)
        box.addView(st)
        box.addView(en)
        box.addView(bPos, lpm(MATCH, WRAP, t = 6))
        box.addView(bSize, lpm(MATCH, WRAP, t = 6))
        box.addView(bCol, lpm(MATCH, WRAP, t = 6))

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Novo texto" else "Editar texto")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("OK") { _, _ ->
                val txt = et.text.toString().trim()
                if (txt.isNotEmpty()) {
                    val s = parseMs(st.text.toString()) ?: 0L
                    var e = parseMs(en.text.toString()) ?: (s + 3000L)
                    if (e <= s) e = s + 1000L
                    val item = TextItem(txt, s, e, pos, size, colorValues[col])
                    if (existing == null) texts.add(item) else texts[index] = item
                    showTool(currentTool)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------------ edição da linha do tempo

    private fun totalMs(): Long = clips.sumOf { it.effMs }

    private fun updateSeekMax() {
        timeSeek.max = totalMs().toInt().coerceAtLeast(1)
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
        player.setMediaItems(items, i, posMs)
        player.prepare()
        player.setPlaybackSpeed(clips[i].speed)
        player.volume = if (muteOriginal) 0f else 1f
    }

    private fun afterEdit(playIndex: Int, posMs: Long) {
        rebuildPlayer(playIndex, posMs)
        refreshTimeline()
        updateSeekMax()
        showTool(currentTool)
    }

    private fun applyPlaybackSpeed() {
        val i = player.currentMediaItemIndex
        if (i in clips.indices) player.setPlaybackSpeed(clips[i].speed)
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
        clips.add(selected + 1, c.copy())
        selected += 1
        afterEdit(selected, 0L)
    }

    private fun delClip() {
        if (sel() == null) return
        clips.removeAt(selected)
        selected = selected.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
        afterEdit(selected, 0L)
    }

    private fun moveClip(d: Int) {
        val j = selected + d
        if (sel() == null || j !in clips.indices) return
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
        bg.execute {
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
            surface.layoutParams = FrameLayout.LayoutParams(
                (vw * scale).toInt(), (vh * scale).toInt(), Gravity.CENTER
            )
        }
    }

    // ------------------------------------------------------------------ exportação

    private fun filterEffects(i: Int): List<Effect> = when (i) {
        1 -> listOf(RgbFilter.createGrayscaleFilter())
        2 -> listOf(HslAdjustment.Builder().adjustSaturation(35f).build(), Contrast(0.15f))
        3 -> listOf(RgbAdjustment.Builder().setRedScale(1.15f).setGreenScale(1.0f).setBlueScale(0.88f).build())
        4 -> listOf(RgbAdjustment.Builder().setRedScale(0.88f).setGreenScale(1.0f).setBlueScale(1.15f).build())
        5 -> listOf(RgbFilter.createInvertedFilter())
        else -> emptyList()
    }

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

    private fun exportar() {
        if (clips.isEmpty()) {
            toast("Adicione vídeos primeiro")
            return
        }
        player.pause()

        val items = ArrayList<EditedMediaItem>()
        for (c in clips) {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(c.startMs)
                .setEndPositionMs(c.endMs)
                .build()
            val mi = MediaItem.Builder().setUri(c.uri).setClippingConfiguration(clipping).build()

            val v = ArrayList<Effect>()
            if (c.rotation != 0 || c.flip) {
                v.add(
                    ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(-c.rotation.toFloat())
                        .setScale(if (c.flip) -1f else 1f, 1f)
                        .build()
                )
            }
            v.addAll(filterEffects(c.filter))
            if (c.brightness != 0) v.add(Brightness(c.brightness / 100f))
            if (c.contrast != 0) v.add(Contrast(c.contrast / 100f))

            val a = ArrayList<AudioProcessor>()
            if (c.speed != 1f) {
                v.add(SpeedChangeEffect(c.speed))
                if (!muteOriginal) {
                    val sonic = SonicAudioProcessor()
                    sonic.setSpeed(c.speed)
                    a.add(sonic)
                }
            }

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
            sp.setSpan(ForegroundColorSpan(t.color), 0, len, flags)
            sp.setSpan(AbsoluteSizeSpan((shortOut * sizeFactor[t.size]).toInt()), 0, len, flags)
            val bgColor = if (t.color == Color.BLACK) 0x99FFFFFF.toInt() else 0x66000000
            sp.setSpan(BackgroundColorSpan(bgColor), 0, len, flags)
            val y = posY[t.pos]
            val shown = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).build()
            val hidden = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).setAlphaScale(0f).build()
            overlays.add(TimedText(t, sp, shown, hidden))
        }
        if (overlays.isNotEmpty()) compEffects.add(OverlayEffect(ImmutableList.copyOf(overlays)))

        val seqs = ArrayList<EditedMediaItemSequence>()
        seqs.add(EditedMediaItemSequence(items))
        val mu = musicUri
        if (mu != null) {
            val cb = MediaItem.ClippingConfiguration.Builder()
            if (musicDurMs > 0L) cb.setEndPositionMs(min(musicDurMs, totalMs()))
            val mMedia = MediaItem.Builder().setUri(mu).setClippingConfiguration(cb.build()).build()
            val mItem = EditedMediaItem.Builder(mMedia).setRemoveVideo(true).build()
            seqs.add(EditedMediaItemSequence(listOf(mItem)))
        }

        val composition = Composition.Builder(seqs)
            .setEffects(Effects(emptyList(), compEffects))
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
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Vídeo exportado")
                        .setMessage("Salvo em Filmes/EditorVideo.")
                        .setPositiveButton("Abrir") { _, _ ->
                            try {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri ?: Uri.fromFile(saida), "video/mp4")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            } catch (e: Exception) {
                                toast("Abra pela galeria: Filmes/EditorVideo")
                            }
                        }
                        .setNegativeButton("Fechar", null)
                        .show()
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

        statusTv.text = "Exportando..."
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
        return uri
    }
}
