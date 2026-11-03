#!/usr/bin/env python3
"""Gera os ícones do Sonivell (proposta "Onda plana") para Android e PC.

    python3 -m pip install --require-hashes -r .github/ci/requisitos-icones.txt
    python3 tools/gen_app_icons.py

Desenho: uma única onda lima (#C6F24E) sobre grafite (#0E0F12), sem gradiente, brilho, sombra
nem vidro. O mesmo glifo da aba "Para você". O ícone é igual nas edições Lite e SMART, em todos os
estilos do app e não acompanha a cor de destaque escolhida pelo usuário.

Grade de referência: quadro de 96 x 96.
  - Onda: M20 48 C32 22, 44 22, 48 48 C52 74, 64 74, 76 48 (58% da largura, centrada).
  - Traço: 9 (9,4% do ícone), pontas redondas.
  - Ícone quadrado: cantos de 24 (25%).
  - Camadas adaptativas (108 dp): a onda é reduzida a 75% em volta do centro, para ocupar a
    mesma proporção dos 72 dp visíveis e caber no círculo seguro de 66 dp.

Saída (sobrescreve):
  app/src/{smart,lite}/res/mipmap-*/ic_launcher{,_round,_background,_foreground,_monochrome}.png
  app/src/main/ic_launcher-playstore.png (512 x 512, quadrado cheio: a Play aplica a máscara)
  desktop/packaging/sonivell.png e desktop/src/main/resources/icon.png
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
S = 2048  # tamanho de trabalho; tudo é reduzido no fim (antisserrilhado)

FUNDO = (0x0E, 0x0F, 0x12, 255)
ONDA_COR = (0xC6, 0xF2, 0x4E, 255)
BRANCO = (255, 255, 255, 255)

# Onda no quadro de 96: duas cúbicas, crista para cima e vale para baixo.
ONDA = [((20, 48), (32, 22), (44, 22), (48, 48)), ((48, 48), (52, 74), (64, 74), (76, 48))]
TRACO = 9


def onda(img, cor, k, ox=0.0, oy=0.0, escala=1.0):
    """Desenha a onda no quadro de 96, com fator k (px por unidade), deslocamento e escala no centro."""
    d = ImageDraw.Draw(img)
    raio = TRACO * escala * k / 2
    for p0, p1, p2, p3 in ONDA:
        for i in range(2401):
            t = i / 2400
            a, b, c, e = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t ** 2, t ** 3
            x = a * p0[0] + b * p1[0] + c * p2[0] + e * p3[0]
            y = a * p0[1] + b * p1[1] + c * p2[1] + e * p3[1]
            x, y = 48 + (x - 48) * escala, 48 + (y - 48) * escala
            px, py = ox + x * k, oy + y * k
            d.ellipse([px - raio, py - raio, px + raio, py + raio], fill=cor)


def quadrado(cantos=True, margem=0.0):
    """Ícone inteiro: fundo grafite com a onda. margem: fração vazia em volta (ícone do PC)."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    lado = S * (1 - 2 * margem)
    o = S * margem
    k = lado / 96
    d = ImageDraw.Draw(img)
    if cantos:
        d.rounded_rectangle([o, o, o + lado - 1, o + lado - 1], radius=24 * k, fill=FUNDO)
    else:
        d.rectangle([0, 0, S - 1, S - 1], fill=FUNDO)
    onda(img, ONDA_COR, k, o, o)
    return img


def camada_frente(cor):
    """Primeiro plano adaptativo (108 dp): onda a 75%, centrada no quadro de 108."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    k = S / 108
    onda(img, cor, k, 6 * k, 6 * k, escala=0.75)  # o quadro de 96 centrado no de 108
    return img


def redondo():
    """Ícone redondo: os 72 dp visíveis da camada adaptativa recortados em círculo."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, S - 1, S - 1], fill=FUNDO)
    k = S / 72
    onda(img, ONDA_COR, k, -12 * k, -12 * k, escala=0.75)  # quadro de 96 centrado nos 72 visíveis
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).ellipse([0, 0, S - 1, S - 1], fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), m)
    return out


def salvar(img, caminho, lado):
    caminho.parent.mkdir(parents=True, exist_ok=True)
    img.resize((lado, lado), Image.LANCZOS).save(caminho, optimize=True)
    print(f"{lado:>4} px  {caminho.relative_to(ROOT)}")


DENSIDADES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def main():
    icone = quadrado()
    circulo = redondo()
    fundo = Image.new("RGBA", (S, S), FUNDO)
    frente = camada_frente(ONDA_COR)
    mono = camada_frente(BRANCO)
    for edicao in ("smart", "lite"):
        res = ROOT / "app" / "src" / edicao / "res"
        for d, f in DENSIDADES.items():
            pasta = res / f"mipmap-{d}"
            salvar(icone, pasta / "ic_launcher.png", round(48 * f))
            salvar(circulo, pasta / "ic_launcher_round.png", round(48 * f))
            for nome, img in (("background", fundo), ("foreground", frente), ("monochrome", mono)):
                salvar(img, pasta / f"ic_launcher_{nome}.png", round(108 * f))
    salvar(quadrado(cantos=False), ROOT / "app" / "src" / "main" / "ic_launcher-playstore.png", 512)
    pc = quadrado(margem=0.025)
    salvar(pc, ROOT / "desktop" / "packaging" / "sonivell.png", 512)
    salvar(pc, ROOT / "desktop" / "src" / "main" / "resources" / "icon.png", 512)


if __name__ == "__main__":
    main()
