package com.editorvideo

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

// v3.3.9: biblioteca de mídia no estilo do PowerDirector: coluna de categorias à esquerda
// (Vídeo, Música, Texto) e, ao lado, os cartões de onde importar. Os seletores continuam
// sendo os do sistema (galeria, arquivos, microfone); aqui só fica tudo num lugar só.

private class LibTab(val icon: String, val name: String, val items: List<Triple<String, String, () -> Unit>>)

internal fun MainActivity.showMediaLibrary() {
    val scrim = FrameLayout(this).apply {
        setBackgroundColor(Color.parseColor("#F2050B18"))
        isClickable = true
    }

    fun close() {
        rootFrame.removeView(scrim)
    }

    val tabs = listOf(
        LibTab(
            "🎞", "Vídeo", listOf(
                Triple("🖼️", "Galeria") { openGallery() },
                Triple("📁", "Vídeo de arquivo") { pickVideos.launch(arrayOf("video/*")) }
            )
        ),
        LibTab(
            "🎵", "Música", listOf(
                Triple("🎵", "Música de arquivo") { pickMusic.launch(arrayOf("audio/*")) },
                Triple("🎙️", "Gravação de voz") { requestRecord() }
            )
        ),
        LibTab(
            "🔤", "Texto", listOf(
                Triple("🔤", "Novo texto") {
                    currentTool = 5
                    buildToolTabs()
                    showTool(5, true)
                    itemDialog(-1, false)
                },
                Triple("😀", "Adesivo") {
                    currentTool = 6
                    buildToolTabs()
                    showTool(6, true)
                    itemDialog(-1, true)
                }
            )
        )
    )

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    val rail = vbox().apply {
        background = glass(18)
        setPadding(dp(4), dp(6), dp(4), dp(6))
    }
    val content = vbox()
    val header = hbox()
    val titleTv = tv("", 18f)
    val cards = hbox().apply { gravity = Gravity.TOP }

    header.addView(iconBtn("‹") { close() }, lpm(dp(42), dp(42), r = 10))
    header.addView(titleTv, lpm(0, WRAP, weight = 1f))
    content.addView(header)
    content.addView(cards, lpm(MATCH, WRAP, t = 14))

    fun select(index: Int) {
        val tab = tabs[index]
        titleTv.text = tab.name
        cards.removeAllViews()
        tab.items.forEach { (icon, label, action) ->
            val card = vbox().apply {
                gravity = Gravity.CENTER
                background = glass(16)
                setPadding(dp(8), dp(12), dp(8), dp(12))
                setOnClickListener {
                    close()
                    action()
                }
            }
            press(card)
            card.addView(tv(icon, 30f).apply { gravity = Gravity.CENTER })
            card.addView(tv(label, 13f).apply { gravity = Gravity.CENTER })
            cards.addView(card, lpm(dp(140), dp(104), r = 10))
        }
        for (k in 0 until rail.childCount) {
            val r = rail.getChildAt(k)
            val on = k == index
            r.background = if (on) gradient(16) else null
            ((r as LinearLayout).getChildAt(1) as android.widget.TextView)
                .setTextColor(if (on) cNavy else cText)
        }
    }

    tabs.forEachIndexed { i, tab ->
        val item = vbox().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { select(i) }
        }
        press(item)
        item.addView(tv(tab.icon, 20f).apply { gravity = Gravity.CENTER })
        item.addView(tv(tab.name, 11f))
        rail.addView(item, lpm(dp(64), WRAP, b = 4))
    }

    root.addView(rail, lpm(WRAP, WRAP))
    root.addView(content, lpm(0, MATCH, l = 12, weight = 1f))
    scrim.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
    rootFrame.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
    select(0)
}
