#!/usr/bin/env bash
# Gera músicas de exemplo, abre o Sonivell para PC num ambiente gráfico virtual (Xvfb) e tira capturas de tela.
# Uso: tools/desktop-screenshot.sh <pasta-de-saida>
set -euxo pipefail
OUT="${1:-screenshots}"
mkdir -p "$OUT"
DEMO_HOME="$(mktemp -d)"
export HOME="$DEMO_HOME"
export XDG_CONFIG_HOME="$HOME/.config" XDG_DATA_HOME="$HOME/.local/share" XDG_CACHE_HOME="$HOME/.cache"
mkdir -p "$HOME/Music" "$XDG_CONFIG_HOME/sonivell" "$HOME/covers"
cat > "$XDG_CONFIG_HOME/sonivell/settings.properties" <<'PROPS'
theme=DARK
accent=Lima
darkBg=Grafite
PROPS
echo "musicDirs=$HOME/Music" >> "$XDG_CONFIG_HOME/sonivell/settings.properties"

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
make 02.flac "Ancora" "Lua Norte" "Horizonte" Indie 2023 2 247 horizonte flac
make 03.mp3 "Madrugada Azul" "Lua Norte" "Horizonte" Indie 2023 3 262 horizonte mp3
make 04.mp3 "Casa de Vento" "Rio Acima" "Rio Acima" Folk 2021 1 294 rioacima mp3
make 05.mp3 "Pedra e Pó" "Rio Acima" "Rio Acima" Folk 2021 2 330 rioacima mp3
make 06.flac "Farol" "Sal Grosso" "Sal Grosso" Eletrônica 2022 1 349 salgrosso flac
make 07.flac "Brasa Lenta" "Sal Grosso" "Sal Grosso" Eletrônica 2022 2 392 salgrosso flac
make 08.mp3 "Chuva de Sal" "Sal Grosso" "Sal Grosso" Eletrônica 2022 3 440 salgrosso mp3
make 09.flac "Noites de Vidro" "Marina Ruído" "Vidro e Sal" Pop 2020 1 494 vidro flac
make 10.mp3 "Vidro e Sal" "Marina Ruído" "Vidro e Sal" Pop 2020 2 523 vidro mp3
make 11.mp3 "Janela Molhada" "Marina Ruído" "Vidro e Sal" Pop 2020 3 587 vidro mp3

export LANG=pt_BR.UTF-8 JAVA_TOOL_OPTIONS="-Duser.language=pt -Duser.country=BR"
APP=/opt/sonivell/bin/sonivell
export DISPLAY=:99
export SKIKO_RENDER_API=SOFTWARE_FAST
Xvfb :99 -screen 0 1600x1000x24 &
XVFB=$!
sleep 2
"$APP" > "$OUT/app.log" 2>&1 &
APPPID=$!
sleep 25
OX=48; OY=48
shot() { import -window root -crop 1360x860+48+48 +repage "$OUT/$1.png"; }
shot 1-para-voce
# Biblioteca
xdotool mousemove $((110+OX)) $((195+OY)) click 1; sleep 3; shot 2-biblioteca
# Toca a primeira música da tabela
xdotool mousemove $((520+OX)) $((290+OY)) click 1; sleep 4; shot 3-biblioteca-tocando
# Player completo (clica na capa da barra inferior)
xdotool mousemove $((62+OX)) $((800+OY)) click 1; sleep 3; shot 4-player
# Busca
xdotool mousemove $((110+OX)) $((148+OY)) click 1; sleep 1; xdotool type "sal"; sleep 2; shot 5-busca
# Ajustes
xdotool mousemove $((110+OX)) $((820+OY)) click 1; sleep 2; shot 6-ajustes
kill $APPPID || true
kill $XVFB || true
ls -la "$OUT"
tail -n 30 "$OUT/app.log" || true
