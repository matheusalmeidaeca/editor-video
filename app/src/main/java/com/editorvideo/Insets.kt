package com.editorvideo

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

// v3.3.8: tela de borda a borda em todas as versões do Android (no Android 15+ é obrigatória
// com targetSdk 35 ou mais). O conteúdo ganha margem das barras do sistema, do recorte da
// câmera e do teclado; o fundo da janela é o azul-marinho do app, então a margem não vira
// uma faixa de outra cor.

/** Chamar uma vez no onCreate, antes de montar a tela. */
@Suppress("DEPRECATION")
internal fun MainActivity.setupEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#050B18")))
    if (Build.VERSION.SDK_INT < 35) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= 29) {
        window.isNavigationBarContrastEnforced = false
    }
}

/** Dá ao quadro raiz de cada tela o padding das barras do sistema e do teclado. */
internal fun MainActivity.applySystemInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val type = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
        val b = insets.getInsets(type)
        v.setPadding(b.left, b.top, b.right, b.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
