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
- Tema claro, escuro ou automático e seis cores de destaque.
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

O release é assinado com uma **chave de teste** (`keystore/sonivell-teste.jks`, senha `sonivell123`), versionada no repositório só para facilitar os testes. Antes de publicar na Play Store, gere uma chave própria, guarde-a fora do repositório e passe-a pelas variáveis `SONIVELL_KEYSTORE`, `SONIVELL_STORE_PASSWORD`, `SONIVELL_KEY_ALIAS` e `SONIVELL_KEY_PASSWORD`.

## Créditos

- Motor SMART e modelos: [LatentJam](https://github.com/Nikita-sud/latentjam), Apache License 2.0 — veja `NOTICE`. Os próprios autores avisam que parte dos pesos dos modelos ainda não passou pela revisão de direitos dos dados de treino; por isso a edição SMART é, por enquanto, só para uso pessoal.
- Fonte [Manrope](https://github.com/googlefonts/manrope), licença SIL Open Font License 1.1.
