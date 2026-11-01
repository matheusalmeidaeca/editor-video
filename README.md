# Editor de Vídeo 3.3.0 (Android, Kotlin + Media3)

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

**Novidades da 3.3.0 (sem mudança para quem usa):**
- Primeira etapa da subida do Media3, de 1.4.1 para 1.5.1, com `compileSdk` 35. O `targetSdk` continua em 34.
  Tudo deve funcionar como antes; a mudança prepara fotos como clipes, quadro congelado e fps na exportação.

**Novidades da 3.2.11:**
- **Normalizar volume do clipe:** no Mixer, o botão ajusta o volume do clipe selecionado para o pico do trecho cortado chegar
  a cerca de 90%. Sobe voz baixa (até 200%) e desce áudio alto (até 20%). Usa a forma de onda; se ela ainda estiver sendo
  analisada, o app pede para tentar de novo. Dá para desfazer. Na prévia o volume não passa de 100%; o ganho acima disso vale na exportação.

**Novidades da 3.2.10:**
- Só correção de um teste unitário (`snap`): o valor do teste estava errado, o código estava certo.

**Novidades da 3.2.9:**
- **Ações do clipe só com seleção:** Duplicar, Excluir e Mover aparecem na barra de ações só depois de tocar num clipe
  da linha do tempo. Tocar de novo no mesmo clipe, tocar fora dos clipes ou arrastar a linha do tempo para outro clipe
  esconde essas ações. Dividir e Cortar à esquerda/direita continuam sempre na barra, porque dependem da agulha.

**Novidades da 3.2.8:**
- **Galeria no menu de adicionar:** o menu radial agora tem "Galeria" (seletor de fotos e vídeos do sistema, até 30 vídeos de uma vez)
  e "Arquivos" (o seletor de arquivos de antes). Os vídeos da galeria são copiados para dentro do app, para o projeto abrir depois;
  isso ocupa espaço e a cópia some quando o projeto é excluído.

**Novidades da 3.2.7:**
- **Forma de onda na linha do tempo:** o som de cada clipe aparece em verde na faixa do vídeo, e a música e a narração
  ganham a própria onda em branco. Acompanha corte, velocidade e zoom da linha do tempo. A análise roda em segundo plano
  (o desenho aparece alguns segundos depois de importar) e vídeo sem som fica sem onda.

**Novidades da 3.2.6:**
- **Remover silêncios:** na ferramenta Cortar, o botão "Remover silêncios" analisa o áudio do clipe selecionado e o divide
  nos trechos com som, tirando as pausas de 500 ms ou mais (com 120 ms de folga de cada lado). O limite de silêncio é
  relativo ao trecho mais alto do clipe, então funciona também com voz baixa. Dá para desfazer. A análise decodifica o
  áudio, então um clipe longo pode levar alguns segundos; o status aparece no rodapé.

**Novidades da 3.2.5 (organização do código, nada muda para quem usa):**
- `ExportComposition.kt`: a montagem da exportação (clipes, texto, fades, transições, música e narração) saiu do `MainActivity`.
  `TextLayout` guarda as posições e tamanhos do texto, que a prévia e a exportação leem do mesmo lugar.
- `ProjectJson.kt`: conversão de clipes e textos para JSON e de volta, com teste novo (`ProjectJsonTest`).

**Novidades da 3.2.4 (só organização do código, nada muda para quem usa):**
- O modelo (`Clip`, `TextItem`, `Snapshot`) e as camadas de exportação (texto, fade, transição) foram para `Model.kt`.
- Os efeitos de vídeo (filtros, zoom, rotação, cor) foram para `VideoEffects.kt`.
- A cadeia de áudio do clipe (velocidade, volume, fades) virou uma função só em `Audio.kt`, usada pela exportação e pela extração.

**Novidades da 3.2.3:**
- **Extrair áudio:** na ferramenta Mixer, o botão "Extrair áudio deste clipe" salva o som do clipe selecionado
  (trecho cortado, velocidade, volume e fades) como .m4a em Música/EditorVideo. Se o vídeo não tiver som, aparece um erro.

**Novidades da 3.2.2:**
- **Ímã da agulha:** ao arrastar a linha do tempo, a agulha gruda nos cortes entre clipes e nas pontas (a até 8 dp),
  com um toque de vibração. Dê zoom na linha do tempo para ajustar bem perto de um corte.
- **Fade de áudio por clipe:** na ferramenta Áudio, fade de entrada e de saída de 0 a 3 s no clipe selecionado.
  Aplicado na exportação; a prévia ainda não mostra o fade. O projeto salva os valores.

**Novidades da 3.2.1:**
- **Predefinições de exportação** no diálogo Salvar e compartilhar: Reels/TikTok/Shorts, YouTube, WhatsApp e Menor arquivo
  (ajustam formato, resolução e qualidade de uma vez; a proporção continua sendo a do projeto).
- **Receber vídeos pelo Compartilhar** de outros apps (galeria, WhatsApp): vira um projeto novo. O app guarda uma cópia
  do vídeo em `imports/` (ocupa espaço) e apaga a cópia quando o projeto é excluído.
- **Erros de exportação em português**, com o código no fim para diagnóstico, e aviso de **pouco espaço livre** antes de exportar.

**Novidades da 3.2.0 (base de confiança):**
- **Exportação em serviço de primeiro plano:** notificação com progresso e botão Cancelar; o app não é
  encerrado com a tela apagada ou ao trocar de app. Pede a permissão de notificações na primeira exportação
  (se você negar, a exportação continua, só sem a notificação).
- **Projetos como arquivos** (`projects/<id>/project.json` + miniatura): vários projetos, duplicar, renomear, excluir.
  O projeto único da 3.1.x é trazido automaticamente na primeira abertura.
- **Tela inicial** com Novo projeto, Importar vídeo e grade de projetos recentes (toque longo: duplicar, renomear, excluir).
- **Menu hambúrguer** (☰) na tela inicial e no editor; o botão voltar do editor leva à tela inicial.
- Testes unitários novos do armazenamento de projetos (`ProjectStoreTest`).

**Novidades da 3.1.1 (confiança):** camadas de fade e transição com bitmap mínimo (não estouram
memória em vídeos grandes), aviso no diálogo de exportação quando o aparelho não codifica o
formato/resolução escolhidos, matemática da linha do tempo isolada em `TimelineMath.kt` com testes
unitários (`gradle testDebugUnitTest`) e suporte a assinatura fixa do APK.

**Assinatura fixa (uma vez, dá para fazer só com o celular no Termux):**
sem ela, cada build do GitHub tem uma chave diferente e o Android recusa instalar um por cima do outro.
1. No Termux: `pkg install openjdk-17`.
2. `keytool -genkeypair -v -keystore editorvideo.jks -alias editorvideo -keyalg RSA -keysize 2048 -validity 10000`
   (anote a senha que você escolher).
3. `base64 -w0 editorvideo.jks > keystore.b64` e depois `cat keystore.b64` (copie o texto inteiro).
4. GitHub → Settings → Secrets and variables → Actions: crie `KEYSTORE_BASE64` (o texto do passo 3),
   `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`editorvideo`) e `KEY_PASSWORD`.
5. Guarde o `editorvideo.jks` em dois lugares (nunca no repositório). Se perder, não há como atualizar o app assinado.
O primeiro build assinado ainda conflita com o instalado antes (chave diferente): desinstale uma vez
(isso apaga os projetos) e, daí em diante, instale por cima.
