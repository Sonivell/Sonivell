// Desenha o documento (doc.js) como HTML, no modelo escolhido.
// O mesmo HTML serve para a visualização, as miniaturas e a impressão em PDF.

export const FONTS = {
  inter: { label: 'Inter', css: "'Inter', system-ui, sans-serif" },
  manrope: { label: 'Manrope', css: "'Manrope', system-ui, sans-serif" },
  carlito: { label: 'Carlito', css: "'Carlito', Calibri, sans-serif" },
  caladea: { label: 'Caladea', css: "'Caladea', Cambria, Georgia, serif" },
};

// layout: single | side-left | side-right
// contact: onde ficam os dados pessoais — head (linha no cabeçalho), side (coluna), table (tabela rotulada)
// photo: head | side
// datesLeft: datas numa coluna à esquerda
// levels: estilo padrão dos níveis
// ats: estrutura de uma coluna, a mais segura para leitura automática
export const TEMPLATES = {
  essencial: { label: 'Essencial', layout: 'single', contact: 'head', photo: 'head', levels: 'text', ats: true },
  cronologico: { label: 'Cronológico', layout: 'single', contact: 'head', photo: 'head', datesLeft: true, levels: 'bar', ats: true },
  executivo: { label: 'Executivo', layout: 'single', contact: 'head', photo: 'head', levels: 'text', ats: true },
  classico: { label: 'Clássico', layout: 'single', contact: 'head', photo: 'head', levels: 'dots', ats: true },
  tabela: { label: 'Tabela', layout: 'single', contact: 'table', photo: 'table', datesLeft: true, levels: 'text', ats: true },
  coluna: { label: 'Coluna', layout: 'side-left', contact: 'side', photo: 'side', levels: 'bar' },
  contraste: { label: 'Contraste', layout: 'side-left', contact: 'side', photo: 'side', levels: 'dots' },
  faixa: { label: 'Faixa', layout: 'side-right', contact: 'head', photo: 'head', levels: 'bar' },
  elegante: { label: 'Elegante', layout: 'side-left', contact: 'side', photo: 'side', levels: 'bar' },
  vertical: { label: 'Vertical', layout: 'side-left', contact: 'side', photo: 'side', levels: 'dots' },
};

const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const runsHtml = runs => runs.map(r => {
  let h = esc(r.text);
  if (r.bold) h = `<strong>${h}</strong>`;
  if (r.italic) h = `<em>${h}</em>`;
  return h;
}).join('');

const blocksHtml = blocks => blocks.map(b => b.type === 'ul'
  ? `<ul>${b.items.map(i => `<li>${runsHtml(i)}</li>`).join('')}</ul>`
  : `<p>${runsHtml(b.runs)}</p>`).join('');

// Ícones desenhados à mão (traço simples), usados no contato.
const ICON = {
  email: '<path d="M3 6h18v12H3z"/><path d="m3 7 9 6 9-6"/>',
  phone: '<path d="M6 3h4l2 5-2.5 1.5a11 11 0 0 0 5 5L16 12l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 4 5a2 2 0 0 1 2-2"/>',
  address: '<path d="M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z"/><circle cx="12" cy="9" r="2.5"/>',
  website: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
  linkedin: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M8 10v7M8 7v.01M12 17v-4a2 2 0 0 1 4 0v4M12 10v7"/>',
  birthDate: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  driverLicense: '<rect x="3" y="6" width="18" height="12" rx="2"/><circle cx="8.5" cy="12" r="2"/><path d="M13 10h5M13 14h4"/>',
  default: '<circle cx="12" cy="12" r="3"/>',
};
const icon = key => `<svg class="cv-ico" viewBox="0 0 24 24" aria-hidden="true">${ICON[key] || ICON.default}</svg>`;

function levelHtml(item, style) {
  if (!item.level || style === 'none') return '';
  if (style === 'text') return `<span class="lv-text">${esc(item.levelText)}</span>`;
  if (style === 'dots') {
    return `<span class="lv-dots" aria-label="${esc(item.levelText)}">${[1, 2, 3, 4, 5]
      .map(n => `<i class="${n <= item.level ? 'on' : ''}"></i>`).join('')}</span>`;
  }
  return `<span class="lv-bar" aria-label="${esc(item.levelText)}"><i style="width:${item.level * 20}%"></i></span>`;
}

function entryHtml(it, tpl) {
  const meta = [it.org, it.city].filter(Boolean).join(', ');
  const dates = it.dates ? `<span class="cv-dates">${esc(it.dates)}</span>` : '';
  const head = `<div class="cv-entry-head"><h3>${esc(it.title)}</h3>${tpl.datesLeft ? '' : dates}</div>
    ${meta ? `<div class="cv-org">${esc(meta)}</div>` : ''}`;
  const body = `${head}${it.blocks.length ? `<div class="cv-desc">${blocksHtml(it.blocks)}</div>` : ''}`;
  return tpl.datesLeft
    ? `<div class="cv-entry dl"><div class="cv-when">${dates}</div><div>${body}</div></div>`
    : `<div class="cv-entry">${body}</div>`;
}

function sectionHtml(s, tpl, levelStyle) {
  let inner = '';
  if (s.kind === 'text') {
    inner = `<div class="cv-desc">${blocksHtml(s.blocks)}</div>`;
  } else if (s.kind === 'entries') {
    inner = s.items.map(it => entryHtml(it, tpl)).join('');
  } else if (s.kind === 'levels') {
    inner = `<ul class="cv-levels lvs-${levelStyle}">${s.items.map(i =>
      `<li><span class="lv-name">${esc(i.name)}</span>${levelHtml(i, levelStyle)}</li>`).join('')}</ul>`;
  } else if (s.kind === 'tags') {
    inner = `<ul class="cv-tags">${s.items.map(i => `<li>${esc(i.name)}</li>`).join('')}</ul>`;
  } else if (s.kind === 'references') {
    inner = `<div class="cv-refs">${s.items.map(i => `<div class="cv-ref"><h3>${esc(i.title)}</h3>
      ${i.org || i.city ? `<div class="cv-org">${esc([i.org, i.city].filter(Boolean).join(', '))}</div>` : ''}
      ${[i.phone, i.email].filter(Boolean).map(v => `<div>${esc(v)}</div>`).join('')}</div>`).join('')}</div>`;
  } else if (s.kind === 'signature') {
    inner = `<div class="cv-sign">${s.place ? `<div>${esc(s.place)}</div>` : ''}
      ${s.image ? `<img src="${s.image}" alt="">` : '<div class="cv-sign-line"></div>'}
      ${s.name ? `<div class="cv-sign-name">${esc(s.name)}</div>` : ''}</div>`;
  }
  if (s.type === 'footer') return `<footer class="cv-footer">${inner}</footer>`;
  const title = s.title ? `<h2 class="cv-h2"><span>${esc(s.title)}</span></h2>` : '';
  return `<section class="cv-sec sec-${s.type}">${title}${inner}</section>`;
}

const photoHtml = (doc, shape) => doc.photo
  ? `<img class="cv-photo ph-${shape}" src="${doc.photo}" alt="">` : '';

function contactList(doc, withLabels) {
  if (!doc.contact.length) return '';
  return `<ul class="cv-contact${withLabels ? ' labeled' : ''}">${doc.contact.map(c => {
    const val = c.href ? `<a href="${esc(c.href)}">${esc(c.value)}</a>` : esc(c.value);
    return withLabels
      ? `<li><span class="cv-lbl">${esc(c.label)}</span><span class="cv-val">${val}</span></li>`
      : `<li>${icon(c.key)}<span>${c.key === 'custom' && c.label ? `${esc(c.label)}: ` : ''}${val}</span></li>`;
  }).join('')}</ul>`;
}

function personalTable(doc, shape) {
  const rows = [{ label: doc.L.fields.name, value: doc.name }, ...doc.contact].filter(r => r.value);
  if (!rows.length && !doc.photo) return '';
  return `<section class="cv-sec sec-personal"><h2 class="cv-h2"><span>${esc(doc.L.personal)}</span></h2>
    <div class="cv-ptable"><table>${rows.map(r =>
      `<tr><th>${esc(r.label)}</th><td>${r.href ? `<a href="${esc(r.href)}">${esc(r.value)}</a>` : esc(r.value)}</td></tr>`).join('')}
    </table>${photoHtml(doc, shape)}</div></section>`;
}

export function renderResume(doc) {
  const st = doc.style;
  const tpl = TEMPLATES[st.template] || TEMPLATES.essencial;
  const levelStyle = st.levelStyle === 'auto' ? tpl.levels : st.levelStyle;
  const twoCol = tpl.layout !== 'single';
  const font = (FONTS[st.font] || FONTS.inter).css;

  const vars = [
    `--accent:${st.color}`, `--font:${font}`, `--fs:${st.fontSize}pt`,
    `--lh:${st.lineHeight}`, `--gap:${st.spacing}`,
  ].join(';');

  const footer = doc.sections.filter(s => s.type === 'footer');
  const content = doc.sections.filter(s => s.type !== 'footer');
  const side = twoCol ? content.filter(s => s.side) : [];
  const main = twoCol ? content.filter(s => !s.side) : content;

  const headPhoto = tpl.photo === 'head' ? photoHtml(doc, st.photoShape) : '';
  const header = `<header class="cv-head">
      ${headPhoto}
      <div class="cv-head-text">
        ${tpl.contact === 'table' ? `<div class="cv-kicker">${esc(doc.title)}</div>` : ''}
        ${tpl.contact !== 'table' && doc.name ? `<h1 class="cv-name">${esc(doc.name)}</h1>` : ''}
        ${tpl.contact !== 'table' && doc.title !== doc.L.resume ? `<div class="cv-role">${esc(doc.title)}</div>`
          : (tpl.contact !== 'table' && doc.role ? `<div class="cv-role">${esc(doc.role)}</div>` : '')}
        ${tpl.contact === 'head' ? contactList(doc, false) : ''}
      </div>
    </header>`;

  const sideContact = tpl.contact === 'side' && (doc.contact.length)
    ? `<section class="cv-sec sec-personal"><h2 class="cv-h2"><span>${esc(doc.L.personal)}</span></h2>${contactList(doc, true)}</section>` : '';

  const mainHtml = (tpl.contact === 'table' ? personalTable(doc, st.photoShape) : '')
    + main.map(s => sectionHtml(s, tpl, levelStyle)).join('');

  const body = twoCol
    ? `<div class="cv-body">
        <aside class="cv-side">
          ${tpl.photo === 'side' ? photoHtml(doc, st.photoShape) : ''}
          ${sideContact}
          ${side.map(s => sectionHtml(s, tpl, levelStyle)).join('')}
        </aside>
        <main class="cv-main">${tpl.photo === 'side' || tpl.contact === 'side' ? header : ''}${mainHtml}</main>
      </div>`
    : `<div class="cv-body"><main class="cv-main">${mainHtml}</main></div>`;

  const topHeader = twoCol && (tpl.photo === 'side' || tpl.contact === 'side') ? '' : header;

  return `<article class="cv tpl-${st.template} layout-${tpl.layout}" style="${vars}" lang="${doc.lang}">
    ${twoCol ? '<div class="cv-sidebg" aria-hidden="true"></div>' : ''}
    ${topHeader}
    ${body}
    ${footer.map(s => sectionHtml(s, tpl, levelStyle)).join('')}
  </article>`;
}
