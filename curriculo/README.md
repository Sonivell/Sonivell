# Currículo (nome provisório)

PWA para criar currículos **100% offline**, sem conta e sem servidor. Os dados ficam só no
aparelho (IndexedDB). Código aberto (MIT), sem nenhuma dependência de terceiros: tudo — o
gerador de PDF, o gerador de Word, o ZIP, a análise ATS — foi escrito aqui. As fontes são
livres (SIL OFL) e vêm junto.

## O que faz

**Editor** organizado como os bons criadores de currículo:

- Dados pessoais com foto (recortada e reduzida no aparelho), vaga desejada com a opção
  *usar como título*, e campos opcionais em um toque: data de nascimento, naturalidade,
  CNH, gênero, nacionalidade, estado civil, site, LinkedIn e campos personalizados.
- Seções arrastáveis (mouse ou toque) e recolhíveis: Formação, Experiência, Competências,
  Idiomas, Interesses — e para adicionar: Resumo profissional, Cursos, Estágios, Atividades
  extracurriculares, Referências, Qualidades, Certificados, Realizações, Assinatura
  (inclusive desenhada), Rodapé, Seção personalizada e Lista personalizada.
- Itens reordenáveis, renomear/ocultar seções, descrições com **negrito**, *itálico* e
  tópicos (`- item`).
- Vários currículos, duplicar, renomear, backup de tudo num arquivo.

**Visualização** ao vivo (lado a lado no computador; botão flutuante no celular) com
marcação de quebra de página e barra de aparência:

- 10 modelos: Essencial, Cronológico, Executivo, Clássico, Tabela (uma coluna, marcados
  como **ATS**), Coluna, Contraste, Faixa, Elegante e Vertical (duas colunas).
- Fonte (Inter, Manrope, Carlito, Caladea) e tamanho; altura das linhas e espaço entre
  seções; estilo dos níveis (barra, pontos, texto ou oculto); formato da foto; cor.
- Idioma do currículo (títulos e datas): português, inglês ou espanhol.

**Exportação** (tudo gerado no aparelho):

| Formato | Para quê |
|---|---|
| PDF (visual) | Igual à visualização. Usa a impressão do navegador → "Salvar como PDF". |
| PDF para ATS | Gerador próprio: uma coluna, texto real em ordem de leitura, links clicáveis, sem foto. |
| Word (.docx) | OOXML escrito à mão, com estilos de título e listas reais. |
| Texto (.txt) | Para colar em formulários de vaga (também há "Copiar texto"). |
| Markdown, HTML | Sites, GitHub, notas; HTML em arquivo único. |
| JSON Resume | Padrão aberto ([jsonresume.org](https://jsonresume.org)). |
| Backup (.json) | Tudo, para restaurar em outro aparelho. |

**Importação**: backup do app, JSON Resume e o ZIP de dados do LinkedIn
(Configurações → Privacidade de dados → *Obter uma cópia dos seus dados*) — lido no
próprio navegador.

**Análise ATS**: nota de 0 a 100 com regras transparentes (contato, resumo, datas, tópicos,
resultados com números, tamanho, modelo de uma coluna…) e comparação de palavras-chave com
o texto de uma vaga.

## Rodar

Não tem build. Sirva a pasta com qualquer servidor estático:

```sh
cd curriculo
python3 -m http.server 8080
# abra http://localhost:8080 e use "Instalar app" no navegador
```

Para publicar, basta copiar a pasta para qualquer hospedagem estática com HTTPS (GitHub
Pages, Codeberg Pages, Netlify…). Depois da primeira visita, funciona sem internet.

Ao alterar arquivos, aumente `VERSION` em `sw.js` para os aparelhos receberem a atualização.

## Estrutura

```
index.html            casca do app
css/app.css           interface
css/resume.css        os modelos de currículo (tela e impressão)
js/app.js             telas, editor, visualização, exportação
js/model.js           tipos de seção e campos
js/doc.js             currículo → documento neutro (usado por todos os formatos)
js/render.js          documento → HTML nos modelos
js/i18n.js            textos do currículo em pt/en/es
js/ats.js             análise ATS e palavras-chave
js/import.js          importação (backup, JSON Resume, ZIP do LinkedIn)
js/store.js           IndexedDB
js/ui.js              utilitários de interface (arrastar, diálogos, ícones)
js/export/pdf.js      gerador de PDF (Helvetica padrão, WinAnsi)
js/export/metrics.js  larguras das fontes padrão do PDF (gerado por tools/gen-metrics.py)
js/export/docx.js     gerador de .docx
js/export/zip.js      ZIP sem compressão + CRC32
js/export/text.js     TXT, Markdown, HTML e JSON Resume
sw.js                 cache offline
tools/                scripts de geração (métricas, ícones)
```

## Para renomear o projeto

Troque o nome em `js/app.js` (`APP_NAME`), `index.html` (`<title>` e marca),
`manifest.webmanifest` (`name`, `short_name`) e neste README.

## Licenças

- Código: MIT (veja `LICENSE`).
- Fontes em `fonts/` (subconjunto latino, WOFF2): Inter, Manrope, Carlito e Caladea, todas
  sob a SIL Open Font License 1.1 — os textos estão na mesma pasta.
- Métricas das fontes padrão do PDF: derivadas dos arquivos AFM da Adobe, de livre
  distribuição.
