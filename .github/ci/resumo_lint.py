#!/usr/bin/env python3
"""Resume os relatórios XML do Android Lint (lite e smart) em Markdown.

Uso: resumo_lint.py <relatório.xml> [<relatório.xml> ...]
Achados iguais nas duas versões (mesmo id, arquivo e linha) contam uma vez.
"""
import collections
import os
import sys
import xml.etree.ElementTree as ET

ORDEM = ["Fatal", "Error", "Warning", "Information"]
RAIZ = os.environ.get("GITHUB_WORKSPACE", os.getcwd()).rstrip("/") + "/"
NOMES = {"Fatal": "fatal", "Error": "erro", "Warning": "aviso", "Information": "informação"}


def main():
    achados = {}
    lidos = 0
    for caminho in sys.argv[1:]:
        if not os.path.exists(caminho):
            continue
        lidos += 1
        for issue in ET.parse(caminho).getroot().iter("issue"):
            loc = issue.find("location")
            arquivo = loc.get("file", "") if loc is not None else ""
            if arquivo.startswith(RAIZ):
                arquivo = arquivo[len(RAIZ):]
            linha = loc.get("line", "") if loc is not None else ""
            chave = (issue.get("id"), arquivo, linha)
            achados.setdefault(chave, (issue.get("severity"), issue.get("message", "")))

    print("### Android Lint (lite e smart)\n")
    if not lidos:
        print(":x: Nenhum relatório do lint encontrado (a tarefa falhou antes de analisar).\n")
        return
    por_sev = collections.Counter(s for s, _ in achados.values())
    resumo = ", ".join(f"{por_sev[s]} {NOMES[s]}(s)" for s in ORDEM if por_sev[s]) or "nenhum achado"
    icone = ":x:" if por_sev["Fatal"] or por_sev["Error"] else (
        ":warning:" if por_sev["Warning"] else ":white_check_mark:")
    print(f"{icone} {resumo}.\n")

    graves = sorted((k, v) for k, v in achados.items() if v[0] in ("Fatal", "Error"))
    if graves:
        print("| Severidade | Regra | Local | Mensagem |\n|---|---|---|---|")
        for (rid, arq, lin), (sev, msg) in graves[:40]:
            msg = msg.replace("|", "\\|").replace("\n", " ")[:160]
            print(f"| {NOMES[sev]} | `{rid}` | `{arq}:{lin}` | {msg} |")
        print()

    avisos = collections.Counter(k[0] for k, v in achados.items() if v[0] == "Warning")
    if avisos:
        print("<details><summary>Avisos por regra</summary>\n")
        print("| Regra | Quantidade |\n|---|---|")
        for rid, n in avisos.most_common(40):
            print(f"| `{rid}` | {n} |")
        print("\n</details>\n")


if __name__ == "__main__":
    main()
