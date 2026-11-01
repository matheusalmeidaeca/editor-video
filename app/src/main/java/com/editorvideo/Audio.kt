package com.editorvideo

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import java.nio.ByteBuffer

/** Muda o volume (0.0 a 2.0) de áudio PCM 16 bits. */
class GainProcessor(private val gain: Float) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        while (inputBuffer.remaining() >= 2) {
            val s = inputBuffer.getShort().toInt()
            out.putShort((s * gain).toInt().coerceIn(-32768, 32767).toShort())
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}

/** Fade de entrada e saída do áudio final (a mistura inteira). */
class FadeProcessor(
    private val inMs: Long,
    private val outMs: Long,
    private val totalMs: Long
) : BaseAudioProcessor() {

    private var frames = 0L
    private var rate = 44100
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        rate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        frames = 0L
        return inputAudioFormat
    }

    private fun gainAt(ms: Long): Float {
        var g = 1f
        if (inMs > 0L && ms < inMs) g = ms.toFloat() / inMs
        if (outMs > 0L && ms > totalMs - outMs) {
            g = minOf(g, ((totalMs - ms).toFloat() / outMs).coerceIn(0f, 1f))
        }
        return g.coerceIn(0f, 1f)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        val bytesPerFrame = 2 * channels
        while (inputBuffer.remaining() >= bytesPerFrame) {
            val g = gainAt(frames * 1000L / rate)
            for (ch in 0 until channels) {
                val s = inputBuffer.getShort().toInt()
                out.putShort((s * g).toInt().coerceIn(-32768, 32767).toShort())
            }
            frames++
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}

/**
 * Cadeia de áudio de um clipe, na ordem: velocidade, volume, fades do clipe.
 * Usada pela exportação e pela extração de áudio, para as duas soarem igual.
 */
internal fun clipAudioProcessors(c: Clip): List<AudioProcessor> {
    val a = ArrayList<AudioProcessor>()
    if (c.speed != 1f) {
        val sonic = SonicAudioProcessor()
        sonic.setSpeed(c.speed)
        a.add(sonic)
    }
    if (c.vol != 100) a.add(GainProcessor(c.vol / 100f))
    val (fin, fout) = TimelineMath.clipFades(c.fadeIn, c.fadeOut, c.effMs)
    if (fin > 0L || fout > 0L) a.add(FadeProcessor(fin, fout, c.effMs))
    return a
}
