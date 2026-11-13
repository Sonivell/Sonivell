#!/usr/bin/env bash
# Gera músicas de exemplo, abre o Sonivell GO num ambiente gráfico virtual (Xvfb) e tira capturas.
# Uso: tools/desktop-go-screenshot.sh <binário> <pasta-de-saida>
set -euxo pipefail
APP=$(readlink -f "${1:-desktop-go/build/sonivell-go}")
OUT=$(readlink -m "${2:-screenshots-go}")
mkdir -p "$OUT"
DEMO_HOME="$(mktemp -d)"
export HOME="$DEMO_HOME"
export XDG_CONFIG_HOME="$HOME/.config" XDG_DATA_HOME="$HOME/.local/share" XDG_CACHE_HOME="$HOME/.cache"
mkdir -p "$HOME/Music" "$XDG_CONFIG_HOME/sonivell-go" "$HOME/covers"
cat > "$XDG_CONFIG_HOME/sonivell-go/settings.json" <<JSON
{"musicDirs": ["$HOME/Music"], "volume": 0.8, "accent": "Lima"}
JSON

cover() { # nome corFundo corNucleo
  ffmpeg -loglevel error -y -f lavfi -i "color=c=0x$2:s=600x600,drawbox=x=150:y=150:w=300:h=300:color=0x$3@0.55:t=fill,drawbox=x=250:y=250:w=100:h=100:color=0x$3:t=fill" -frames:v 1 "$HOME/covers/$1.png"
}
cover horizonte 1B2A26 5FE3D0
cover rioacima 2A1D30 C98BE8
cover salgrosso 3A2A14 FFC46B
cover vidro 1F2A3D 8FB8FF

make() { # arquivo titulo artista album genero ano faixa frequencia capa codec
  local f="$HOME/Music/$1"
  if [ "${10}" = flac ]; then local codec="-c:a flac"; else local codec="-c:a libmp3lame -b:a 192k -id3v2_version 3"; fi
  ffmpeg -loglevel error -y -f lavfi -i "sine=f=$8:d=95" -i "$HOME/covers/$9.png" -map 0:a -map 1:v $codec -c:v copy -disposition:v attached_pic \
    -metadata title="$2" -metadata artist="$3" -metadata album="$4" -metadata genre="$5" -metadata date="$6" -metadata track="$7" "$f"
}
make 01.flac "Maré Alta" "Lua Norte" "Horizonte" Indie 2023 1 220 horizonte flac
make 02.flac "Âncora" "Lua Norte" "Horizonte" Indie 2023 2 247 horizonte flac
make 03.mp3 "Madrugada Azul" "Lua Norte" "Horizonte" Indie 2023 3 262 horizonte mp3
make 04.mp3 "Casa de Vento" "Rio Acima" "Rio Acima" Folk 2021 1 294 rioacima mp3
make 05.mp3 "Pedra e Pó" "Rio Acima" "Rio Acima" Folk 2021 2 330 rioacima mp3
make 06.flac "Farol" "Sal Grosso" "Sal Grosso" Eletrônica 2022 1 349 salgrosso flac
make 07.flac "Brasa Lenta" "Sal Grosso" "Sal Grosso" Eletrônica 2022 2 392 salgrosso flac
make 08.mp3 "Chuva de Sal" "Sal Grosso" "Sal Grosso" Eletrônica 2022 3 440 salgrosso mp3
make 09.flac "Noites de Vidro" "Marina Ruído" "Vidro e Sal" Pop 2020 1 494 vidro flac
make 10.mp3 "Vidro e Sal" "Marina Ruído" "Vidro e Sal" Pop 2020 2 523 vidro mp3
make 11.mp3 "Janela Molhada" "Marina Ruído" "Vidro e Sal" Pop 2020 3 587 vidro mp3
make 12.flac "Neblina" "Rio Acima" "Rio Acima" Jazz 1998 3 262 rioacima flac
printf '#EXTM3U\n#PLAYLIST:Estrada\n06.flac\n01.flac\n09.flac\n04.mp3\n' > "$HOME/Music/estrada.m3u8"

export LANG=pt_BR.UTF-8 LIBGL_ALWAYS_SOFTWARE=1
export DISPLAY=:99
Xvfb :99 -screen 0 1400x900x24 &
XVFB=$!
trap 'kill $XVFB 2>/dev/null || true' EXIT
sleep 2

# Tempo até abrir (o binário nativo, sem JVM): da execução ao primeiro mapa da janela.
start=$(date +%s%N)
"$APP" > "$OUT/app-startup.log" 2>&1 &
PID=$!
for _ in $(seq 1 200); do
  if xdotool search --name "Sonivell GO" >/dev/null 2>&1; then break; fi
  sleep 0.05
done
end=$(date +%s%N)
echo "janela em $(( (end - start) / 1000000 )) ms" | tee "$OUT/startup.txt"
sleep 4
grep -E "VmRSS|VmHWM" /proc/$PID/status | tee -a "$OUT/startup.txt" || true
import -window root -crop 1280x820+0+0 +repage "$OUT/0-primeira-abertura.png"
# Clique de verdade: Biblioteca na barra lateral.
xdotool mousemove 120 192 click 1; sleep 1.5
import -window root -crop 1280x820+0+0 +repage "$OUT/0b-clique-biblioteca.png"
kill $PID; wait $PID 2>/dev/null || true

shot() { # tela nome largura altura
  SONIVELL_GO_DEMO="$1" "$APP" > "$OUT/app-$1.log" 2>&1 &
  local pid=$!
  sleep 6
  import -window root -crop "$3x$4+0+0" +repage "$OUT/$2.png"
  kill $pid; wait $pid 2>/dev/null || true
}
shot home 1-para-voce 1280 820
shot library 2-biblioteca 1280 820
shot album 3-album 1280 820
shot nowplaying 4-player-vidro 1280 820
shot queue 5-fila 1280 820
shot settings 6-ajustes 1280 820
shot genres 6b-generos 1280 820
shot playlists 6c-playlists 1280 820
shot compact 7-compacto 380 600
ls -la "$OUT"
