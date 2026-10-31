#!/usr/bin/env bash
# Gera o AppImage do Sonivell (Linux x86_64) a partir da distribuição do Compose Desktop.
#
#   tools/desktop-appimage.sh            # roda o Gradle (createDistributable) e empacota
#   SKIP_GRADLE=1 tools/desktop-appimage.sh   # reaproveita uma distribuição já gerada
#
# O AppImage leva junto o ffmpeg/ffprobe estáticos (o app depende deles para tocar, ler etiquetas
# e para o SMART), então roda em qualquer distro sem instalar nada. FFMPEG_URL=none pula isso e
# usa o ffmpeg do sistema. Resultado: desktop/build/appimage/Sonivell-<versão>-x86_64.AppImage
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

# Versões fixas, conferidas pelo SHA-256 (auditoria SUP-02): o mesmo binário em todo build.
# O BtbN apaga os autobuilds antigos depois de algumas semanas; quando a URL der 404, escolha um
# novo (git ls-remote --tags https://github.com/BtbN/FFmpeg-Builds; o nome do arquivo segue o
# "ffmpeg -version": ffmpeg-N-<rev>-g<hash>-linux64-gpl.tar.xz) e atualize URL e hash juntos.
FFMPEG_URL=${FFMPEG_URL:-https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-10-31-12-59/ffmpeg-N-128227-g4811625b5f-linux64-gpl.tar.xz}
FFMPEG_SHA256=${FFMPEG_SHA256-67929be39bbb696f41d3295686597a439aeac902f61d82be660ccc1453c1fdfb}
APPIMAGETOOL_URL=${APPIMAGETOOL_URL:-https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage}
APPIMAGETOOL_SHA256=${APPIMAGETOOL_SHA256-ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0}
# Com outra URL, passe também o hash dela; FFMPEG_SHA256= (vazio) só avisa, sem conferir.

# Confere o SHA-256 de $1 contra $2 (vazio = só avisa). Imprime o hash calculado.
verify() {
  local file=$1 expected=$2 name=$3 actual
  actual=$(sha256sum "$file" | cut -d' ' -f1)
  if [ -n "$expected" ]; then
    if [ "$actual" != "$expected" ]; then
      echo "ERRO: $name com SHA-256 $actual, esperado $expected. Abortando." >&2
      exit 1
    fi
  else
    echo "AVISO: $name sem SHA-256 fixado (baixado de $([ "$name" = ffmpeg ] && echo "$FFMPEG_URL" || echo "$APPIMAGETOOL_URL")); hash: $actual" >&2
    if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::warning::$name sem SHA-256 fixado; hash usado: $actual" >&2; fi
  fi
  echo "$actual"
}

VERSION=$(grep -oP 'packageVersion\s*=\s*"\K[^"]+' desktop/build.gradle.kts)
OUT="$ROOT/desktop/build/appimage"
WORK="$OUT/work"
APPDIR="$WORK/Sonivell.AppDir"
DIST="$ROOT/desktop/build/compose/binaries/main/app/sonivell"

if [ -z "${SKIP_GRADLE:-}" ]; then
  ./gradlew -Psonivell.desktop=only :desktop:createDistributable
fi
[ -x "$DIST/bin/sonivell" ] || { echo "distribuição não encontrada em $DIST" >&2; exit 1; }

rm -rf "$WORK" && mkdir -p "$APPDIR/usr/bin" "$OUT"

# 1. O app (JVM própria + jars + libs nativas), como o jpackage montou.
cp -a "$DIST" "$APPDIR/sonivell"

# 2. ffmpeg + ffprobe estáticos.
if [ "$FFMPEG_URL" != "none" ]; then
  curl -fsSL --retry 3 -o "$WORK/ffmpeg.tar.xz" "$FFMPEG_URL"
  FFMPEG_HASH=$(verify "$WORK/ffmpeg.tar.xz" "$FFMPEG_SHA256" ffmpeg)
  mkdir -p "$WORK/ffmpeg" && tar -xJf "$WORK/ffmpeg.tar.xz" -C "$WORK/ffmpeg" --strip-components=1
  install -m 0755 "$WORK/ffmpeg/bin/ffmpeg" "$WORK/ffmpeg/bin/ffprobe" "$APPDIR/usr/bin/"
  mkdir -p "$APPDIR/usr/share/doc/ffmpeg"
  cp "$WORK/ffmpeg/LICENSE.txt" "$APPDIR/usr/share/doc/ffmpeg/" 2>/dev/null || true
  # Rastreabilidade da GPL: qual binário exato foi embutido (o "-version" traz a revisão do código-fonte).
  {
    echo "ffmpeg: $FFMPEG_URL (GPL; binários independentes, executados como processos)"
    echo "SHA-256 do pacote: $FFMPEG_HASH"
    "$APPDIR/usr/bin/ffmpeg" -hide_banner -version 2>/dev/null | head -n 1 || true
    echo "Código-fonte: https://github.com/FFmpeg/FFmpeg (revisão acima) e https://github.com/BtbN/FFmpeg-Builds"
  } > "$APPDIR/usr/share/doc/ffmpeg/SOURCE"
fi

# 3. Ponto de entrada: o ffmpeg embutido vem primeiro no PATH (versão conhecida e testada).
cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE=$(dirname "$(readlink -f "$0")")
export PATH="$HERE/usr/bin:$PATH"
exec "$HERE/sonivell/bin/sonivell" "$@"
EOF
chmod 0755 "$APPDIR/AppRun"

# 4. Atalho e ícone (o appimagetool exige os dois na raiz do AppDir).
cp desktop/packaging/sonivell.png "$APPDIR/sonivell.png"
mkdir -p "$APPDIR/usr/share/icons/hicolor/512x512/apps"
cp desktop/packaging/sonivell.png "$APPDIR/usr/share/icons/hicolor/512x512/apps/sonivell.png"
cat > "$APPDIR/sonivell.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Sonivell
Comment=Player de música moderno, leve e com identidade própria
Exec=sonivell %F
Icon=sonivell
Categories=AudioVideo;Audio;Player;
MimeType=audio/mpeg;audio/flac;audio/ogg;audio/x-wav;audio/mp4;audio/aac;audio/opus;
Terminal=false
X-AppImage-Version=$VERSION
EOF

# 5. Empacota. Sem FUSE (CI, contêineres) o appimagetool roda extraído.
TOOL="$WORK/appimagetool"
curl -fsSL --retry 3 -o "$TOOL" "$APPIMAGETOOL_URL"
verify "$TOOL" "$APPIMAGETOOL_SHA256" appimagetool >/dev/null
chmod +x "$TOOL"
TARGET="$OUT/Sonivell-$VERSION-x86_64.AppImage"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --no-appstream "$APPDIR" "$TARGET"

rm -rf "$WORK"
echo "AppImage: $TARGET ($(du -h "$TARGET" | cut -f1))"
