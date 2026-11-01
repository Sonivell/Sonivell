#!/usr/bin/env bash
# CI: confere a assinatura dos APKs de release e registra o SHA-256 do certificado no resumo.
set -euo pipefail
apksigner=$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -n 1)
chave_teste=882ef7c4a4ff1dcce21802ac5eef4779473c90c304ac83770ef4f0535fa1477b
echo "### Assinatura dos APKs" >> "$GITHUB_STEP_SUMMARY"
for apk in app/build/outputs/apk/*/release/*.apk; do
  if ! "$apksigner" verify --print-certs "$apk" > certs.txt 2>&1; then
    cat certs.txt; echo "::error::$apk sem assinatura válida"; exit 1
  fi
  # O começo da linha muda entre versões do apksigner ("Signer #1", "Signer (minSdkVersion=…)"),
  # então pega o primeiro certificado pelo fim da linha.
  sha=$(grep -m1 -oE 'certificate SHA-256 digest: [0-9a-f]{64}' certs.txt | awk '{print $NF}' || true)
  dn=$(grep -m1 'certificate DN: ' certs.txt | sed 's/^.*certificate DN: //' || true)
  [ -n "$sha" ] || { cat certs.txt; echo "::error::$apk: SHA-256 do certificado não encontrado na saída do apksigner"; exit 1; }
  [ "$sha" != "$chave_teste" ] || { echo "::error::$apk foi assinado com a antiga chave de TESTE"; exit 1; }
  echo "::notice::$(basename "$apk"): $dn, SHA-256 $sha"
  echo "- \`$(basename "$apk")\`: $dn · SHA-256 \`$sha\`" >> "$GITHUB_STEP_SUMMARY"
done
