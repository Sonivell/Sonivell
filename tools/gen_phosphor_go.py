#!/usr/bin/env python3
"""Gera desktop-go/internal/ui/phosphor_gen.go (ícones do Sonivell GO).

Lê os SVGs oficiais do Phosphor (https://github.com/phosphor-icons/core, licença MIT) e grava os
caminhos de cada ícone nos pesos Regular e Fill (o Fill marca o item ativo). Para trocar ou
acrescentar um ícone, edite NAMES e rode o script.

Uso:
    git clone --depth 1 https://github.com/phosphor-icons/core /tmp/phosphor-core
    python3 tools/gen_phosphor_go.py /tmp/phosphor-core
"""
import pathlib
import re
import shutil
import subprocess
import sys

NAMES = """
arrow-left caret-down caret-right x check plus trash magnifying-glass gear-six dots-three
play pause skip-back skip-forward shuffle repeat repeat-once heart queue vinyl-record user
music-notes music-note squares-four folder-open folder-plus speaker-high speaker-low speaker-x
picture-in-picture arrows-out-simple sparkle clock arrow-clockwise info palette
""".split()

WEIGHTS = ["regular", "fill"]
OUT = pathlib.Path(__file__).resolve().parent.parent / "desktop-go/internal/ui/phosphor_gen.go"


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
        f"// Gerado por tools/gen_phosphor_go.py a partir de phosphor-icons/core {version}",
        "// (https://phosphoricons.com), licença MIT. Copyright (c) 2023 Phosphor Icons.",
        "// Não edite à mão: os caminhos vêm dos SVGs oficiais.",
        "",
        "package ui",
        "",
        "// Os ícones do Phosphor usados no Sonivell GO (viewBox 0 0 256 256, área cheia).",
        "var (",
    ]
    for name in sorted(NAMES):
        regular, fill = (path_of(core, name, w) for w in WEIGHTS)
        out.append(f'\tIc{camel(name)} = &Icon{{Regular: "{regular}", Fill: "{fill}"}}')
    out.append(")")
    OUT.write_text("\n".join(out) + "\n")
    if shutil.which("gofmt"):
        subprocess.run(["gofmt", "-w", str(OUT)], check=True)
    print(f"{len(NAMES)} ícones gravados em {OUT}")


if __name__ == "__main__":
    main()
