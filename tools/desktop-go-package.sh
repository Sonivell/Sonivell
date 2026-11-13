#!/usr/bin/env bash
# Compila o Sonivell GO e gera o .deb e o AppImage (Linux x86_64).
#
#   tools/desktop-go-package.sh               # binário + .deb + AppImage
#   SKIP_APPIMAGE=1 tools/desktop-go-package.sh   # só binário e .deb
#
# Dependências de build (Debian/Ubuntu): golang 1.24+, gcc, pkg-config, dpkg-dev, fakeroot e
#   libwayland-dev libx11-dev libx11-xcb-dev libxkbcommon-x11-dev libgles2-mesa-dev libegl1-mesa-dev
#   libffi-dev libxcursor-dev libxfixes-dev libvulkan-dev libasound2-dev
# Resultado em desktop-go/build/: sonivell-go, sonivell-go_<versão>_amd64.deb e
# SonivellGO-<versão>-x86_64.AppImage.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
VERSION=$(grep -oP '^versionName=\K\S+' version.properties)
OUT="$ROOT/desktop-go/build"
rm -rf "$OUT" && mkdir -p "$OUT"

# 1. O binário (um só arquivo, sem JVM).
(cd desktop-go && CGO_ENABLED=1 go build -trimpath -ldflags "-s -w -X main.version=$VERSION" -o "$OUT/sonivell-go" .)
"$OUT/sonivell-go" --version
echo "binário: $(du -h "$OUT/sonivell-go" | cut -f1)"

DESKTOP_ENTRY="[Desktop Entry]
Type=Application
Name=Sonivell GO
GenericName=Player de música
Comment=Sonivell reescrito em Go: mais leve e mais rápido
Comment[en]=Sonivell rewritten in Go: lighter and faster
Exec=sonivell-go
Icon=sonivell-go
StartupWMClass=sonivell-go
Categories=AudioVideo;Audio;Player;
Keywords=música;music;player;sonivell;
Terminal=false"

# 2. O .deb, montado direto com o dpkg-deb (as bibliotecas vêm do dpkg-shlibdeps).
PKG="$OUT/deb/sonivell-go"
install -Dm0755 "$OUT/sonivell-go" "$PKG/usr/bin/sonivell-go"
install -Dm0644 desktop-go/packaging/sonivell.png "$PKG/usr/share/icons/hicolor/512x512/apps/sonivell-go.png"
install -Dm0644 desktop-go/packaging/sonivell.svg "$PKG/usr/share/icons/hicolor/scalable/apps/sonivell-go.svg"
mkdir -p "$PKG/usr/share/applications" "$PKG/usr/share/doc/sonivell-go"
printf '%s\n' "$DESKTOP_ENTRY" > "$PKG/usr/share/applications/sonivell-go.desktop"
{
  echo "Sonivell GO $VERSION — https://github.com/Sonivell/Sonivell"
  echo
  echo "Ícones: Phosphor Icons (MIT)."
  cat LICENSE-Phosphor.txt
  echo
  echo "Fonte: Manrope (SIL Open Font License 1.1)."
  cat OFL-Manrope.txt
} > "$PKG/usr/share/doc/sonivell-go/copyright"

SHLIB=$(mktemp -d)
mkdir -p "$SHLIB/debian"
printf 'Source: sonivell-go\n\nPackage: sonivell-go\nArchitecture: amd64\n' > "$SHLIB/debian/control"
LIBS=$(cd "$SHLIB" && dpkg-shlibdeps -O "$PKG/usr/bin/sonivell-go" 2>/dev/null | sed -n 's/^shlibs:Depends=//p')
rm -rf "$SHLIB"
[ -n "$LIBS" ] || { echo "dpkg-shlibdeps não achou as bibliotecas" >&2; exit 1; }

mkdir -p "$PKG/DEBIAN"
cat > "$PKG/DEBIAN/control" <<EOF
Package: sonivell-go
Version: $VERSION
Architecture: amd64
Maintainer: Juscelino Be <sonivell@users.noreply.github.com>
Installed-Size: $(du -sk "$PKG" | cut -f1)
Depends: ffmpeg, $LIBS
Recommends: pipewire-alsa | libasound2-plugins, zenity | kdialog
Section: sound
Priority: optional
Homepage: https://github.com/Sonivell/Sonivell
Description: Sonivell GO — player de música reescrito em Go
 O Sonivell para Linux reescrito em Go: um binário só, sem Java, que abre
 mais rápido e usa menos memória. Mesmo visual Ondas e player Vidro, ícones
 Phosphor, teclas de mídia (MPRIS) e modo compacto.
EOF
fakeroot dpkg-deb --root-owner-group -Zxz -b "$PKG" "$OUT/sonivell-go_${VERSION}_amd64.deb" >/dev/null
rm -rf "$OUT/deb"
dpkg-deb -I "$OUT/sonivell-go_${VERSION}_amd64.deb" | sed -n '/Package:/,/Description:/p'
echo ".deb: $(du -h "$OUT/sonivell-go_${VERSION}_amd64.deb" | cut -f1)"

[ -z "${SKIP_APPIMAGE:-}" ] || exit 0

# 3. O AppImage, com o mesmo ffmpeg estático (e conferido) do Sonivell em Kotlin.
FFMPEG_URL=$(grep -oP '^FFMPEG_URL=\$\{FFMPEG_URL:-\K[^}]+' tools/desktop-appimage.sh)
FFMPEG_SHA256=$(grep -oP '^FFMPEG_SHA256=\$\{FFMPEG_SHA256-\K[^}]+' tools/desktop-appimage.sh)
APPIMAGETOOL_URL=$(grep -oP '^APPIMAGETOOL_URL=\$\{APPIMAGETOOL_URL:-\K[^}]+' tools/desktop-appimage.sh)
APPIMAGETOOL_SHA256=$(grep -oP '^APPIMAGETOOL_SHA256=\$\{APPIMAGETOOL_SHA256-\K[^}]+' tools/desktop-appimage.sh)
check() { # arquivo hash nome
  local got
  got=$(sha256sum "$1" | cut -d' ' -f1)
  [ "$got" = "$2" ] || { echo "ERRO: $3 com SHA-256 $got, esperado $2" >&2; exit 1; }
}

WORK="$OUT/appimage"
APPDIR="$WORK/SonivellGO.AppDir"
mkdir -p "$APPDIR/usr/bin" "$APPDIR/usr/share/icons/hicolor/512x512/apps"
install -m0755 "$OUT/sonivell-go" "$APPDIR/usr/bin/"
curl -fsSL --retry 3 -o "$WORK/ffmpeg.tar.xz" "$FFMPEG_URL"
check "$WORK/ffmpeg.tar.xz" "$FFMPEG_SHA256" ffmpeg
mkdir -p "$WORK/ffmpeg" && tar -xJf "$WORK/ffmpeg.tar.xz" -C "$WORK/ffmpeg" --strip-components=1
install -m0755 "$WORK/ffmpeg/bin/ffmpeg" "$WORK/ffmpeg/bin/ffprobe" "$APPDIR/usr/bin/"
mkdir -p "$APPDIR/usr/share/doc/ffmpeg"
cp "$WORK/ffmpeg/LICENSE.txt" "$APPDIR/usr/share/doc/ffmpeg/" 2>/dev/null || true
echo "ffmpeg: $FFMPEG_URL (SHA-256 $FFMPEG_SHA256). Código-fonte: https://github.com/FFmpeg/FFmpeg" > "$APPDIR/usr/share/doc/ffmpeg/SOURCE"
cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE=$(dirname "$(readlink -f "$0")")
export PATH="$HERE/usr/bin:$PATH"
exec "$HERE/usr/bin/sonivell-go" "$@"
EOF
chmod 0755 "$APPDIR/AppRun"
cp desktop-go/packaging/sonivell.png "$APPDIR/sonivell-go.png"
cp desktop-go/packaging/sonivell.png "$APPDIR/usr/share/icons/hicolor/512x512/apps/sonivell-go.png"
printf '%s\nX-AppImage-Version=%s\n' "$DESKTOP_ENTRY" "$VERSION" > "$APPDIR/sonivell-go.desktop"

TOOL="$WORK/appimagetool"
curl -fsSL --retry 3 -o "$TOOL" "$APPIMAGETOOL_URL"
check "$TOOL" "$APPIMAGETOOL_SHA256" appimagetool
chmod +x "$TOOL"
TARGET="$OUT/SonivellGO-$VERSION-x86_64.AppImage"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --no-appstream "$APPDIR" "$TARGET"
rm -rf "$WORK"
echo "AppImage: $TARGET ($(du -h "$TARGET" | cut -f1))"
