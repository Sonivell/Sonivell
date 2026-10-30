"""Gera js/export/metrics.js com as larguras das fontes padrão do PDF.

Uso: python3 tools/gen-metrics.py  (requer reportlab, só para gerar o arquivo)
"""
from pathlib import Path
from reportlab.pdfbase import pdfmetrics

out = ["// Larguras (1/1000 em) das fontes padrão do PDF em WinAnsiEncoding, índice = byte.",
       "// Gerado por tools/gen-metrics.py a partir das métricas AFM da Adobe (livre distribuição).",
       ""]
for js, font in [("HELVETICA", "Helvetica"), ("HELVETICA_BOLD", "Helvetica-Bold"),
                 ("HELVETICA_OBLIQUE", "Helvetica-Oblique")]:
    widths = [int(x) for x in pdfmetrics.getFont(font).widths]
    out.append(f"export const {js} = [{','.join(map(str, widths))}];")

target = Path(__file__).resolve().parent.parent / "js" / "export" / "metrics.js"
target.write_text("\n".join(out) + "\n")
