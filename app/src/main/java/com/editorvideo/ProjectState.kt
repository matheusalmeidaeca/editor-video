package com.editorvideo

import android.net.Uri

/**
 * Tudo o que pertence ao projeto aberto (v3.3.3). Antes eram campos soltos do
 * MainActivity. A tela ainda os enxerga pelos mesmos nomes (delegação), então
 * as extensões não mudaram; o passo seguinte é migrá-las para `state.` aos poucos
 * e, na v3.4, trocar `clips` por trilhas.
 */
internal class ProjectState {
    val clips = mutableListOf<Clip>()
    val texts = mutableListOf<TextItem>()
    var selected = 0
    var musicUri: Uri? = null
    var musicName = ""
    var musicDurMs = 0L
    var musicVol = 100
    var voiceUri: Uri? = null
    var voiceDurMs = 0L
    var voiceVol = 100
    var muteOriginal = false
    var aspectIdx = 0
    var qualityIdx = 0
    var fitIdx = 0
    var fadeInIdx = 0
    var fadeOutIdx = 0
    var transType = 0
    var transIdx = 1
    var projectName = "Meu projeto"
    var logoUri: Uri? = null      // logo/marca d'água (v3.3.12)
    var logoPos = 3               // 0 sup. esq., 1 sup. dir., 2 inf. esq., 3 inf. dir., 4 centro
    var logoSize = 1              // índice em LogoMath.sizeFrac
    var logoAlpha = 0             // índice em LogoMath.alphaValues
    var musicFadeIn = 0           // índice em MainActivity.musicFadeMs
    var musicFadeOut = 0
    var musicLoop = false         // repete a música até o fim do vídeo
    var vignette = 0              // índice em StyleMath.vignetteAlpha (v3.3.13)
    var bars = 0                  // índice em StyleMath.barsFrac
    var duck = false              // abaixa a música enquanto a narração toca

    /** Duração de cada clipe já com a velocidade aplicada. */
    fun effs(): List<Long> = clips.map { it.effMs }

    /** Duração total da linha do tempo, em ms. */
    fun totalMs(): Long = clips.sumOf { it.effMs }

    /** Instantes dos cortes entre clipes (ponto de partida do plano de renderização único). */
    fun bounds(): List<Long> = TimelineMath.boundaries(effs())

    /** Volta aos valores de um projeto novo (não mexe em arquivos). */
    fun reset() {
        clips.clear()
        texts.clear()
        selected = 0
        musicUri = null
        musicName = ""
        musicDurMs = 0L
        musicVol = 100
        voiceUri = null
        voiceDurMs = 0L
        voiceVol = 100
        muteOriginal = false
        aspectIdx = 0
        qualityIdx = 0
        fitIdx = 0
        fadeInIdx = 0
        fadeOutIdx = 0
        transType = 0
        transIdx = 1
        projectName = "Meu projeto"
        logoUri = null
        logoPos = 3
        logoSize = 1
        logoAlpha = 0
        musicFadeIn = 0
        musicFadeOut = 0
        musicLoop = false
        vignette = 0
        bars = 0
        duck = false
    }

    fun snapshot() = Snapshot(
        clips.map { it.copy() }, texts.toList(), selected,
        musicUri, musicName, musicDurMs, musicVol,
        voiceUri, voiceDurMs, voiceVol,
        muteOriginal, aspectIdx, qualityIdx, fitIdx, fadeInIdx, fadeOutIdx, transType, transIdx,
        logoUri, logoPos, logoSize, logoAlpha, musicFadeIn, musicFadeOut, musicLoop,
        vignette, bars, duck
    )

    fun restore(s: Snapshot) {
        clips.clear()
        clips.addAll(s.clips.map { it.copy() })
        texts.clear()
        texts.addAll(s.texts)
        selected = s.selected.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
        musicUri = s.musicUri
        musicName = s.musicName
        musicDurMs = s.musicDurMs
        musicVol = s.musicVol
        voiceUri = s.voiceUri
        voiceDurMs = s.voiceDurMs
        voiceVol = s.voiceVol
        muteOriginal = s.muteOriginal
        aspectIdx = s.aspectIdx
        qualityIdx = s.qualityIdx
        fitIdx = s.fitIdx
        fadeInIdx = s.fadeInIdx
        fadeOutIdx = s.fadeOutIdx
        transType = s.transType
        transIdx = s.transIdx
        logoUri = s.logoUri
        logoPos = s.logoPos
        logoSize = s.logoSize
        logoAlpha = s.logoAlpha
        musicFadeIn = s.musicFadeIn
        musicFadeOut = s.musicFadeOut
        musicLoop = s.musicLoop
        vignette = s.vignette
        bars = s.bars
        duck = s.duck
    }
}
