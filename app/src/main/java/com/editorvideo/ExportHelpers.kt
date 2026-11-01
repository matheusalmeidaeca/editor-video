package com.editorvideo

/** Predefinição de exportação: só mexe em formato, resolução e qualidade (a proporção é do projeto). */
class ExportPreset(
    val name: String,
    val codecIdx: Int,    // 0 = H.264, 1 = HEVC
    val qualityIdx: Int,  // índice em qualityNames: 0 original, 1 = 1080p, 2 = 720p, 3 = 480p
    val bitrateIdx: Int,  // 0 baixa, 1 média, 2 alta
    val hint: String
)

object ExportPresets {
    /** Pontos de partida. Valores escolhidos por critério próprio, ajuste conforme o resultado. */
    val all = listOf(
        ExportPreset("Reels / TikTok / Shorts", 0, 1, 1, "Use a proporção 9:16 no projeto. 1080p, formato compatível."),
        ExportPreset("YouTube", 0, 1, 2, "1080p com qualidade alta, formato compatível."),
        ExportPreset("WhatsApp", 0, 2, 0, "720p e arquivo leve, bom para enviar por mensagem."),
        ExportPreset("Menor arquivo", 1, 2, 1, "HEVC em 720p. Nem todo aparelho ou app abre HEVC.")
    )
}

/** Traduz o código de erro da exportação e estima o espaço necessário. Sem Android, testável na JVM. */
object ExportHelpers {

    /**
     * As faixas de código seguem a numeração da Media3 (2xxx arquivo, 3xxx decodificador, 4xxx codificador,
     * 5xxx imagem, 6xxx áudio, 7xxx gravação do arquivo). [verificar] na subida do Media3 (v3.3).
     * O código sempre aparece na mensagem para facilitar o diagnóstico.
     */
    fun friendlyError(code: Int): String {
        val texto = when (code) {
            in 2000..2999 -> "Não consegui ler um dos arquivos do projeto. Confira se ele ainda existe e tente de novo."
            in 3000..3999 -> "O aparelho não conseguiu abrir um dos vídeos. Tente converter esse clipe ou usar outro."
            in 4000..4999 -> "O aparelho não consegue gerar esse formato ou tamanho. Tente H.264 ou uma resolução menor."
            in 5000..5999 -> "Falha ao aplicar os efeitos de imagem. Tente tirar um filtro ou texto e exportar de novo."
            in 6000..6999 -> "Falha ao processar o áudio. Tente tirar a música ou a narração e exportar de novo."
            in 7000..7999 -> "Não consegui gravar o arquivo final. Veja se há espaço livre no aparelho."
            else -> "A exportação falhou por um motivo que o app ainda não reconhece."
        }
        return "$texto (código $code)"
    }

    /** Bytes aproximados do vídeo de saída: taxa de bits mais ~128 kbps de áudio. */
    fun estimatedBytes(videoBitrate: Int, totalMs: Long): Long =
        ((videoBitrate + 128_000L) / 8L) * (totalMs / 1000L).coerceAtLeast(1L)

    /** Exige a estimativa com folga de 50% mais 50 MB livres no cache. */
    fun hasRoom(freeBytes: Long, estimated: Long): Boolean =
        freeBytes >= estimated + estimated / 2 + 50_000_000L
}
