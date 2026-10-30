package com.editorvideo

import android.content.ContentValues
import android.graphics.Color
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
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
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

class MainActivity : ComponentActivity() {

    private class Clip(
        val uri: Uri,
        val durationMs: Long,
        val startEt: EditText,
        val endEt: EditText,
        val row: View
    )

    private val clips = mutableListOf<Clip>()
    private lateinit var clipsBox: LinearLayout
    private lateinit var textoEt: EditText
    private lateinit var muteCb: CheckBox
    private lateinit var exportBtn: Button
    private lateinit var statusTv: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var videoView: VideoView

    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progressHolder = ProgressHolder()

    private val pollProgress = object : Runnable {
        override fun run() {
            val t = transformer ?: return
            if (t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                progressBar.progress = progressHolder.progress
            }
            handler.postDelayed(this, 300)
        }
    }

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach { addClip(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Editor de Vídeo"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Os vídeos são unidos na ordem em que você os adiciona. Início/fim em segundos (vazio = vídeo inteiro)."
            textSize = 13f
        })

        root.addView(Button(this).apply {
            text = "Adicionar vídeos"
            setOnClickListener { picker.launch(arrayOf("video/*")) }
        })

        clipsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(clipsBox)

        textoEt = EditText(this).apply { hint = "Texto na tela (opcional)" }
        root.addView(textoEt)

        muteCb = CheckBox(this).apply { text = "Remover áudio" }
        root.addView(muteCb)

        exportBtn = Button(this).apply {
            text = "Exportar vídeo"
            setOnClickListener { exportar() }
        }
        root.addView(exportBtn)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar)

        statusTv = TextView(this)
        root.addView(statusTv)

        videoView = VideoView(this).apply {
            visibility = View.GONE
            setMediaController(MediaController(this@MainActivity))
        }
        root.addView(
            videoView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300))
        )

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollProgress)
        transformer?.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return "vídeo"
    }

    private fun queryDuration(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            r.release()
        }
    }

    private fun addClip(uri: Uri) {
        val name = queryName(uri)
        val dur = queryDuration(uri)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = "$name  (${"%.1f".format(dur / 1000.0)} s)"
        })

        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val startEt = EditText(this).apply { hint = "início (s)"; inputType = numType }
        val endEt = EditText(this).apply { hint = "fim (s)"; inputType = numType }
        val weight = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        times.addView(startEt, weight)
        times.addView(endEt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val clip = Clip(uri, dur, startEt, endEt, row)
        times.addView(Button(this).apply {
            text = "Remover"
            setOnClickListener {
                clips.remove(clip)
                clipsBox.removeView(row)
            }
        })
        row.addView(times)

        clips.add(clip)
        clipsBox.addView(row)
    }

    private fun parseMs(s: String): Long? {
        val v = s.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return (v * 1000).toLong()
    }

    private fun exportar() {
        if (clips.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos um vídeo", Toast.LENGTH_SHORT).show()
            return
        }

        val items = ArrayList<EditedMediaItem>()
        for ((index, c) in clips.withIndex()) {
            val start = parseMs(c.startEt.text.toString())
            var end = parseMs(c.endEt.text.toString())
            if (end != null && c.durationMs > 0 && end > c.durationMs) end = c.durationMs
            if (start != null && end != null && end <= start) {
                Toast.makeText(this, "Vídeo ${index + 1}: o fim precisa ser maior que o início", Toast.LENGTH_LONG).show()
                return
            }

            val clipping = MediaItem.ClippingConfiguration.Builder().apply {
                if (start != null) setStartPositionMs(start)
                if (end != null) setEndPositionMs(end)
            }.build()

            val mediaItem = MediaItem.Builder()
                .setUri(c.uri)
                .setClippingConfiguration(clipping)
                .build()

            items.add(
                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(muteCb.isChecked)
                    .build()
            )
        }

        val videoEffects = ArrayList<Effect>()
        val texto = textoEt.text.toString().trim()
        if (texto.isNotEmpty()) {
            val span = SpannableString(texto)
            span.setSpan(ForegroundColorSpan(Color.WHITE), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(AbsoluteSizeSpan(72), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val settings = OverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, -0.8f)
                .build()
            videoEffects.add(
                OverlayEffect(ImmutableList.of(TextOverlay.createStaticTextOverlay(span, settings)))
            )
        }

        val sequence = EditedMediaItemSequence(items)
        val composition = Composition.Builder(listOf(sequence))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val saida = File(cacheDir, "saida_${System.currentTimeMillis()}.mp4")

        val t = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacks(pollProgress)
                    progressBar.visibility = View.GONE
                    exportBtn.isEnabled = true
                    val uri = salvarNaGaleria(saida)
                    statusTv.text = "Pronto! Salvo em Filmes/EditorVideo."
                    videoView.visibility = View.VISIBLE
                    videoView.setVideoURI(uri ?: Uri.fromFile(saida))
                    videoView.start()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    handler.removeCallbacks(pollProgress)
                    progressBar.visibility = View.GONE
                    exportBtn.isEnabled = true
                    statusTv.text = "Erro ao exportar: ${exportException.message}"
                }
            })
            .build()
        transformer = t

        exportBtn.isEnabled = false
        statusTv.text = "Exportando..."
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        videoView.visibility = View.GONE
        t.start(composition, saida.absolutePath)
        handler.post(pollProgress)
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
