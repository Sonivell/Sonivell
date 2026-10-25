# Sonivell

Player de música moderno, leve e eficiente para Android — Kotlin + Jetpack Compose.

## O que já funciona

- **Biblioteca** lida direto do MediaStore (sem banco próprio): músicas, álbuns, artistas e pastas, com busca e ordenação.
- **Tocando agora** fiel ao mockup: capa grande, barra de progresso com arrasto, aleatório, repetir (todas/uma), curtidas e selo do formato do arquivo (FLAC, MP3…).
- **Fila**: faixa atual, "a seguir" com reordenação por arrasto, limpar e embaralhar.
- **Reprodução em segundo plano** com Media3 (ExoPlayer + MediaSession): notificação, tela de bloqueio, fones Bluetooth e pausa ao desconectar o fone.
- Capas geométricas geradas quando a música não tem arte.

## Ainda por vir

Letras, playlists salvas, menu "mais opções", equalizador e escolha de dispositivo de saída (os botões já estão na interface e avisam "em breve").

## Stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- AndroidX Media3 (ExoPlayer, Session)
- Coil para capas
- minSdk 26, targetSdk 35

## Compilar

Abra no Android Studio, ou rode:

```
./gradlew assembleDebug
```

A cada push na `main`, o GitHub Actions compila os APKs de debug e de release — baixe em **Actions → Build APK → Artifacts**.

### Assinatura

O release é assinado com uma **chave de teste** (`keystore/sonivell-teste.jks`, senha `sonivell123`), versionada no repositório só para facilitar os testes. Antes de publicar na Play Store, gere uma chave própria, guarde-a fora do repositório e passe-a pelas variáveis `SONIVELL_KEYSTORE`, `SONIVELL_STORE_PASSWORD`, `SONIVELL_KEY_ALIAS` e `SONIVELL_KEY_PASSWORD`.

## Créditos

Fonte [Manrope](https://github.com/googlefonts/manrope), licença SIL Open Font License 1.1.
