#!/usr/bin/env python3
"""Gera app/src/main/java/com/juscelino/sonivell/ui/theme/IconsaxIcons.kt.

Lê os SVGs do Iconsax (https://github.com/lusaxweb/iconsax, pasta static/Iconsax/Svg/All) e grava
os caminhos de cada ícone nos estilos Linear (traço) e Bold (cheio). O Iconsax é gratuito para uso
pessoal e comercial, mas não pode ser vendido nem redistribuído com outro nome, nem usado para
montar templates ou UI kits (veja LICENSE-Iconsax.txt). Para trocar ou acrescentar um ícone,
edite NAMES, rode o script e use o novo campo em AppIcons.kt.

Uso:
    git clone --depth 1 https://github.com/lusaxweb/iconsax /tmp/iconsax
    python3 tools/gen_iconsax_icons.py /tmp/iconsax
"""
import pathlib
import re
import sys

NAMES = """
add arrow-left arrow-left-1 arrow-right-1 arrow-swap-horizontal car category cd chart clock close-circle
cloud-change colorfilter edit-2 export folder folder-open gallery gallery-slash heart home-2 info-circle
magic-star menu microphone-2 minus minus-cirlce mobile moon more music-filter music-library-2 music-square
music-square-add musicnote next notification-bing pause play play-add play-circle previous profile-circle
repeate-music repeate-one save-add search-normal-1 setting-2 setting-4 share slash sort sound star star-1
textalign-justifyleft timer-1 trash trend-down trend-up user user-minus volume-high volume-low-1
""".split()

STYLES = ["linear", "bold"]
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/juscelino/sonivell/ui/theme/IconsaxIcons.kt"


def camel(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("-"))


def attr(tag: str, name: str) -> str | None:
    m = re.search(rf'\s{name}="([^"]*)"', tag)
    return m.group(1) if m else None


def parts_of(root: pathlib.Path, name: str, style: str) -> tuple[tuple[float, float], list[str]]:
    svg = (root / "static/Iconsax/Svg/All" / style / f"{name}.svg").read_text()
    vb = re.search(r'viewBox="0 0 ([0-9.]+) ([0-9.]+)"', svg)
    if not vb or float(vb.group(1)) > 24 or float(vb.group(2)) > 24:
        sys.exit(f"{name}-{style}: viewBox inesperado")
    others = set(re.findall(r"<([a-zA-Z]+)\b", svg)) - {"svg", "path"}
    if others:
        sys.exit(f"{name}-{style}: só caminhos <path> são aceitos ({others})")
    out = []
    for tag in re.findall(r"<path\b[^>]*>", svg):
        d = attr(tag, "d")
        fill = attr(tag, "fill") not in (None, "none")
        stroke = attr(tag, "stroke") not in (None, "none")
        width = float(attr(tag, "stroke-width") or "1") if stroke else 0.0
        cap = (attr(tag, "stroke-linecap") or "butt")[0]
        join = (attr(tag, "stroke-linejoin") or "miter")[0]
        alpha = float(attr(tag, "opacity") or "1")
        if attr(tag, "stroke-dasharray"):
            sys.exit(f"{name}-{style}: traço tracejado não é suportado")
        out.append(f'IxPart("{d}", {str(fill).lower()}, {width}f, \'{cap}\', \'{join}\', {alpha}f)')
    if not out:
        sys.exit(f"{name}-{style}: nenhum caminho")
    # SVG recortado (menor que 24): desloca para o centro da área de 24.
    return ((24 - float(vb.group(1))) / 2, (24 - float(vb.group(2))) / 2), out


def main() -> None:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/iconsax")
    out = [
        "// Gerado a partir do Iconsax (https://github.com/lusaxweb/iconsax), estilos Linear e Bold.",
        "// Uso gratuito pessoal e comercial; veja LICENSE-Iconsax.txt. Não edite à mão: rode",
        "// tools/gen_iconsax_icons.py.",
        "package com.juscelino.sonivell.ui.theme",
        "",
        "/**",
        " * Um traço ou forma de um ícone do Iconsax (viewBox 0 0 24 24): [fill] cheio, [stroke] a",
        " * espessura do traço (0 = sem traço), [cap] e [join] a primeira letra de round/butt/square e",
        " * round/miter/bevel, [alpha] a opacidade.",
        " */",
        "internal class IxPart(val d: String, val fill: Boolean, val stroke: Float, val cap: Char, val join: Char, val alpha: Float)",
        "",
        "/** Um ícone do Iconsax nos dois estilos; os *Shift centralizam os SVGs recortados. */",
        "internal class IxIcon(val linear: List<IxPart>, val bold: List<IxPart>, val linearShift: Pair<Float, Float> = NO_SHIFT, val boldShift: Pair<Float, Float> = NO_SHIFT)",
        "",
        "internal val NO_SHIFT = 0f to 0f",
        "",
        "/** Os ícones do Iconsax usados no app. */",
        "internal object Iconsax {",
    ]
    for name in sorted(NAMES):
        out.append(f"    val {camel(name)} = IxIcon(")
        sizes = []
        for style in STYLES:
            size, parts = parts_of(root, name, style)
            sizes.append(size)
            out.append("        listOf(")
            out += [f"            {p}," for p in parts]
            out.append("        ),")
        for style, (dx, dy) in zip(STYLES, sizes):
            if (dx, dy) != (0, 0):
                out.append(f"        {style}Shift = {dx}f to {dy}f,")
        out.append("    )")
    out.append("}")
    OUT.write_text("\n".join(out) + "\n")
    print(f"{len(NAMES)} ícones -> {OUT}")


if __name__ == "__main__":
    main()
