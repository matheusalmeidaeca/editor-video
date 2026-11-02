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

// Salvar e restaurar o projeto atual (saiu do MainActivity na v3.3.1).

internal fun MainActivity.scheduleSave() {
    handler.removeCallbacks(saveRunnable)
    handler.postDelayed(saveRunnable, 700)
}

internal fun MainActivity.saveProject() {
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
        o.put("logoUri", logoUri?.toString() ?: "")
        o.put("logoPos", logoPos)
        o.put("logoSize", logoSize)
        o.put("logoAlpha", logoAlpha)
        o.put("mFadeIn", musicFadeIn)
        o.put("mFadeOut", musicFadeOut)
        o.put("mLoop", musicLoop)
        o.put("vig", vignette)
        o.put("bars", bars)
        o.put("duck", duck)
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

internal fun MainActivity.loadFromJson(s: String) {
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
        val lu = o.optString("logoUri", "")
        if (lu.isNotEmpty() && canRead(Uri.parse(lu))) logoUri = Uri.parse(lu)
        logoPos = o.optInt("logoPos", 3).coerceIn(0, logoPosNames.size - 1)
        logoSize = o.optInt("logoSize", 1).coerceIn(0, LogoMath.sizeFrac.size - 1)
        logoAlpha = o.optInt("logoAlpha", 0).coerceIn(0, LogoMath.alphaValues.size - 1)
        musicFadeIn = o.optInt("mFadeIn", 0).coerceIn(0, musicFadeMs.size - 1)
        musicFadeOut = o.optInt("mFadeOut", 0).coerceIn(0, musicFadeMs.size - 1)
        musicLoop = o.optBoolean("mLoop", false)
        vignette = o.optInt("vig", 0).coerceIn(0, StyleMath.vignetteAlpha.size - 1)
        bars = o.optInt("bars", 0).coerceIn(0, StyleMath.barsFrac.size - 1)
        duck = o.optBoolean("duck", false)
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
