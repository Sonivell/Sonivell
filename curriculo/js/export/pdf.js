// Gerador de PDF próprio, sem dependências: monta um PDF de texto real, em uma
// coluna, com as fontes padrão do PDF (Helvetica). É o formato mais seguro para
// sistemas de triagem (ATS): o texto é selecionável e lido na ordem certa.

import { HELVETICA, HELVETICA_BOLD, HELVETICA_OBLIQUE } from './metrics.js';
import { plain } from '../doc.js';

const PAGE_W = 595.28, PAGE_H = 841.89; // A4 em pontos
const MARGIN_X = 50, MARGIN_TOP = 48, MARGIN_BOTTOM = 50;
const CONTENT_W = PAGE_W - 2 * MARGIN_X;

const FONTS = {
  R: { res: 'F1', base: 'Helvetica', widths: HELVETICA },
  B: { res: 'F2', base: 'Helvetica-Bold', widths: HELVETICA_BOLD },
  I: { res: 'F3', base: 'Helvetica-Oblique', widths: HELVETICA_OBLIQUE },
};

// Unicode → byte da WinAnsiEncoding.
const WIN_ANSI_EXTRA = {
  0x20ac: 0x80, 0x201a: 0x82, 0x0192: 0x83, 0x201e: 0x84, 0x2026: 0x85, 0x2020: 0x86, 0x2021: 0x87,
  0x02c6: 0x88, 0x2030: 0x89, 0x0160: 0x8a, 0x2039: 0x8b, 0x0152: 0x8c, 0x017d: 0x8e, 0x2018: 0x91,
  0x2019: 0x92, 0x201c: 0x93, 0x201d: 0x94, 0x2022: 0x95, 0x2013: 0x96, 0x2014: 0x97, 0x02dc: 0x98,
  0x2122: 0x99, 0x0161: 0x9a, 0x203a: 0x9b, 0x0153: 0x9c, 0x017e: 0x9e, 0x0178: 0x9f,
};

function toWinAnsi(text) {
  let out = '';
  for (const ch of String(text).normalize('NFC')) {
    const cp = ch.codePointAt(0);
    if (cp === 0x09 || cp === 0x0a) { out += ' '; continue; }
    if ((cp >= 0x20 && cp < 0x7f) || (cp >= 0xa0 && cp <= 0xff)) { out += ch; continue; }
    if (WIN_ANSI_EXTRA[cp]) { out += String.fromCharCode(WIN_ANSI_EXTRA[cp]); continue; }
    if (cp === 0x2212) { out += '-'; continue; }
    // Tenta sem acento (ex.: "ő" → "o"); senão, "?"
    const base = ch.normalize('NFD').replace(/[̀-ͯ]/g, '');
    out += base && base.codePointAt(0) < 0x7f ? base : '?';
  }
  return out;
}

const widthOf = (bytes, font, size) => {
  const w = FONTS[font].widths;
  let sum = 0;
  for (let i = 0; i < bytes.length; i++) sum += w[bytes.charCodeAt(i)] || 500;
  return (sum * size) / 1000;
};

const escapePdf = s => s.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');

function hexToRgb(hex) {
  const m = /^#?([\da-f]{2})([\da-f]{2})([\da-f]{2})$/i.exec(hex || '');
  if (!m) return [0.18, 0.36, 0.54];
  return [1, 2, 3].map(i => parseInt(m[i], 16) / 255);
}
const rgb = c => c.map(v => v.toFixed(3)).join(' ');

const INK = [0.13, 0.14, 0.16], MUTED = [0.38, 0.4, 0.44], RULE = [0.8, 0.82, 0.85];

class Writer {
  constructor(accent) {
    this.accent = accent;
    this.pages = [];
    this.newPage();
  }

  newPage() {
    this.page = { ops: [], links: [] };
    this.pages.push(this.page);
    this.y = PAGE_H - MARGIN_TOP;
  }

  ensure(h) { if (this.y - h < MARGIN_BOTTOM) this.newPage(); }

  text(x, y, bytes, font, size, color) {
    this.page.ops.push(`BT /${FONTS[font].res} ${size} Tf ${rgb(color)} rg ${x.toFixed(2)} ${y.toFixed(2)} Td (${escapePdf(bytes)}) Tj ET`);
  }

  rule(y, color = RULE, width = 0.6) {
    this.page.ops.push(`${rgb(color)} RG ${width} w ${MARGIN_X} ${y.toFixed(2)} m ${(PAGE_W - MARGIN_X).toFixed(2)} ${y.toFixed(2)} l S`);
  }

  link(x, y, w, h, uri) { this.page.links.push({ rect: [x, y, x + w, y + h], uri }); }

  // Quebra uma lista de trechos [{text, font}] em linhas que cabem em maxW.
  wrap(runs, size, maxW) {
    const words = [];
    for (const r of runs) {
      for (const part of toWinAnsi(r.text).split(/( +)/)) {
        if (part) words.push({ text: part, font: r.font, space: /^ +$/.test(part) });
      }
    }
    const lines = [];
    let line = [], w = 0;
    for (const word of words) {
      const ww = widthOf(word.text, word.font, size);
      if (word.space) { if (line.length) { line.push(word); w += ww; } continue; }
      if (w + ww > maxW && line.length) {
        while (line.length && line[line.length - 1].space) line.pop();
        lines.push(line); line = []; w = 0;
      }
      // Palavra maior que a linha inteira: corta por caractere.
      if (ww > maxW) {
        let chunk = '';
        for (const ch of word.text) {
          if (widthOf(chunk + ch, word.font, size) > maxW) { lines.push([{ ...word, text: chunk }]); chunk = ''; }
          chunk += ch;
        }
        line = [{ ...word, text: chunk }]; w = widthOf(chunk, word.font, size);
        continue;
      }
      line.push(word); w += ww;
    }
    while (line.length && line[line.length - 1].space) line.pop();
    if (line.length) lines.push(line);
    return lines;
  }

  // Escreve um parágrafo; devolve nada, avança this.y.
  paragraph(runs, { size = 9.5, x = MARGIN_X, maxW = CONTENT_W, color = INK, lead = 1.38, bullet = false } = {}) {
    const lh = size * lead;
    const lines = this.wrap(runs, size, maxW);
    lines.forEach((line, i) => {
      this.ensure(lh);
      this.y -= lh;
      if (bullet && i === 0) this.text(x - 10, this.y + size * 0.12, '\x95', 'R', size, this.accent);
      let cx = x;
      for (const seg of line) {
        if (!seg.space) this.text(cx, this.y + size * 0.12, seg.text, seg.font, size, seg.color || color);
        cx += widthOf(seg.text, seg.font, size);
      }
    });
  }

  blocks(blocks, size = 9.5) {
    for (const b of blocks) {
      if (b.type === 'p') {
        this.paragraph(toRuns(b.runs), { size });
        this.y -= 1.5;
      } else {
        for (const item of b.items) this.paragraph(toRuns(item), { size, x: MARGIN_X + 14, maxW: CONTENT_W - 14, bullet: true });
        this.y -= 1.5;
      }
    }
  }
}

const toRuns = runs => runs.map(r => ({ text: r.text, font: r.bold ? 'B' : r.italic ? 'I' : 'R' }));

function heading(w, title) {
  w.ensure(46);
  w.y -= 16;
  const bytes = toWinAnsi(title.toLocaleUpperCase());
  w.y -= 11;
  w.text(MARGIN_X, w.y, bytes, 'B', 10.5, w.accent);
  w.y -= 4;
  w.rule(w.y, w.accent, 0.7);
  w.y -= 2;
}

function entry(w, it) {
  w.ensure(40);
  const size = 10;
  const dates = toWinAnsi(it.dates || '');
  const datesW = dates ? widthOf(dates, 'R', 9) : 0;
  w.y -= 6;
  // Título (pode quebrar) com as datas alinhadas à direita na primeira linha.
  const lines = w.wrap([{ text: it.title || it.org || '', font: 'B' }], size, CONTENT_W - datesW - 12);
  lines.forEach((line, i) => {
    w.y -= size * 1.3;
    w.text(MARGIN_X, w.y, line.map(s => s.text).join(''), 'B', size, INK);
    if (i === 0 && dates) w.text(PAGE_W - MARGIN_X - datesW, w.y, dates, 'R', 9, MUTED);
  });
  if (!lines.length && dates) { w.y -= size * 1.3; w.text(PAGE_W - MARGIN_X - datesW, w.y, dates, 'R', 9, MUTED); }
  const meta = [it.title ? it.org : '', it.city].filter(Boolean).join(', ');
  if (meta) w.paragraph([{ text: meta, font: 'I' }], { size: 9.5, color: MUTED });
  if (it.blocks.length) { w.y -= 2; w.blocks(it.blocks); }
}

export function buildPdf(doc, { title = 'Currículo', author = '' } = {}) {
  const accent = hexToRgb(doc.style.color);
  const w = new Writer(accent);

  // Cabeçalho
  if (doc.name) {
    w.y -= 20;
    w.paragraph([{ text: doc.name, font: 'B' }], { size: 20, lead: 1.15 });
  }
  const role = doc.title !== doc.L.resume ? doc.title : doc.role;
  if (role) { w.y -= 2; w.paragraph([{ text: role, font: 'R' }], { size: 11.5, color: accent }); }

  if (doc.contact.length) {
    w.y -= 4;
    const size = 9, lh = size * 1.45, sep = toWinAnsi('  |  ');
    const sepW = widthOf(sep, 'R', size);
    let x = MARGIN_X;
    w.y -= lh;
    doc.contact.forEach((c, i) => {
      const labelled = !['email', 'phone', 'address', 'website', 'linkedin'].includes(c.key);
      const bytes = toWinAnsi(labelled && c.label ? `${c.label}: ${c.value}` : c.value);
      const cw = widthOf(bytes, 'R', size);
      if (i > 0) {
        if (x + sepW + cw > PAGE_W - MARGIN_X) { x = MARGIN_X; w.y -= lh; }
        else { w.text(x, w.y, sep, 'R', size, RULE); x += sepW; }
      }
      w.text(x, w.y, bytes, 'R', size, MUTED);
      if (c.href) w.link(x, w.y - 2, cw, size + 3, c.href);
      x += cw;
    });
  }

  for (const s of doc.sections) {
    if (s.type === 'footer') continue;
    if (s.title) heading(w, s.title);
    else w.y -= 10;

    if (s.kind === 'text') {
      w.y -= 4;
      w.blocks(s.blocks);
    } else if (s.kind === 'entries') {
      s.items.forEach(it => entry(w, it));
    } else if (s.kind === 'levels' || s.kind === 'tags') {
      w.y -= 4;
      const runs = [];
      s.items.forEach((it, i) => {
        if (i) runs.push({ text: '  •  ', font: 'R' });
        runs.push({ text: it.name, font: 'R' });
        if (it.level && doc.style.levelStyle !== 'none') runs.push({ text: ` (${it.levelText})`, font: 'R' });
      });
      w.paragraph(runs);
    } else if (s.kind === 'references') {
      for (const r of s.items) {
        w.ensure(40);
        w.y -= 6;
        w.paragraph([{ text: r.title, font: 'B' }], { size: 10 });
        const meta = [r.org, r.city].filter(Boolean).join(', ');
        if (meta) w.paragraph([{ text: meta, font: 'I' }], { color: MUTED });
        const contact = [r.phone, r.email].filter(Boolean).join('  |  ');
        if (contact) w.paragraph([{ text: contact, font: 'R' }]);
      }
    } else if (s.kind === 'signature') {
      w.ensure(60);
      w.y -= 6;
      if (s.place) w.paragraph([{ text: s.place, font: 'R' }]);
      w.y -= 26;
      w.page.ops.push(`${rgb(INK)} RG 0.6 w ${MARGIN_X} ${w.y.toFixed(2)} m ${MARGIN_X + 170} ${w.y.toFixed(2)} l S`);
      if (s.name) w.paragraph([{ text: s.name, font: 'B' }]);
    }
  }

  for (const f of doc.sections.filter(s => s.type === 'footer')) {
    w.y -= 14;
    for (const b of f.blocks) {
      const runs = b.type === 'p' ? [b.runs] : b.items;
      for (const r of runs) w.paragraph([{ text: plain(r), font: 'R' }], { size: 8.5, color: MUTED });
    }
  }

  return serialize(w.pages, { title, author, lang: doc.lang });
}

// Monta o arquivo PDF (objetos, tabela xref e trailer).
function serialize(pages, meta) {
  const objs = []; // índice i → objeto i+1
  const add = body => { objs.push(body); return objs.length; };
  const ref = n => `${n} 0 R`;

  const catalog = add(null);
  const pagesObj = add(null);
  const fontRefs = Object.values(FONTS).map(f =>
    `/${f.res} ${ref(add(`<< /Type /Font /Subtype /Type1 /BaseFont /${f.base} /Encoding /WinAnsiEncoding >>`))}`);

  const pageRefs = [];
  for (const p of pages) {
    const stream = p.ops.join('\n');
    const content = add(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
    const annots = p.links.map(l => ref(add(
      `<< /Type /Annot /Subtype /Link /Rect [${l.rect.map(v => v.toFixed(2)).join(' ')}] /Border [0 0 0] /A << /S /URI /URI (${escapePdf(toWinAnsi(l.uri))}) >> >>`)));
    pageRefs.push(ref(add(`<< /Type /Page /Parent ${ref(pagesObj)} /MediaBox [0 0 ${PAGE_W} ${PAGE_H}] `
      + `/Resources << /Font << ${fontRefs.join(' ')} >> >> /Contents ${ref(content)}`
      + `${annots.length ? ` /Annots [${annots.join(' ')}]` : ''} >>`)));
  }
  objs[pagesObj - 1] = `<< /Type /Pages /Kids [${pageRefs.join(' ')}] /Count ${pageRefs.length} >>`;
  objs[catalog - 1] = `<< /Type /Catalog /Pages ${ref(pagesObj)} /Lang (${meta.lang || 'pt'}) >>`;

  const d = new Date();
  const pad = n => String(n).padStart(2, '0');
  const date = `D:${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
  const info = add(`<< /Title (${escapePdf(toWinAnsi(meta.title))}) /Author (${escapePdf(toWinAnsi(meta.author))}) `
    + `/Creator (Curriculo PWA) /Producer (Curriculo PWA) /CreationDate (${date}) >>`);

  let out = '%PDF-1.4\n%\xe2\xe3\xcf\xd3\n';
  const offsets = [];
  objs.forEach((body, i) => {
    offsets.push(out.length);
    out += `${i + 1} 0 obj\n${body}\nendobj\n`;
  });
  const xref = out.length;
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n`;
  for (const o of offsets) out += `${String(o).padStart(10, '0')} 00000 n \n`;
  out += `trailer\n<< /Size ${objs.length + 1} /Root ${ref(catalog)} /Info ${ref(info)} >>\nstartxref\n${xref}\n%%EOF\n`;

  const bytes = new Uint8Array(out.length);
  for (let i = 0; i < out.length; i++) bytes[i] = out.charCodeAt(i) & 0xff;
  return new Blob([bytes], { type: 'application/pdf' });
}
