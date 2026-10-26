#!/usr/bin/env python3
"""Gera FinoIconPaths.kt a partir do pacote npm @material-symbols/svg-300.

Uso:
    npm i @material-symbols/svg-300
    python3 tools/gen_fino_icons.py node_modules/@material-symbols/svg-300/outlined

Material Symbols (Google) — Apache License 2.0.
"""
import re
import sys
from pathlib import Path

# constante Kotlin -> arquivo do Material Symbols (sem .svg)
ICONS = {
    "ARROW_BACK": "arrow_back", "CLOSE": "close", "CHECK": "check", "ADD": "add",
    "REMOVE": "remove", "SEARCH": "search", "SEARCH_FILL": "search-fill",
    "SETTINGS": "settings", "MORE_VERT": "more_vert", "MORE_HORIZ": "more_horiz",
    "ARROW_DOWN": "keyboard_arrow_down", "ARROW_RIGHT": "keyboard_arrow_right",
    "PLAY_FILL": "play_arrow-fill", "PAUSE_FILL": "pause-fill",
    "SKIP_PREVIOUS": "skip_previous", "SKIP_NEXT": "skip_next",
    "SHUFFLE": "shuffle", "SMART": "wand_stars", "REPEAT": "repeat", "REPEAT_ONE": "repeat_one",
    "FAVORITE": "favorite", "FAVORITE_FILL": "favorite-fill",
    "QUEUE_MUSIC": "queue_music", "QUEUE_PLAY_NEXT": "queue_play_next",
    "PLAYLIST_ADD": "playlist_add", "REMOVE_CIRCLE": "do_not_disturb_on",
    "DRAG_HANDLE": "drag_handle", "ALBUM": "album", "PERSON": "person",
    "CATEGORY": "category", "FOLDER": "folder", "FOLDER_OPEN": "folder_open",
    "SHARE": "share", "EDIT": "edit", "DELETE": "delete", "UPLOAD": "upload",
    "BEDTIME": "bedtime", "TIMER": "timer", "TIMER_OFF": "timer_off",
    "MUSIC_NOTE": "music_note", "LYRICS": "lyrics", "GRAPHIC_EQ": "graphic_eq",
    "PHONE": "mobile", "VOLUME_UP": "volume_up", "VOLUME_DOWN": "volume_down",
    "SWAP_VERT": "swap_vert", "HOME": "home", "HOME_FILL": "home-fill",
    "LIBRARY": "library_music", "LIBRARY_FILL": "library_music-fill",
}

def main(src: str) -> None:
    root = Path(src)
    out = Path(__file__).resolve().parent.parent / \
        "app/src/main/java/com/juscelino/sonivell/ui/theme/FinoIconPaths.kt"
    lines = [
        "package com.juscelino.sonivell.ui.theme",
        "",
        "// Gerado por tools/gen_fino_icons.py — não editar à mão.",
        "// Material Symbols Outlined, peso 300 — Apache License 2.0, Google.",
        "// Caminhos no viewBox \"0 -960 960 960\".",
        "",
        "internal object FinoIconPaths {",
    ]
    for const, name in ICONS.items():
        svg = (root / f"{name}.svg").read_text()
        d = " ".join(re.findall(r' d="([^"]+)"', svg))
        if not d:
            sys.exit(f"sem caminho em {name}.svg")
        lines.append(f'    const val {const} = "{d}"')
    lines.append("}")
    out.write_text("\n".join(lines) + "\n")
    print(f"{len(ICONS)} ícones -> {out}")

if __name__ == "__main__":
    main(sys.argv[1])
