# Sonivell

Player de música moderno, leve e eficiente para Android — Kotlin + Jetpack Compose.

## O que já funciona

- **Biblioteca** lida direto do MediaStore (sem banco próprio): músicas, álbuns, artistas e pastas, com busca e ordenação.
- **Tocando agora** fiel ao mockup: capa grande, barra de progresso com arrasto, aleatório, repetir (todas/uma), curtidas e selo do formato do arquivo (FLAC, MP3…).
- **Fila**: faixa atual, "a seguir" com reordenação por arrasto, limpar e embaralhar.
- **Reprodução em segundo plano** com Media3 (ExoPlayer + MediaSession): notificação, tela de bloqueio, fones Bluetooth e pausa ao desconectar o fone.
- Capas geométricas geradas quando a música não tem arte.

## Duas edições

| | **Sonivell** (lite) | **Sonivell SMART** |
|---|---|---|
| Player, biblioteca, fila | ✓ | ✓ |
| Aleatório SMART | — | ✓ |
| Tamanho do APK | ~2 MB | maior (modelos + ONNX Runtime) |

O **aleatório SMART** usa o motor do [LatentJam](https://github.com/Nikita-sud/latentjam): o app analisa no próprio celular como cada música *soa* (modelo de áudio), além de gênero, artista, ano e idioma, e monta uma fila de faixas parecidas com a que você escolheu. Nada sai do aparelho.

Como usar: toque no botão de aleatório até aparecer o selo **SMART** e escolha uma música. Na primeira abertura a biblioteca é analisada em segundo plano (o progresso aparece em Início); o SMART já funciona com os metadados e melhora conforme a análise do áudio avança.

O SMART também **aprende com a sessão**: pular uma música (ouvir menos de 30 s) refaz o "a seguir" levando o pulo em conta, e o histórico de escuta (só no aparelho) entra em cada fila nova. A análise continua com a tela desligada, com uma notificação de progresso que some ao terminar.

**Para você** (as duas edições): um destaque do dia para redescobrir, músicas que ficaram pela metade, o que você costuma ouvir neste horário e favoritas esquecidas. Na edição SMART, também **mixes pelo som**, cada um começando de um artista que você ouve.

As duas edições podem ficar instaladas ao mesmo tempo.

## Também tem

- Playlists (com importação e exportação M3U), menu de cada música, telas de álbum, artista, gênero e pasta.
- Sessão salva: a fila e a posição voltam como estavam.
- Temporizador, controle de volume, equalizador com predefinições e reforço de graves.
- Tema claro, escuro ou automático; fundo escuro grafite ou preto (#121212); sete cores de destaque, incluindo verde #1DB954.
- Três layouts: Ondas (padrão — progresso em onda, botões de modo com nome e "Por que tocou" do SMART), Contraste (player tingido pela capa) e Clássica (o visual da primeira versão).
- Três estilos de ícone: Traço (padrão), Fino e Arredondado.
- Estatísticas de audição por período, com exportação do histórico em CSV.
- Letras: embutidas no arquivo ou em .lrc (pasta escolhida por você), sincronizadas, com toque para pular e ajuste de sincronia.
- Normalização de volume pelo ReplayGain das tags.
- Playlists automáticas (mais/menos ouvidas, nunca tocadas, recentes, por década) e inteligentes, com regras combinadas, ordem e limite por quantidade ou duração.
- Busca tolerante a erros de digitação e acentos, com anos e décadas ("anos 80", "1997"), resultados agrupados e, na edição SMART, busca pelo estilo.
- Organizar biblioteca: duplicatas (com sugestão de qual manter e exclusão confirmada pelo Android) e músicas sem informação.
- "Não recomendar" faixas ou artistas no SMART e no Para você.

## Ainda por vir

Widgets e Android Auto.

## Stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- AndroidX Media3 (ExoPlayer, Session)
- Coil para capas
- minSdk 26, targetSdk 35

## Compilar

Abra no Android Studio, ou rode:

```
./gradlew assembleLiteDebug
```

A cada push na `main`, o GitHub Actions compila os APKs de release das duas edições — baixe em **Actions → Build APK → Artifacts** (`sonivell-lite-apk` e `sonivell-smart-apk`).

Localmente: `./gradlew assembleLiteRelease` ou `./gradlew assembleSmartRelease` (a edição SMART precisa do NDK e do CMake, que o Android Studio instala sozinho).

### Assinatura

O release é assinado com a **chave de release**, que nunca fica no repositório. Ela entra pelas variáveis `SONIVELL_KEYSTORE` (caminho do `.jks`), `SONIVELL_STORE_PASSWORD`, `SONIVELL_KEY_ALIAS` e `SONIVELL_KEY_PASSWORD`. No GitHub Actions, cadastre os secrets `KEYSTORE_BASE64` (o `.jks` em base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` no environment **`release`** (*Settings → Environments → release → Environment secrets*), não nos secrets do repositório: só o job de build usa esse environment, então nenhum outro workflow recebe a chave. O workflow os repassa ao Gradle com os nomes acima.

Não há chave de teste no repositório. Sem a chave de release, o build de release para com uma explicação; para testar no aparelho, use `./gradlew assembleLiteDebug`. No CI, o build confere a chave antes de compilar e, no fim, registra a impressão digital (SHA-256) do certificado de cada APK, para conferir que saíram com a sua chave.

APKs antigos assinados com a chave de teste (que era pública) não atualizam para os novos: o Android exige o mesmo certificado. Desinstale a versão antiga e instale a nova.

### Release

A versão é uma só para tudo: o `versionName` em [`version.properties`](version.properties) vale para o APK lite, o APK SMART, o `.deb` e o AppImage. O `versionCode` do Android sai dele (`X*10000 + Y*100 + Z`, então 1.2.0 = 10200), e todo build da mesma versão tem o mesmo código.

Para lançar: aumente o `versionName`, faça o merge na `main` e rode **Actions → Release → Run workflow**. O workflow compila os APKs assinados, o `.deb` e o AppImage, confere que todos saíram com a mesma versão e publica o release `v<versão>` com os quatro arquivos e um `SHA256SUMS.txt`. Uma versão que já foi lançada não é publicada de novo.

## Créditos

- Motor SMART e modelos: [LatentJam](https://github.com/Nikita-sud/latentjam), Apache License 2.0 — veja `NOTICE`. Os próprios autores avisam que parte dos pesos dos modelos ainda não passou pela revisão de direitos dos dados de treino; por isso a edição SMART é, por enquanto, só para uso pessoal.
- Fonte [Manrope](https://github.com/googlefonts/manrope), licença SIL Open Font License 1.1.
