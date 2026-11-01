#!/usr/bin/env bash
# CI: decodifica a chave de release (secret KEYSTORE_BASE64), confere senha e alias e a deixa
# para o Gradle (SONIVELL_KEYSTORE e SONIVELL_BUILD no $GITHUB_ENV). Usado pelo build.yml e pelo
# release.yml, sempre num job com "environment: release" (onde ficam os secrets).
# Entrada: KEYSTORE_BASE64, SONIVELL_STORE_PASSWORD, SONIVELL_KEY_ALIAS, SONIVELL_KEY_PASSWORD
# e PR_DE_FORK (true só em PR de fork: aí, sem secrets, compila só debug).
set -euo pipefail
if [ -z "${KEYSTORE_BASE64:-}" ]; then
  if [ "$PR_DE_FORK" = true ]; then
    echo "::warning::PR de fork: sem acesso aos secrets, só os APKs de debug foram compilados."
    echo "SONIVELL_BUILD=debug" >> "$GITHUB_ENV"
    exit 0
  fi
  echo "::error::Falta o secret KEYSTORE_BASE64 (o .jks em base64). Veja a seção Assinatura do README."
  exit 1
fi
# Secret do repositório -> variável que o Gradle lê.
for par in KEYSTORE_PASSWORD:SONIVELL_STORE_PASSWORD KEY_ALIAS:SONIVELL_KEY_ALIAS KEY_PASSWORD:SONIVELL_KEY_PASSWORD; do
  v=${par#*:}
  [ -n "${!v:-}" ] || { echo "::error::Falta o secret ${par%%:*}."; exit 1; }
done
jks="$RUNNER_TEMP/sonivell-release.jks"
# Tolera o que costuma vir junto ao colar: quebras de linha, tabs, aspas, as linhas
# "-----BEGIN/END" do certutil -encode (Windows) e o alfabeto url-safe (- e _).
b64=$(printf '%s' "$KEYSTORE_BASE64" | grep -v -- '-----' | tr -d '[:space:]"=' | tr -- '-_' '+/')
# Completa o "=" do fim, que alguns geradores (e algumas colagens) deixam de fora.
while [ $(( ${#b64} % 4 )) -ne 0 ]; do b64="$b64="; done
if ! printf '%s' "$b64" | base64 -d > "$jks" 2>/dev/null; then
  # Diagnóstico sem expor o conteúdo: tamanho, resto por 4 e caracteres fora do base64.
  invalidos=$(printf '%s' "$b64" | tr -d 'A-Za-z0-9+/=' | wc -c)
  echo "::error::KEYSTORE_BASE64 não é um base64 válido (${#b64} caracteres, resto por 4 = $(( ${#b64} % 4 )), $invalidos fora do alfabeto). Gere com: base64 -w0 sua-chave.jks"
  exit 1
fi
# Confere a senha do arquivo e o alias antes de compilar, com uma mensagem clara.
if ! keytool -list -keystore "$jks" -storepass "$SONIVELL_STORE_PASSWORD" -alias "$SONIVELL_KEY_ALIAS" > /dev/null 2>&1; then
  # Diagnóstico sem expor segredos: tamanho e tipo do arquivo, o erro do keytool e os aliases.
  tipo=$(od -An -tx1 -N4 "$jks" | tr -d ' ')
  case "$tipo" in feedfeed) tipo="JKS";; 3082*) tipo="PKCS12";; *) tipo="desconhecido ($tipo)";; esac
  erro=$(keytool -list -keystore "$jks" -storepass "$SONIVELL_STORE_PASSWORD" 2>&1 | grep -iE 'error|exception|incorrect|invalid' | head -n 1 || true)
  aliases=$(keytool -list -keystore "$jks" -storepass "$SONIVELL_STORE_PASSWORD" 2>/dev/null | grep -oE '^[^,]+, ' | tr -d ',' | tr '\n' ' ' || true)
  echo "::error::A chave não abriu: arquivo $tipo de $(stat -c %s "$jks") bytes; keytool: ${erro:-sem mensagem}; aliases: ${aliases:-nenhum}. Confira os secrets KEYSTORE_PASSWORD e KEY_ALIAS."
  exit 1
fi
echo "SONIVELL_KEYSTORE=$jks" >> "$GITHUB_ENV"
echo "SONIVELL_BUILD=release" >> "$GITHUB_ENV"
