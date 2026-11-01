# Editor de Vídeo 3.1.1 (Android, Kotlin + Media3)

Sem IA. Visual "osmótico": fundo líquido animado, painéis de vidro e botões que respiram.

**Ferramentas:** cortar (com "início/fim aqui"), dividir, duplicar, mover, velocidade, 12 filtros,
brilho/contraste/saturação, girar/espelhar, zoom e posição, panorâmica e zoom animado,
textos e adesivos com tempo e posição, volume por clipe, música, narração gravada no app,
fade de abertura e final, proporção/qualidade, desfazer/refazer, projeto salvo automaticamente
e compartilhar depois de exportar.

A prévia mostra filtros, rotação e zoom ao vivo.

Compile pelo GitHub Actions (aba Actions). O APK sai em Artifacts e também em Releases.

**Novidades da 3.1 (inspiradas no KineMaster):** linha do tempo contínua com agulha central
(arraste para navegar, pince para dar zoom, trilhas de texto, música e narração), novo projeto
com nome e proporção (16:9, 9:16, 1:1, 4:3, 3:4, 4:5, 2.35:1), cortar à esquerda/direita da agulha,
transições entre clipes (fade no preto ou no branco), menu radial de adição, diálogo
"Salvar e compartilhar" (H.264/HEVC, resolução e qualidade com tamanho estimado) e layout
próprio para a tela deitada.

**Novidades da 3.1.1 (confiança):** camadas de fade e transição com bitmap mínimo (não estouram
memória em vídeos grandes), aviso no diálogo de exportação quando o aparelho não codifica o
formato/resolução escolhidos, matemática da linha do tempo isolada em `TimelineMath.kt` com testes
unitários (`gradle testDebugUnitTest`) e suporte a assinatura fixa do APK.

**Assinatura fixa (uma vez):** sem ela, cada build do GitHub tem uma chave diferente e o Android
recusa instalar um por cima do outro.
1. No computador: `keytool -genkeypair -v -keystore editorvideo.jks -alias editorvideo -keyalg RSA -keysize 2048 -validity 10000`
2. Converta: `base64 -w0 editorvideo.jks` (no Mac: `base64 -i editorvideo.jks`).
3. GitHub → Settings → Secrets and variables → Actions: crie `KEYSTORE_BASE64` (o texto do passo 2),
   `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`editorvideo`) e `KEY_PASSWORD`.
4. Guarde o `.jks` em dois lugares. Se perder, não há como atualizar o app assinado.
O primeiro build assinado ainda conflita com o instalado antes (chave diferente): desinstale uma vez
(isso apaga o projeto salvo) e, daí em diante, instale por cima.
