package com.editorvideo

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation

// Efeitos de vídeo por clipe: filtros, zoom/Ken Burns, rotação, cor (saíram do MainActivity na v3.2.4).

internal fun filterEffects(i: Int): List<Effect> = when (i) {
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

internal fun sepiaEffect(): Effect = object : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = floatArrayOf(
        0.393f, 0.349f, 0.272f, 0f,
        0.769f, 0.686f, 0.534f, 0f,
        0.189f, 0.168f, 0.131f, 0f,
        0f, 0f, 0f, 1f
    )
}

internal fun zoomEffect(c: Clip, export: Boolean): Effect {
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

internal fun videoEffects(c: Clip, export: Boolean): List<Effect> {
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
