#!/usr/bin/env bash
# CI: compila os APKs lite e SMART (release com a chave; debug em PR de fork, ver apk-chave.sh).
set -o pipefail
if [ "$SONIVELL_BUILD" = release ]; then tarefas="assembleLiteRelease assembleSmartRelease"; else tarefas="assembleLiteDebug assembleSmartDebug"; fi
# shellcheck disable=SC2086 # $tarefas são duas palavras de propósito
./gradlew $tarefas 2>&1 | tee build.log || {
  # Em caso de erro, destaca as mensagens como anotações (visíveis no resumo da execução).
  grep -E "^e: |What went wrong|Execution failed|error:|Error:|Lint found" -A3 build.log | head -n 60 | while IFS= read -r line; do
    echo "::error::${line}"
  done
  exit 1
}
