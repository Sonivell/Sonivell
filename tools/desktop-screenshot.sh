#!/usr/bin/env bash
# Gera músicas de exemplo, abre o Sonivell para PC num ambiente gráfico virtual (Xvfb) e tira capturas de tela.
# Uso: tools/desktop-screenshot.sh <pasta-de-saida>
set -euxo pipefail
OUT="${1:-screenshots}"
mkdir -p "$OUT"
DEMO_HOME="$(mktemp -d)"
export HOME="$DEMO_HOME"
export XDG_CONFIG_HOME="$HOME/.config" XDG_DATA_HOME="$HOME/.local/share" XDG_CACHE_HOME="$HOME/.cache"
mkdir -p "$HOME/Music" "$HOME/covers" "$XDG_DATA_HOME/sonivell/shared_prefs"

# Preferências no formato do app (shared_prefs/sonivell.xml): estilo, tema escuro, Smart ligado.
prefs() { # estilo
  cat > "$XDG_DATA_HOME/sonivell/shared_prefs/sonivell.xml" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="ui_style">$1</string>
    <int name="theme" value="2" />
    <boolean name="smart_mode" value="true" />
    <boolean name="welcome_seen" value="true" />
    <boolean name="desktop_migrated_v2" value="true" />
    <set name="music_dirs">
        <string>$HOME/Music</string>
    </set>
</map>
XML
}

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
OX=48; OY=48
shot() { import -window root -crop 1400x880+48+48 +repage "$OUT/$1.png"; }
tab() { xdotool mousemove $((110+OX)) $((OY+$1)) click 1; sleep 3; }
WAIT=50
for STYLE in ONDAS SONIVELL ESSENCIAL CONTRASTE CLASSICA; do
  s=$(echo "$STYLE" | tr 'A-Z' 'a-z')
  prefs "$STYLE"
  # --action=shuffle: toca a biblioteca em ordem aleatória assim que ela carregar.
  "$APP" --action=shuffle > "$OUT/app-$s.log" 2>&1 &
  APPPID=$!
  sleep $WAIT
  WAIT=25
  W=$(xdotool search --name Sonivell | head -n 1 || true)
  [ -n "$W" ] && xdotool windowmove "$W" $OX $OY windowsize "$W" 1400 880 && xdotool windowactivate "$W" || true
  sleep 3
  tab 97; shot "$s-1-para-voce"
  tab 147; xdotool type "sal"; sleep 3; shot "$s-2-explorar"
  tab 197; shot "$s-3-biblioteca"
  tab 247; shot "$s-4-perfil"
  xdotool key ctrl+p; sleep 3; shot "$s-5-player"
  xdotool key Escape; sleep 1
  xdotool key ctrl+l; sleep 3; shot "$s-6-letra"
  xdotool key Escape; sleep 1
  xdotool key ctrl+comma; sleep 3; shot "$s-7-ajustes"
  xdotool key Escape; sleep 1
  if [ "$s" = ondas ]; then xdotool key ctrl+m; sleep 4; import -window root "$OUT/ondas-8-compacto.png"; xdotool key ctrl+m; sleep 2; fi
  kill $APPPID || true
  sleep 3
done
kill $XVFB || true
ls -la "$OUT"
tail -n 30 "$OUT"/app-ondas.log || true
