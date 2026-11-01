#!/usr/bin/env python3
"""Gera os ícones do Sonivell (proposta "Onda", cartões de vidro) para Android e PC.

    python3 -m pip install --require-hashes -r .github/ci/requisitos-icones.txt
    python3 tools/gen_app_icons.py

Variantes:
  - Grafite (escura): edição SMART do Android e o Sonivell para PC.
  - Verde (clara):    edição Lite do Android.

Saída (sobrescreve):
  app/src/{smart,lite}/res/mipmap-*/ic_launcher{,_round,_background,_foreground,_monochrome}.png
  desktop/packaging/sonivell.png e desktop/src/main/resources/icon.png

Desenho de referência: 512 x 512, cantos de 116. Cartão de trás 236 x 260 em (138, 96), raio 64;
cartão da frente 288 x 288 em (112, 128), raio 76; o conjunto fica centralizado no ícone.
O vidro (desfoque do que está atrás, tinta translúcida, borda e reflexo) é calculado aqui, porque
o Android e o Linux não desenham desfoque em ícones.
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
S = 2048  # tamanho de trabalho; tudo é reduzido no fim (antisserrilhado)

VARIANTES = {
    "grafite": dict(
        fundo=[(0, "#23272A"), (0.5, "#15181A"), (1, "#0B0D0E")], borda_icone=(255, 255, 255, 0.08),
        brilho=(205, 235, 79, 0.10), tras=(205, 235, 79, 0.70), tras_borda=(235, 250, 170, 0.55),
        vidro=(255, 255, 255, 0.10), vidro_borda=(255, 255, 255, 0.32), sombra=(0, 0, 0, 0.50),
        tinta="#D4EF55"),
    "verde": dict(
        fundo=[(0, "#DDF56A"), (0.45, "#C6E84A"), (1, "#9CCB2C")], borda_icone=(255, 255, 255, 0.25),
        brilho=(255, 255, 255, 0.35), tras=(28, 48, 6, 0.22), tras_borda=(255, 255, 255, 0.40),
        vidro=(255, 255, 255, 0.30), vidro_borda=(255, 255, 255, 0.70), sombra=(40, 66, 0, 0.30),
        tinta="#11140D"),
}

# Onda: curva no quadro 200 x 200, colocada no centro do cartão da frente (156, 172).
ONDA = [((26, 106), (46, 50), (78, 50), (100, 100)), ((100, 100), (122, 150), (154, 150), (174, 94))]
ONDA_X, ONDA_Y, ONDA_TRACO = 156, 172, 28


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Grade:
    """Converte unidades do desenho (512) em pixels do quadro de trabalho."""

    def __init__(self, k, ox, oy):
        self.k, self.ox, self.oy = k, ox, oy

    def p(self, x, y):
        return (self.ox + x * self.k, self.oy + y * self.k)

    def l(self, v):
        return v * self.k


def mascara(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def cor(rgba, m):
    r, g, b, a = rgba
    camada = Image.new("RGBA", (S, S), (r, g, b, 0))
    camada.putalpha(mascara(np.asarray(m, np.float32) * a))
    return camada


def degrade(paradas, g):
    ang = math.radians(160)  # como o linear-gradient(160deg) do mockup
    dx, dy = math.sin(ang), -math.cos(ang)
    comp = abs(512 * dx) + abs(512 * dy)
    ys, xs = np.mgrid[0:S, 0:S].astype(np.float32)
    ux, uy = (xs - g.ox) / g.k - 256, (ys - g.oy) / g.k - 256
    t = np.clip((ux * dx + uy * dy) / comp + 0.5, 0, 1)
    out = np.zeros((S, S, 3), np.float32)
    cs = [(p, np.array(rgb(c), np.float32)) for p, c in paradas]
    for (p0, c0), (p1, c1) in zip(cs, cs[1:]):
        m = (t >= p0) & (t <= p1)
        f = ((t - p0) / (p1 - p0))[..., None]
        out[m] = (c0 * (1 - f) + c1 * f)[m]
    return Image.fromarray(out.astype(np.uint8), "RGB").convert("RGBA")


def retangulo(g, x, y, w, h, r):
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).rounded_rectangle([g.p(x, y), g.p(x + w, y + h)], radius=g.l(r), fill=255)
    return m


def contorno(g, x, y, w, h, r, t):
    fora = np.asarray(retangulo(g, x, y, w, h, r), np.float32)
    dentro = np.asarray(retangulo(g, x + t, y + t, w - 2 * t, h - 2 * t, r - t), np.float32)
    return mascara(fora - dentro)


def deslocar(m, g, dx, dy):
    out = Image.new("L", (S, S), 0)
    out.paste(m, (round(g.l(dx)), round(g.l(dy))))
    return out


def sombra(m, g, dy, desfoque, rgba):
    return cor(rgba, deslocar(m, g, 0, dy).filter(ImageFilter.GaussianBlur(g.l(desfoque) / 2)))


def vidro(base, m, g, desfoque, saturacao, tinta):
    atras = base.filter(ImageFilter.GaussianBlur(g.l(desfoque)))
    if saturacao != 1:
        atras = ImageEnhance.Color(atras.convert("RGB")).enhance(saturacao).convert("RGBA")
    atras = Image.alpha_composite(atras, cor(tinta, Image.new("L", (S, S), 255)))
    out = base.copy()
    out.paste(atras, (0, 0), m)
    return out


def onda(g, escala=1.0):
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    raio = g.l(ONDA_TRACO * escala) / 2
    for p0, p1, p2, p3 in ONDA:
        for i in range(1601):
            t = i / 1600
            a, b, c, e = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t ** 2, t ** 3
            x = a * p0[0] + b * p1[0] + c * p2[0] + e * p3[0]
            y = a * p0[1] + b * p1[1] + c * p2[1] + e * p3[1]
            px, py = g.p(ONDA_X + x, ONDA_Y + y)
            d.ellipse([px - raio, py - raio, px + raio, py + raio], fill=255)
    return m


def desenhar(v, modo):
    """modo: 'icone' (quadrado de cantos arredondados), 'pc' (com margem) ou 'adaptativo' (fundo, frente)."""
    c = VARIANTES[v]
    if modo == "adaptativo":  # camadas de 108 dp: o ícone inteiro ocupa os 72 dp visíveis
        g = Grade(S / 108 * 72 / 512, S / 108 * 18, S / 108 * 18)
    elif modo == "pc":  # margem de 2,5% em volta, como o ícone anterior do PC
        k = S / 512 * 0.95
        g = Grade(k, S * 0.025, S * 0.025)
    else:
        g = Grade(S / 512, 0, 0)

    fundo = degrade(c["fundo"], g)
    luz = Image.new("L", (S, S), 0)
    ImageDraw.Draw(luz).ellipse([g.p(-80, -140), g.p(592, 220)], fill=255)
    fundo = Image.alpha_composite(fundo, cor(c["brilho"], luz.filter(ImageFilter.GaussianBlur(g.l(40)))))

    img = fundo.copy()
    sombras = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    tras = retangulo(g, 138, 96, 236, 260, 64)
    s = sombra(tras, g, 18, 36, c["sombra"])
    img, sombras = Image.alpha_composite(img, s), Image.alpha_composite(sombras, s)
    img = vidro(img, tras, g, 16, 1.0, c["tras"])
    img = Image.alpha_composite(img, cor(c["tras_borda"], contorno(g, 138, 96, 236, 260, 64, 1.5)))

    frente = retangulo(g, 112, 128, 288, 288, 76)
    for dy, desf in ((2, 4), (28, 56)):
        s = sombra(frente, g, dy, desf, c["sombra"])
        img, sombras = Image.alpha_composite(img, s), Image.alpha_composite(sombras, s)
    img = vidro(img, frente, g, 28, 1.8, c["vidro"])
    ys = np.arange(S, dtype=np.float32)
    y0, y1 = g.p(0, 128)[1], g.p(0, 128 + 144)[1]
    reflexo = np.clip(1 - (ys - y0) / (y1 - y0), 0, 1) * (ys >= y0)
    m = np.asarray(frente, np.float32) / 255 * reflexo[:, None] * 0.30 * 255
    img = Image.alpha_composite(img, cor((255, 255, 255, 1), mascara(m)))
    img = Image.alpha_composite(img, cor(c["vidro_borda"], contorno(g, 112, 128, 288, 288, 76, 1.5)))
    aresta = np.asarray(frente, np.float32) - np.asarray(deslocar(frente, g, 0, 2), np.float32)
    img = Image.alpha_composite(img, cor((255, 255, 255, 0.75), mascara(aresta)))
    img = Image.alpha_composite(img, cor(rgb(c["tinta"]) + (1,), onda(g)))

    if modo == "adaptativo":
        cartoes = np.maximum(np.asarray(tras, np.float32), np.asarray(frente, np.float32))
        frente_img = img.copy()
        frente_img.putalpha(mascara(cartoes))
        return fundo, Image.alpha_composite(sombras, frente_img)

    img = Image.alpha_composite(img, cor(c["borda_icone"], contorno(g, 0, 0, 512, 512, 116, 1)))
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), retangulo(g, 0, 0, 512, 512, 116))
    return out


def monocromatico():
    """Ícone temático (Android 13+): só a onda, branca, na grade de 108 dp."""
    g = Grade(S / 108 * 72 / 512, S / 108 * 18, S / 108 * 18)
    return cor((255, 255, 255, 1), onda(g))


def redondo(fundo, frente):
    cheio = Image.alpha_composite(fundo, frente)
    a, b = round(S * 18 / 108), round(S * 90 / 108)
    cheio = cheio.crop((a, a, b, b)).resize((S, S), Image.LANCZOS)
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).ellipse([0, 0, S - 1, S - 1], fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(cheio, (0, 0), m)
    return out


def salvar(img, caminho, lado):
    caminho.parent.mkdir(parents=True, exist_ok=True)
    img.resize((lado, lado), Image.LANCZOS).save(caminho, optimize=True)
    print(f"{lado:>4} px  {caminho.relative_to(ROOT)}")


DENSIDADES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def main():
    mono = monocromatico()
    for v, edicao in (("grafite", "smart"), ("verde", "lite")):
        icone = desenhar(v, "icone")
        fundo, frente = desenhar(v, "adaptativo")
        circulo = redondo(fundo, frente)
        res = ROOT / "app" / "src" / edicao / "res"
        for d, f in DENSIDADES.items():
            pasta = res / f"mipmap-{d}"
            salvar(icone, pasta / "ic_launcher.png", round(48 * f))
            salvar(circulo, pasta / "ic_launcher_round.png", round(48 * f))
            for nome, img in (("background", fundo), ("foreground", frente), ("monochrome", mono)):
                salvar(img, pasta / f"ic_launcher_{nome}.png", round(108 * f))
    pc = desenhar("grafite", "pc")
    salvar(pc, ROOT / "desktop" / "packaging" / "sonivell.png", 512)
    salvar(pc, ROOT / "desktop" / "src" / "main" / "resources" / "icon.png", 512)


if __name__ == "__main__":
    main()
