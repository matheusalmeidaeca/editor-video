package com.editorvideo

// Contas puras da v3.3.12 (sem Android, para testar na JVM): animação de texto, legendas .srt e posição do logo.

/** Animação de entrada do texto e do adesivo. A prévia não anima; só a exportação. */
internal object TextAnim {
    const val DUR_MS = 350L
    val names = listOf("Nenhuma", "Aparecer (fade)", "Pop", "Subir")

    /** Devolve [opacidade, escala, deslocamento vertical]. Fora do tempo do item a opacidade é 0. */
    fun frame(anim: Int, ms: Long, start: Long, end: Long): FloatArray {
        if (ms < start || ms > end) return floatArrayOf(0f, 1f, 0f)
        val p = ((ms - start).toFloat() / DUR_MS).coerceIn(0f, 1f)
        return when (anim) {
            1 -> {
                val out = ((end - ms).toFloat() / DUR_MS).coerceIn(0f, 1f)
                floatArrayOf(minOf(p, out), 1f, 0f)
            }
            2 -> floatArrayOf(p, 0.6f + 0.4f * p, 0f)
            3 -> floatArrayOf(p, 1f, -0.12f * (1f - p))
            else -> floatArrayOf(1f, 1f, 0f)
        }
    }
}

internal class SrtCue(val startMs: Long, val endMs: Long, val text: String)

/** Lê legendas no formato SubRip (.srt). Ignora blocos quebrados em vez de falhar. */
internal object SrtParser {
    private val timeRe = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val blockSplit = Regex("\n\\s*\n")
    private val tagRe = Regex("<[^>]*>")
    private val braceRe = Regex("\\{[^}]*\\}")

    fun parseTime(s: String): Long? {
        val m = timeRe.find(s) ?: return null
        val g = m.groupValues
        val frac = g[4].padEnd(3, '0').toLong()
        return g[1].toLong() * 3_600_000L + g[2].toLong() * 60_000L + g[3].toLong() * 1_000L + frac
    }

    fun parse(raw: String): List<SrtCue> {
        val text = raw.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n')
        val out = ArrayList<SrtCue>()
        for (block in text.split(blockSplit)) {
            val lines = block.trim().split('\n')
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val parts = lines[ti].split("-->")
            if (parts.size < 2) continue
            val a = parseTime(parts[0]) ?: continue
            val b = parseTime(parts[1]) ?: continue
            val body = lines.drop(ti + 1).joinToString("\n") { it.trim() }
                .replace(tagRe, "")
                .replace(braceRe, "")
                .trim()
            if (body.isEmpty() || b <= a) continue
            out.add(SrtCue(a, b, body))
        }
        return out
    }
}

/** Onde e quão grande o logo fica no quadro final. A prévia e a exportação usam as mesmas frações. */
internal object LogoMath {
    val sizeFrac = listOf(0.20f, 0.32f, 0.50f)   // largura do logo em relação ao lado menor do vídeo
    val alphaValues = listOf(255, 191, 128, 77)   // 100%, 75%, 50%, 30%
    const val MARGIN = 0.04f

    /** Retângulo [esquerda, topo, direita, base] em pixels. pos: 0 sup. esq., 1 sup. dir., 2 inf. esq., 3 inf. dir., 4 centro. */
    fun rect(ow: Int, oh: Int, bw: Int, bh: Int, pos: Int, size: Int): FloatArray {
        val short = minOf(ow, oh).toFloat()
        var w = short * sizeFrac[size.coerceIn(0, sizeFrac.size - 1)]
        var h = w * bh / bw.coerceAtLeast(1)
        val maxH = oh * 0.5f
        if (h > maxH) {
            w *= maxH / h
            h = maxH
        }
        val m = short * MARGIN
        val left = when (pos) {
            0, 2 -> m
            1, 3 -> ow - m - w
            else -> (ow - w) / 2f
        }
        val top = when (pos) {
            0, 1 -> m
            2, 3 -> oh - m - h
            else -> (oh - h) / 2f
        }
        return floatArrayOf(left, top, left + w, top + h)
    }
}

/** Divide [total] em [n] partes quase iguais (a última leva a sobra). Devolve vazio se alguma parte ficar menor que [minMs]. */
internal object SplitMath {
    fun equalParts(total: Long, n: Int, minMs: Long = 300L): List<Pair<Long, Long>> {
        if (n < 2 || total <= 0L) return emptyList()
        val part = total / n
        if (part < minMs) return emptyList()
        val out = ArrayList<Pair<Long, Long>>()
        for (k in 0 until n) {
            val a = k * part
            val b = if (k == n - 1) total else a + part
            out.add(Pair(a, b))
        }
        return out
    }
}

// ---------------------------------------------------------------------------------------------
// v3.3.13: contas puras novas (sem Android, testáveis na JVM).

/** Escreve legendas no formato SubRip (.srt). */
internal object SrtWriter {
    fun time(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        val h = v / 3_600_000L
        val m = (v % 3_600_000L) / 60_000L
        val s = (v % 60_000L) / 1_000L
        val f = v % 1_000L
        return "%02d:%02d:%02d,%03d".format(h, m, s, f)
    }

    fun write(cues: List<SrtCue>): String {
        val sb = StringBuilder()
        for ((i, q) in cues.withIndex()) {
            sb.append(i + 1).append('\n')
            sb.append(time(q.startMs)).append(" --> ").append(time(q.endMs)).append('\n')
            sb.append(q.text.trim()).append("\n\n")
        }
        return sb.toString()
    }
}

/** Desloca o tempo de um texto sem deixar o início ficar negativo; a duração se mantém. */
internal object TextShift {
    fun shift(start: Long, end: Long, delta: Long): Pair<Long, Long> {
        val len = (end - start).coerceAtLeast(1L)
        val ns = (start + delta).coerceAtLeast(0L)
        return Pair(ns, ns + len)
    }
}

/** Rampas de velocidade: o clipe é dividido em partes iguais e cada parte ganha a sua velocidade. */
internal object SpeedRamp {
    val names = listOf("Câmera lenta no meio", "Acelerar no fim", "Desacelerar no fim", "Pulso (rápido e lento)")
    val patterns = listOf(
        listOf(1f, 0.5f, 1f),
        listOf(0.75f, 1f, 1.5f, 2f),
        listOf(2f, 1.5f, 1f, 0.75f),
        listOf(1f, 0.5f, 1f, 0.5f, 1f)
    )

    /** Partes [início, fim] relativas ao trecho do clipe, uma por velocidade. Vazio se alguma parte ficar curta demais. */
    fun parts(total: Long, speeds: List<Float>): List<Pair<Long, Long>> =
        SplitMath.equalParts(total, speeds.size, 400L)
}

/** Vinheta e barras de cinema (camada única do tamanho do quadro). */
internal object StyleMath {
    val vignetteNames = listOf("Sem", "Suave", "Média", "Forte")
    val vignetteAlpha = listOf(0, 110, 170, 220)
    val barsNames = listOf("Sem", "Finas", "Grossas")
    val barsFrac = listOf(0f, 0.08f, 0.14f)

    /** Altura de cada barra, em pixels, para um quadro de altura [h]. */
    fun barHeight(h: Int, level: Int): Int =
        (h * barsFrac[level.coerceIn(0, barsFrac.size - 1)]).toInt()

    fun active(vignette: Int, bars: Int): Boolean = vignette > 0 || bars > 0
}

/** Desfoque por clipe (desvio padrão em pixels). */
internal object BlurMath {
    val names = listOf("Sem", "Suave", "Médio", "Forte")
    val sigma = listOf(0f, 3f, 8f, 16f)
}
