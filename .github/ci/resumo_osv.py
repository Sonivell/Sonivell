#!/usr/bin/env python3
"""Resume a saída JSON do OSV-Scanner em Markdown e aplica o corte de severidade.

Uso: resumo_osv.py <resultado.json> <título> [--falhar-a-partir-de 7.0]

Sai com 1 se alguma vulnerabilidade tiver CVSS >= o corte. Vulnerabilidades sem nota CVSS
entram na tabela como "sem nota" e não reprovam (precisam de leitura manual).
"""
import json
import sys


def nota(grupo):
    valor = grupo.get("max_severity") or ""
    try:
        return float(valor)
    except ValueError:
        return None


def versao_corrigida(vulns, ids, pacote):
    corrigidas = set()
    for v in vulns:
        if v.get("id") not in ids:
            continue
        for afetado in v.get("affected", []):
            if afetado.get("package", {}).get("name") != pacote:
                continue
            for faixa in afetado.get("ranges", []):
                for ev in faixa.get("events", []):
                    if "fixed" in ev:
                        corrigidas.add(ev["fixed"])
    return ", ".join(sorted(corrigidas)) or "—"


def main():
    args = sys.argv[1:]
    corte = None
    if "--falhar-a-partir-de" in args:
        i = args.index("--falhar-a-partir-de")
        corte = float(args[i + 1])
        del args[i:i + 2]
    caminho, titulo = args[0], args[1]

    try:
        with open(caminho, encoding="utf-8") as f:
            dados = json.load(f)
    except (OSError, ValueError) as e:
        print(f"### {titulo}\n\n:x: Sem resultado do OSV-Scanner ({e}).\n")
        return 1

    linhas, pacotes, reprovados, fontes = [], set(), 0, 0
    for resultado in dados.get("results") or []:
        fontes += 1
        for p in resultado.get("packages") or []:
            pkg = p.get("package", {})
            nome, versao = pkg.get("name", "?"), pkg.get("version", "?")
            pacotes.add((nome, versao))
            for g in p.get("groups") or []:
                ids = g.get("ids") or []
                aliases = g.get("aliases") or ids
                n = nota(g)
                if corte is not None and n is not None and n >= corte:
                    reprovados += 1
                cves = [a for a in aliases if a.startswith("CVE-")] or ids
                linhas.append((n if n is not None else -1, nome, versao, ", ".join(cves[:3]),
                               "sem nota" if n is None else f"{n:.1f}",
                               versao_corrigida(p.get("vulnerabilities") or [], set(ids), nome)))

    linhas.sort(reverse=True)
    saida = [f"### {titulo}", ""]
    if not linhas:
        saida.append(f":white_check_mark: Nenhuma vulnerabilidade conhecida "
                     f"({fontes} arquivo(s) de dependências lido(s)).")
    else:
        icone = ":x:" if reprovados else ":warning:"
        saida.append(f"{icone} {len(linhas)} vulnerabilidade(s) em {len(pacotes)} pacote(s)"
                     + (f"; **{reprovados} com CVSS ≥ {corte:g}**." if corte is not None else "."))
        saida += ["", "| CVSS | Pacote | Versão | IDs | Corrigida em |", "|---|---|---|---|---|"]
        for _, nome, versao, ids, n, fix in linhas[:60]:
            saida.append(f"| {n} | `{nome}` | {versao} | {ids} | {fix} |")
        if len(linhas) > 60:
            saida.append(f"\n… e mais {len(linhas) - 60} (veja o artefato do job).")
    print("\n".join(saida) + "\n")
    return 1 if reprovados else 0


if __name__ == "__main__":
    sys.exit(main())
