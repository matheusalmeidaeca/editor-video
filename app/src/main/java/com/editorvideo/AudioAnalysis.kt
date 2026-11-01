package com.editorvideo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decisão de onde há som e onde há silêncio. Sem Android, testável na JVM. */
internal object SilenceMath {

    /**
     * [peaks]: volume de 0 a 1 em janelas de [bucketMs]. Devolve os trechos a manter, em ms desde o início
     * da análise, como pares (início, fim).
     *
     * - o limite de silêncio é [relThreshold] do pico mais alto do próprio trecho (assim vale para vozes baixas);
     * - silêncios menores que [minSilenceMs] não cortam;
     * - sons menores que [minKeepMs] (estalos) são descartados;
     * - cada trecho ganha [padMs] de folga dos dois lados;
     * - nenhuma peça fica menor que [minPieceMs] (o app não aceita clipes menores que 300 ms).
     */
    fun voicedRanges(
        peaks: FloatArray,
        bucketMs: Long,
        relThreshold: Float,
        minSilenceMs: Long,
        padMs: Long,
        minKeepMs: Long,
        minPieceMs: Long
    ): List<Pair<Long, Long>> {
        if (peaks.isEmpty() || bucketMs <= 0L) return emptyList()
        var max = 0f
        for (p in peaks) if (p > max) max = p
        if (max < 0.005f) return emptyList() // praticamente mudo
        val thr = max * relThreshold
        val totalMs = peaks.size * bucketMs

        // 1) trechos altos, em janelas [início, fim)
        val runs = ArrayList<LongArray>()
        var i = 0
        while (i < peaks.size) {
            if (peaks[i] >= thr) {
                var j = i
                while (j < peaks.size && peaks[j] >= thr) j++
                runs.add(longArrayOf(i.toLong(), j.toLong()))
                i = j
            } else {
                i++
            }
        }

        // 2) junta trechos separados por silêncio curto
        val merged = ArrayList<LongArray>()
        for (r in runs) {
            val last = merged.lastOrNull()
            if (last != null && (r[0] - last[1]) * bucketMs < minSilenceMs) {
                last[1] = r[1]
            } else {
                merged.add(longArrayOf(r[0], r[1]))
            }
        }

        // 3) descarta estalos, aplica a folga e junta o que passou a se sobrepor
        val padded = ArrayList<LongArray>()
        for (r in merged) {
            if ((r[1] - r[0]) * bucketMs < minKeepMs) continue
            val a = (r[0] * bucketMs - padMs).coerceAtLeast(0L)
            val b = (r[1] * bucketMs + padMs).coerceAtMost(totalMs)
            val last = padded.lastOrNull()
            if (last != null && a <= last[1]) {
                last[1] = maxOf(last[1], b)
            } else {
                padded.add(longArrayOf(a, b))
            }
        }

        // 4) peças curtas demais crescem até o mínimo, se couber
        val out = ArrayList<Pair<Long, Long>>()
        var prevEnd = 0L
        for (r in padded) {
            val a = maxOf(r[0], prevEnd)
            var b = r[1]
            if (b - a < minPieceMs) b = minOf(totalMs, a + minPieceMs)
            if (b - a >= minPieceMs) {
                out.add(Pair(a, b))
                prevEnd = b
            }
        }
        return out
    }
}

/** Decodifica o áudio de um trecho do arquivo e mede o volume de pico por janela. */
internal object AudioPeaks {

    /**
     * Devolve o pico (0 a 1) de cada janela de [bucketMs] entre [startMs] e [endMs] do arquivo,
     * ou null se o arquivo não tiver áudio ou o aparelho não conseguir decodificá-lo.
     * Deve rodar fora da thread da tela. Supõe saída PCM de 16 bits, que é o padrão dos decodificadores.
     */
    fun compute(ctx: Context, uri: Uri, startMs: Long, endMs: Long, bucketMs: Long): FloatArray? {
        val spanMs = endMs - startMs
        if (spanMs <= 0L || bucketMs <= 0L) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(ctx, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            val n = ((spanMs + bucketMs - 1) / bucketMs).toInt()
            val peaks = FloatArray(n)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val endUs = endMs * 1000L

            while (!outputDone) {
                if (!inputDone) {
                    val ii = dec.dequeueInputBuffer(10_000L)
                    if (ii >= 0) {
                        val buf = dec.getInputBuffer(ii)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        val t = extractor.sampleTime
                        if (size < 0 || t > endUs) {
                            dec.queueInputBuffer(ii, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(ii, 0, size, t, 0)
                            extractor.advance()
                        }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000L)
                if (oi >= 0) {
                    if (info.size > 0) {
                        val out = dec.getOutputBuffer(oi)
                        if (out != null) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val sb = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val ch = channels.coerceAtLeast(1)
                            val frames = sb.remaining() / ch
                            val baseMs = info.presentationTimeUs / 1000.0
                            for (f in 0 until frames) {
                                val tMs = baseMs + f * 1000.0 / rate
                                val b = ((tMs - startMs) / bucketMs).toInt()
                                var peak = 0
                                for (k in 0 until ch) {
                                    val v = Math.abs(sb.get(f * ch + k).toInt())
                                    if (v > peak) peak = v
                                }
                                if (b in 0 until n) {
                                    val p = peak / 32768f
                                    if (p > peaks[b]) peaks[b] = p
                                }
                            }
                        }
                    }
                    dec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = dec.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            return peaks
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
            }
            try {
                codec?.release()
            } catch (e: Exception) {
            }
            extractor.release()
        }
    }
}

/** Volume de pico de um arquivo inteiro, em janelas de [bucketMs] (v3.2.7). Público porque a `TimelineView` o usa. */
class WaveData(val peaks: FloatArray, val bucketMs: Long) {
    /** Maior pico do arquivo, com piso, para escalar o desenho (vozes baixas também aparecem). */
    val norm: Float = run {
        var m = 0f
        for (p in peaks) if (p > m) m = p
        if (m < 0.02f) 0.02f else m
    }
}

/** Contas do desenho da forma de onda. Sem Android, testável na JVM. */
internal object WaveMath {

    /** Janela de análise: 50 ms até 30 min de áudio; acima disso, 200 ms (menos memória e tempo). */
    fun bucketFor(durationMs: Long): Long = if (durationMs > 30L * 60_000L) 200L else 50L

    /** Maior pico entre [fromMs] e [toMs] (em ms do arquivo). Fora do arquivo devolve 0. */
    fun maxIn(peaks: FloatArray, bucketMs: Long, fromMs: Double, toMs: Double): Float {
        if (peaks.isEmpty() || bucketMs <= 0L) return 0f
        val a = Math.floor(minOf(fromMs, toMs) / bucketMs).toInt()
        val b = Math.floor(maxOf(fromMs, toMs) / bucketMs).toInt()
        if (b < 0 || a > peaks.size - 1) return 0f
        var m = 0f
        var i = maxOf(a, 0)
        val end = minOf(b, peaks.size - 1)
        while (i <= end) {
            if (peaks[i] > m) m = peaks[i]
            i++
        }
        return m
    }

    /** Altura relativa (0 a 1) de uma barra: raiz do pico sobre o maior pico, para realçar sons baixos. */
    fun level(peak: Float, norm: Float): Float {
        if (norm <= 0f) return 0f
        val r = peak / norm
        return Math.sqrt(minOf(1f, maxOf(0f, r)).toDouble()).toFloat()
    }
}
