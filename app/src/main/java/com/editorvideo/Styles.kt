package com.editorvideo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

// Estilos de texto e logo/marca d'água (v3.3.12).

internal object TextStyleKit {
    val families = listOf("sans-serif", "serif", "monospace", "sans-serif-condensed", "cursive")
    val fontNames = listOf("Padrão", "Serifa", "Monoespaçada", "Condensada", "Cursiva")
    val bgNames = listOf("Translúcido", "Sem fundo", "Sólido")

    /** Cor da caixa atrás do texto; null = sem caixa. O modo 0 é o visual de sempre. */
    fun backgroundColor(bg: Int, textColor: Int): Int? = when (bg) {
        1 -> null
        2 -> if (textColor == Color.BLACK) Color.WHITE else Color.BLACK
        else -> if (textColor == Color.BLACK) 0x99FFFFFF.toInt() else 0x66000000
    }

    fun typeface(font: Int, italic: Boolean): Typeface {
        val style = if (italic) Typeface.BOLD_ITALIC else Typeface.BOLD
        return Typeface.create(families[font.coerceIn(0, families.size - 1)], style)
    }
}

/** Camada do tamanho do quadro com o logo já desenhado no canto escolhido. */
internal class StaticLogo(val bmp: Bitmap) : BitmapOverlay() {
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bmp

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        StaticOverlaySettings.Builder().build()
}

internal object LogoLayer {
    /**
     * Desenha o logo numa imagem transparente do tamanho do vídeo final. Com a camada do tamanho do
     * quadro, o resultado é o mesmo se a Media3 esticar a camada ao quadro ou a mostrar em tamanho real.
     */
    fun compose(logo: Bitmap, ow: Int, oh: Int, pos: Int, size: Int, alpha: Int): Bitmap {
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val r = LogoMath.rect(ow, oh, logo.width, logo.height, pos, size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.alpha = alpha.coerceIn(0, 255)
        Canvas(out).drawBitmap(logo, null, RectF(r[0], r[1], r[2], r[3]), paint)
        return out
    }
}

/** Lê o logo reduzido (lado maior até [maxSide] px). Devolve null se não der para abrir. */
internal fun MainActivity.decodeLogo(uri: Uri, maxSide: Int): Bitmap? {
    return try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > maxSide * 2) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
    } catch (e: Exception) {
        null
    }
}

/** Camada do tamanho do quadro, de uma cor só (fades e transições). Com o tamanho do quadro, funciona esticada ou não. */
internal object SolidLayer {
    fun create(ow: Int, oh: Int, color: Int): Bitmap {
        val b = Bitmap.createBitmap(ow.coerceAtLeast(2), oh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return b
    }
}

/** Vinheta (bordas escuras) e barras pretas de cinema numa imagem transparente do tamanho do quadro (v3.3.13). */
internal object StyleLayer {
    fun compose(ow: Int, oh: Int, vignette: Int, bars: Int): Bitmap {
        val out = Bitmap.createBitmap(ow.coerceAtLeast(2), oh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val a = StyleMath.vignetteAlpha[vignette.coerceIn(0, StyleMath.vignetteAlpha.size - 1)]
        if (a > 0) {
            val cx = out.width / 2f
            val cy = out.height / 2f
            val radius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            val paint = Paint()
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(0x00000000, 0x00000000, Color.argb(a, 0, 0, 0)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        }
        val bh = StyleMath.barHeight(out.height, bars)
        if (bh > 0) {
            val paint = Paint()
            paint.color = Color.BLACK
            canvas.drawRect(0f, 0f, out.width.toFloat(), bh.toFloat(), paint)
            canvas.drawRect(0f, (out.height - bh).toFloat(), out.width.toFloat(), out.height.toFloat(), paint)
        }
        return out
    }
}
