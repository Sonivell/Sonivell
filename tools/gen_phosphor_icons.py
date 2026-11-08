#!/usr/bin/env python3
"""Gera app/src/main/java/com/juscelino/sonivell/ui/theme/PhosphorIcons.kt.

Lê os SVGs oficiais do Phosphor (https://github.com/phosphor-icons/core, licença MIT) e grava os
caminhos de cada ícone nos pesos Light, Regular, Bold e Fill. Para trocar ou acrescentar um ícone,
edite NAMES, rode o script e use o novo campo em AppIcons.kt.

Uso:
    git clone --depth 1 https://github.com/phosphor-icons/core /tmp/phosphor-core
    python3 tools/gen_phosphor_icons.py /tmp/phosphor-core
"""
import pathlib
import re
import sys

NAMES = """
arrow-left x check plus minus magnifying-glass gear-six dots-three-vertical dots-three caret-down caret-right
play pause skip-back skip-forward shuffle sparkle repeat repeat-once heart queue rows-plus-top list-plus minus-circle
dots-six-vertical vinyl-record user guitar folder folder-open share-network pencil-simple trash export moon timer x-circle
music-note microphone-stage equalizer device-mobile speaker-high speaker-low arrows-down-up house music-notes squares-four
list user-circle trend-up trend-down star-four clock star image image-broken bell-ringing prohibit user-minus
sliders-horizontal floppy-disk palette car play-circle arrows-left-right chart-line-up cloud-arrow-up info
""".split()

WEIGHTS = ["light", "regular", "bold", "fill"]
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/juscelino/sonivell/ui/theme/PhosphorIcons.kt"


def camel(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("-"))


def path_of(core: pathlib.Path, name: str, weight: str) -> str:
    suffix = "" if weight == "regular" else f"-{weight}"
    svg = (core / "assets" / weight / f"{name}{suffix}.svg").read_text()
    if re.search(r"<(circle|rect|line|polyline|polygon|ellipse)\b", svg):
        sys.exit(f"{name}-{weight}: só caminhos <path> são aceitos")
    paths = re.findall(r'<path d="([^"]+)"', svg)
    if not paths:
        sys.exit(f"{name}-{weight}: nenhum caminho")
    return " ".join(paths)


def main() -> None:
    core = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/phosphor-core")
    version = re.search(r'"version":\s*"([^"]+)"', (core / "package.json").read_text()).group(1)
    out = [
        f"// Gerado a partir de phosphor-icons/core {version} (https://phosphoricons.com), licença MIT.",
        "// Copyright (c) 2023 Phosphor Icons. Não edite à mão: os caminhos vêm dos SVGs oficiais.",
        "package com.juscelino.sonivell.ui.theme",
        "",
        "/** Um ícone do Phosphor nos quatro pesos que o Sonivell usa (viewBox 0 0 256 256, área cheia). */",
        "internal class PhosphorPath(val light: String, val regular: String, val bold: String, val fill: String)",
        "",
        "/** Os ícones do Phosphor usados no app. */",
        "internal object Phosphor {",
    ]
    for name in sorted(NAMES):
        out.append(f"    val {camel(name)} = PhosphorPath(")
        out += [f'        "{path_of(core, name, w)}",' for w in WEIGHTS]
        out.append("    )")
    out.append("}")
    OUT.write_text("\n".join(out) + "\n")
    print(f"{len(NAMES)} ícones -> {OUT}")


if __name__ == "__main__":
    main()
