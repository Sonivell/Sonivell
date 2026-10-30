// Converte o currículo editável num "documento" neutro, já com textos traduzidos,
// datas formatadas e itens vazios removidos. Todos os renderizadores (HTML, PDF,
// DOCX, texto) partem daqui, então o conteúdo é sempre o mesmo em todo formato.

import { SECTION_TYPES } from './model.js';
import { t } from './i18n.js';

// Seções que vão para a coluna lateral nos modelos de duas colunas.
export const SIDE_TYPES = new Set(['skills', 'languages', 'hobbies', 'qualities', 'customList']);

// "**negrito**" e "*itálico*" → [{text, bold, italic}]
export function parseInline(text) {
  const runs = [];
  const re = /(\*\*([^*]+)\*\*|\*([^*]+)\*)/g;
  let last = 0, m;
  while ((m = re.exec(text))) {
    if (m.index > last) runs.push({ text: text.slice(last, m.index) });
    if (m[2] !== undefined) runs.push({ text: m[2], bold: true });
    else runs.push({ text: m[3], italic: true });
    last = re.lastIndex;
  }
  if (last < text.length) runs.push({ text: text.slice(last) });
  return runs;
}

// Texto com linhas "- item" / "• item" → blocos [{type:'p', runs}] | [{type:'ul', items:[runs]}]
export function parseRich(text = '') {
  const blocks = [];
  for (const raw of String(text).split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    const bullet = line.match(/^([-•*·]|\d+[.)])\s+(.*)$/);
    if (bullet && !line.startsWith('**')) {
      const prev = blocks[blocks.length - 1];
      const runs = parseInline(bullet[2]);
      if (prev && prev.type === 'ul') prev.items.push(runs);
      else blocks.push({ type: 'ul', items: [runs] });
    } else {
      blocks.push({ type: 'p', runs: parseInline(line) });
    }
  }
  return blocks;
}

export const plain = runs => runs.map(r => r.text).join('');

export function formatMonth(value, L) {
  if (!value) return '';
  const [y, m] = String(value).split('-');
  if (!m) return y;
  return `${L.months[Number(m) - 1] || ''} ${y}`.trim();
}

export function formatRange(item, L) {
  const start = formatMonth(item.start, L);
  const end = item.current ? L.present : formatMonth(item.end, L);
  if (start && end) return `${start} – ${end}`;
  return start || end;
}

const filled = v => v != null && String(v).trim() !== '';

export function buildDoc(resume) {
  const st = resume.style;
  const L = t(st.lang);
  const p = resume.personal;
  const name = [p.firstName, p.lastName].filter(filled).join(' ').trim();

  const contact = [];
  const add = (key, value, label, href) => {
    if (filled(value)) contact.push({ key, label: label || L.fields[key] || key, value: String(value).trim(), href });
  };
  add('email', p.email, null, p.email ? `mailto:${p.email.trim()}` : undefined);
  add('phone', p.phone, null, p.phone ? `tel:${p.phone.replace(/[^\d+]/g, '')}` : undefined);
  const place = [p.postalCode, p.city].filter(filled).join(' ');
  add('address', [p.address, place].filter(filled).join(', '));
  for (const e of p.extras || []) {
    const label = e.key === 'custom' ? (e.label || '') : null;
    const href = (e.key === 'website' || e.key === 'linkedin') && filled(e.value)
      ? (/^https?:\/\//.test(e.value) ? e.value : `https://${e.value.trim()}`) : undefined;
    const value = e.key === 'birthDate' && /^\d{4}-\d{2}-\d{2}$/.test(e.value || '')
      ? e.value.split('-').reverse().join('/') : e.value;
    add(e.key, value, label, href);
  }

  const sections = [];
  for (const s of resume.sections) {
    if (s.hidden) continue;
    const def = SECTION_TYPES[s.type];
    const title = filled(s.title) ? s.title.trim() : L.sections[s.type];
    const base = { id: s.id, type: s.type, kind: def.kind, title, side: SIDE_TYPES.has(s.type) };

    if (def.kind === 'text') {
      const blocks = parseRich(s.content);
      if (blocks.length) sections.push({ ...base, blocks });
    } else if (def.kind === 'signature') {
      if ([s.city, s.date, s.name, s.image].some(filled)) {
        sections.push({ ...base, place: [s.city, s.date].filter(filled).join(', '), name: s.name || '', image: s.image || '' });
      }
    } else if (def.kind === 'levels') {
      const names = L.levels[def.levels];
      const items = s.items.filter(i => filled(i.name)).map(i => ({
        name: i.name.trim(), level: Number(i.level) || 0, levelText: names[Number(i.level) || 0] || '',
      }));
      if (items.length) sections.push({ ...base, items });
    } else if (def.kind === 'tags') {
      const items = s.items.filter(i => filled(i.name)).map(i => ({ name: i.name.trim() }));
      if (items.length) sections.push({ ...base, items });
    } else if (def.kind === 'references') {
      const items = s.items.filter(i => ['title', 'org', 'phone', 'email'].some(k => filled(i[k])))
        .map(i => ({ title: i.title || '', org: i.org || '', city: i.city || '', phone: i.phone || '', email: i.email || '' }));
      if (items.length) sections.push({ ...base, items });
    } else {
      const items = s.items
        .filter(i => ['title', 'org', 'description', 'start', 'end'].some(k => filled(i[k])))
        .map(i => ({
          title: (i.title || '').trim(), org: (i.org || '').trim(), city: (i.city || '').trim(),
          dates: formatRange(i, L), blocks: parseRich(i.description),
        }));
      if (items.length) sections.push({ ...base, items });
    }
  }

  return {
    lang: st.lang, L, style: st,
    name, firstName: p.firstName || '', lastName: p.lastName || '',
    role: (p.role || '').trim(),
    title: p.roleAsTitle && filled(p.role) ? p.role.trim() : L.resume,
    photo: p.photo || '',
    contact, sections,
  };
}

// Nome de arquivo seguro: "Curriculo-Marina-Duarte"
export function fileBase(doc) {
  const n = (doc.name || 'curriculo').normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '');
  return `Curriculo-${n || 'sem-nome'}`;
}
