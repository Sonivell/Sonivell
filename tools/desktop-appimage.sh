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

FFMPEG_URL=${FFMPEG_URL:-https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz}
APPIMAGETOOL_URL=${APPIMAGETOOL_URL:-https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage}

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
  mkdir -p "$WORK/ffmpeg" && tar -xJf "$WORK/ffmpeg.tar.xz" -C "$WORK/ffmpeg" --strip-components=1
  install -m 0755 "$WORK/ffmpeg/bin/ffmpeg" "$WORK/ffmpeg/bin/ffprobe" "$APPDIR/usr/bin/"
  mkdir -p "$APPDIR/usr/share/doc/ffmpeg"
  cp "$WORK/ffmpeg/LICENSE.txt" "$APPDIR/usr/share/doc/ffmpeg/" 2>/dev/null || true
  echo "ffmpeg: $FFMPEG_URL (GPL; binários independentes, executados como processos)" \
    > "$APPDIR/usr/share/doc/ffmpeg/SOURCE"
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
curl -fsSL --retry 3 -o "$TOOL" "$APPIMAGETOOL_URL" && chmod +x "$TOOL"
TARGET="$OUT/Sonivell-$VERSION-x86_64.AppImage"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --no-appstream "$APPDIR" "$TARGET"

rm -rf "$WORK"
echo "AppImage: $TARGET ($(du -h "$TARGET" | cut -f1))"
