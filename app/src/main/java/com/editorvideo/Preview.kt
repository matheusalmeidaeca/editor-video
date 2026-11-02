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

// Prévia de texto e adesivos sobre o vídeo (saiu do MainActivity na v3.3.2).

internal fun MainActivity.refreshOverlay(pos: Long) {
    val lw = overlayLayer.width
    val lh = overlayLayer.height
    val active = ArrayList<Int>()
    if (pos >= 0L) texts.forEachIndexed { i, t -> if (pos >= t.startMs && pos <= t.endMs) active.add(i) }
    val key = active.joinToString(",") { System.identityHashCode(texts[it]).toString() } +
        "|$lw|$lh|L${logoUri}${logoPos}${logoSize}${logoAlpha}|V$vignette$bars"
    if (key == overlayKey) return
    overlayKey = key
    overlayLayer.removeAllViews()
    if (lw <= 0 || lh <= 0) return
    val shortSide = min(lw, lh).toFloat()
    addStylePreview(lw, lh)
    addLogoPreview(lw, lh)
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
            view.setTypeface(TextStyleKit.typeface(t.font, t.italic))
            val bgc = TextStyleKit.backgroundColor(t.bg, t.color)
            if (bgc != null) view.setBackgroundColor(bgc)
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

/** Mostra o logo no canto escolhido, com as mesmas frações da exportação (v3.3.12). */
internal fun MainActivity.addLogoPreview(lw: Int, lh: Int) {
    val u = logoUri ?: return
    val k = u.toString()
    if (logoPreview == null || logoPreviewKey != k) {
        logoPreview = decodeLogo(u, 512)
        logoPreviewKey = k
    }
    val bmp = logoPreview ?: return
    val r = LogoMath.rect(lw, lh, bmp.width, bmp.height, logoPos, logoSize)
    val iv = ImageView(this)
    iv.setImageBitmap(bmp)
    iv.scaleType = ImageView.ScaleType.FIT_XY
    iv.alpha = LogoMath.alphaValues[logoAlpha] / 255f
    val lp = FrameLayout.LayoutParams((r[2] - r[0]).toInt().coerceAtLeast(1), (r[3] - r[1]).toInt().coerceAtLeast(1))
    lp.leftMargin = r[0].toInt()
    lp.topMargin = r[1].toInt()
    overlayLayer.addView(iv, lp)
}
