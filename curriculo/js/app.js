import {
  SECTION_TYPES, ADDABLE, PERSONAL_EXTRAS, LEVELS, newResume, newSection, newItem, normalize, uid, EXAMPLE,
} from './model.js';
import { buildDoc, fileBase } from './doc.js';
import { renderResume, TEMPLATES, FONTS } from './render.js';
import { RESUME_LANGS } from './i18n.js';
import * as store from './store.js';
import {
  h, icon, iconBtn, sortable, popMenu, confirmDialog, promptDialog, toast, download, pickFile, loadPhoto, debounce,
} from './ui.js';
import { importFile } from './import.js';
import { analyze, matchKeywords } from './ats.js';
import { buildPdf } from './export/pdf.js';
import { buildDocx } from './export/docx.js';
import { toText, toMarkdown, toHtml, toJsonResume } from './export/text.js';

export const APP_NAME = 'Currículo';

const $ = s => document.querySelector(s);
const A4_PX = 297 * 96 / 25.4; // altura de uma página A4 em px CSS

const state = {
  resume: null,
  openItems: new Set(),
  personalOpen: true,
  styleTab: 'templates',
  previewing: false,
};

// ======================= Persistência =======================

const setSaveStatus = busy => {
  const el = $('#save-status');
  if (!el) return;
  el.replaceChildren(icon(busy ? 'cloudBusy' : 'cloud'));
  el.title = busy ? 'Salvando…' : 'Salvo no aparelho';
  el.classList.toggle('busy', busy);
};

const saveNow = async () => {
  if (!state.resume) return;
  await store.saveResume(state.resume);
  setSaveStatus(false);
};
const saveSoon = debounce(saveNow, 400);
const previewSoon = debounce(() => renderPreview(), 120);

function touch({ preview = true } = {}) {
  state.resume.updatedAt = Date.now();
  setSaveStatus(true);
  saveSoon();
  if (preview) previewSoon();
}

// ======================= Rotas =======================

async function route() {
  const m = location.hash.match(/^#\/r\/([^/]+)(\/preview)?/);
  if (!m) {
    if (state.resume) await saveSoon.flush();
    state.resume = null;
    return showHome();
  }
  if (state.resume?.id !== m[1]) {
    const r = await store.getResume(m[1]);
    if (!r) { location.hash = ''; return; }
    state.resume = r;
    state.openItems.clear();
    renderEditor();
  }
  state.previewing = !!m[2];
  showEditor();
}

const openResume = (id, preview = false) => { location.hash = `#/r/${id}${preview ? '/preview' : ''}`; };

// ======================= Início =======================

async function showHome() {
  document.body.dataset.view = 'home';
  document.title = APP_NAME;
  const list = await store.listResumes();
  const grid = $('#resume-list');
  grid.replaceChildren();

  if (!list.length) {
    grid.append(h('div', { class: 'empty' },
      h('p', { class: 'empty-title' }, 'Nenhum currículo ainda'),
      h('p', {}, 'Crie do zero ou comece pelo exemplo. Tudo fica salvo só neste aparelho e funciona sem internet.')));
  }
  for (const r of list) {
    const thumb = h('div', { class: 'thumb' });
    thumb.innerHTML = renderResume(buildDoc(r));
    const more = iconBtn('more', 'Opções', e => {
      e.stopPropagation();
      popMenu(more, [
        { label: 'Abrir', icon: 'edit', action: () => openResume(r.id) },
        { label: 'Renomear', icon: 'pen', action: () => renameResume(r).then(showHome) },
        { label: 'Duplicar', icon: 'copy', action: () => duplicateResume(r).then(showHome) },
        { label: 'Baixar backup (.json)', icon: 'download', action: () => exportBackup([r]) },
        { label: 'Excluir', icon: 'trash', danger: true, action: () => removeResume(r).then(showHome) },
      ]);
    });
    grid.append(h('article', { class: 'card', tabindex: 0, onclick: () => openResume(r.id), onkeydown: e => { if (e.key === 'Enter') openResume(r.id); } },
      h('div', { class: 'thumb-box' }, thumb),
      h('div', { class: 'card-info' },
        h('div', {}, h('h3', {}, r.name), h('p', {}, `Editado ${new Date(r.updatedAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`)),
        more)));
  }
  scaleThumbs(grid);
}

// Ajusta a escala das miniaturas à largura de cada cartão.
const thumbObserver = new ResizeObserver(entries => {
  for (const e of entries) e.target.style.setProperty('--s', e.contentRect.width / (210 * 96 / 25.4));
});
function scaleThumbs(root) {
  root.querySelectorAll('.card .thumb-box').forEach(b => thumbObserver.observe(b));
}

async function createResume(example = false) {
  const r = example ? EXAMPLE() : newResume();
  await store.saveResume(r);
  openResume(r.id);
}

async function renameResume(r) {
  const name = await promptDialog('Nome do currículo (só para você se organizar)', r.name);
  if (!name) return;
  r.name = name;
  r.updatedAt = Date.now();
  await store.saveResume(r);
}

async function duplicateResume(r) {
  const copy = normalize(JSON.parse(JSON.stringify({ ...r, id: uid(), name: `${r.name} (cópia)`, createdAt: Date.now(), updatedAt: Date.now() })));
  await store.saveResume(copy);
  toast('Cópia criada');
  return copy;
}

async function removeResume(r) {
  if (!(await confirmDialog(`Excluir "${r.name}"? Isso não pode ser desfeito.`, { ok: 'Excluir', danger: true }))) return false;
  await store.deleteResume(r.id);
  toast('Currículo excluído');
  return true;
}

function exportBackup(resumes) {
  const data = { app: 'curriculo', version: 1, exportedAt: new Date().toISOString(), resumes };
  const name = resumes.length === 1 ? fileBase(buildDoc(resumes[0])) : 'curriculos-backup';
  download(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }), `${name}.json`);
}

async function importFlow(intoCurrent = false) {
  const file = await pickFile('.json,.zip,application/json,application/zip');
  if (!file) return;
  try {
    const imported = await importFile(file);
    if (intoCurrent && state.resume && imported.length === 1) {
      const keep = { id: state.resume.id, name: state.resume.name, style: state.resume.style, createdAt: state.resume.createdAt };
      state.resume = normalize({ ...imported[0], ...keep });
      touch();
      renderEditor();
      toast('Dados importados');
      return;
    }
    for (const r of imported) await store.saveResume(r);
    toast(imported.length === 1 ? 'Currículo importado' : `${imported.length} currículos importados`);
    if (imported.length === 1) openResume(imported[0].id);
    else showHome();
  } catch (err) {
    console.error(err);
    toast(err.message || 'Não foi possível importar este arquivo.');
  }
}

// ======================= Editor =======================

function showEditor() {
  document.body.dataset.view = 'editor';
  document.body.classList.toggle('previewing', state.previewing);
  const previewOnly = state.previewing && !matchMedia('(min-width: 1000px)').matches;
  $('#doc-title').textContent = previewOnly ? 'Visualização' : state.resume.name;
  $('#doc-title').disabled = previewOnly;
  document.title = `${state.resume.name} · ${APP_NAME}`;
  setSaveStatus(false);
  renderPreview();
  renderStylePanel();
  requestAnimationFrame(fitPreview);
}

function field(label, input, cls = '') {
  return h('label', { class: `field ${cls}` }, h('span', {}, label), input);
}

function textInput(obj, key, opts = {}) {
  return h('input', {
    class: 'input', type: opts.type || 'text', value: obj[key] ?? '', placeholder: opts.placeholder || '',
    autocomplete: opts.autocomplete || 'off', inputmode: opts.inputmode,
    oninput: e => { obj[key] = e.target.value; touch(); opts.after?.(); },
  });
}

// Seletor de mês e ano (funciona em qualquer navegador, ao contrário de type=month).
function monthPicker(obj, key, label, disabled = false) {
  const [y = '', m = ''] = String(obj[key] || '').split('-');
  const nowY = new Date().getFullYear();
  const month = h('select', { class: 'input', 'aria-label': `${label}: mês`, disabled },
    h('option', { value: '' }, 'Mês'),
    ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
      .map((n, i) => h('option', { value: String(i + 1).padStart(2, '0'), selected: m === String(i + 1).padStart(2, '0') }, n)));
  const year = h('select', { class: 'input', 'aria-label': `${label}: ano`, disabled },
    h('option', { value: '' }, 'Ano'),
    Array.from({ length: 70 }, (_, i) => String(nowY + 6 - i)).map(v => h('option', { value: v, selected: v === y }, v)));
  const update = () => {
    obj[key] = year.value ? (month.value ? `${year.value}-${month.value}` : year.value) : '';
    touch();
  };
  month.onchange = update;
  year.onchange = update;
  return h('div', { class: 'field' }, h('span', {}, label), h('div', { class: 'month-pick' }, month, year));
}

// Área de texto com atalhos de negrito, itálico e lista (marcação simples).
function richArea(obj, key, { placeholder = '', after } = {}) {
  const ta = h('textarea', {
    class: 'input rich', rows: 5, placeholder, value: obj[key] || '',
    oninput: e => { obj[key] = e.target.value; touch(); after?.(); autoGrow(e.target); },
  });
  const wrapSel = (mark) => {
    const { selectionStart: a, selectionEnd: b, value } = ta;
    const sel = value.slice(a, b) || 'texto';
    ta.setRangeText(`${mark}${sel}${mark}`, a, b, 'select');
    ta.setSelectionRange(a + mark.length, a + mark.length + sel.length);
    ta.dispatchEvent(new Event('input'));
    ta.focus();
  };
  const bullets = () => {
    const { selectionStart: a, selectionEnd: b, value } = ta;
    const lineStart = value.lastIndexOf('\n', a - 1) + 1;
    const lineEnd = value.indexOf('\n', b) === -1 ? value.length : value.indexOf('\n', b);
    const block = value.slice(lineStart, lineEnd).split('\n');
    const allBullets = block.every(l => /^[-•]\s/.test(l));
    const next = block.map(l => allBullets ? l.replace(/^[-•]\s/, '') : `- ${l.replace(/^[-•]\s/, '')}`).join('\n');
    ta.setRangeText(next, lineStart, lineEnd, 'end');
    ta.dispatchEvent(new Event('input'));
    ta.focus();
  };
  ta.addEventListener('keydown', e => {
    // Enter dentro de uma lista continua a lista.
    if (e.key === 'Enter' && !e.shiftKey) {
      const { selectionStart: a, value } = ta;
      const lineStart = value.lastIndexOf('\n', a - 1) + 1;
      const line = value.slice(lineStart, a);
      const m = line.match(/^([-•])\s/);
      if (m) {
        e.preventDefault();
        if (line.trim() === m[1]) ta.setRangeText('', lineStart, a, 'end');
        else ta.setRangeText(`\n${m[1]} `, a, ta.selectionEnd, 'end');
        ta.dispatchEvent(new Event('input'));
      }
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') { e.preventDefault(); wrapSel('**'); }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'i') { e.preventDefault(); wrapSel('*'); }
  });
  requestAnimationFrame(() => autoGrow(ta));
  return h('div', { class: 'rich-wrap' },
    h('div', { class: 'rich-tools' },
      iconBtn('bold', 'Negrito (Ctrl+B)', () => wrapSel('**')),
      iconBtn('italic', 'Itálico (Ctrl+I)', () => wrapSel('*')),
      iconBtn('list', 'Lista com marcadores', bullets),
      h('span', { class: 'rich-hint' }, 'Linhas com "- " viram tópicos')),
    ta);
}

function autoGrow(ta) {
  if (!ta.isConnected) return;
  ta.style.height = 'auto';
  ta.style.height = `${Math.min(ta.scrollHeight + 2, 480)}px`;
}

function panel({ title, open, onToggle, actions = [], handle = false, id, cls = '' }, ...body) {
  const bodyEl = h('div', { class: 'panel-body', hidden: !open }, ...body);
  const head = h('div', { class: 'panel-head' },
    handle ? h('span', { class: 'drag-handle', title: 'Arraste para reordenar', 'aria-hidden': 'true' }, icon('drag')) : null,
    h('button', { type: 'button', class: 'panel-title', 'aria-expanded': String(open), onclick: () => onToggle(!open) }, title),
    ...actions,
    iconBtn('chevron', open ? 'Recolher' : 'Expandir', () => onToggle(!open), `chev ${open ? 'open' : ''}`));
  return h('section', { class: `panel ${cls}`, dataset: id ? { id } : {} }, head, bodyEl);
}

function renderEditor() {
  const r = state.resume;
  const ed = $('#editor');
  const scrollTop = ed.scrollTop;
  ed.replaceChildren(
    h('div', { class: 'import-row' },
      h('button', { type: 'button', class: 'import-card', onclick: () => importFlow(true) },
        icon('upload'), h('span', {}, 'Importar currículo existente'), h('small', {}, 'JSON Resume ou backup')),
      h('button', { type: 'button', class: 'import-card', onclick: () => importFlow(true) },
        icon('linkedin'), h('span', {}, 'Importar do LinkedIn'), h('small', {}, 'ZIP dos seus dados'))),
    personalPanel(r.personal),
    h('div', { class: 'sections', id: 'sections' }, r.sections.map(s => sectionPanel(s))),
    addSectionChips(),
    h('div', { class: 'editor-foot' },
      h('button', { type: 'button', class: 'btn primary only-mobile', onclick: () => openResume(r.id, true) }, icon('preview'), 'Visualização')));
  sortable($('#sections'), {
    item: '.panel', handle: '.drag-handle',
    onEnd: ids => {
      r.sections.sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
      touch();
    },
  });
  ed.scrollTop = scrollTop;
}

function personalPanel(p) {
  const photo = h('button', {
    type: 'button', class: `photo-pick ${p.photo ? 'has' : ''}`, 'aria-label': 'Escolher foto',
    onclick: async () => {
      const f = await pickFile('image/*');
      if (!f) return;
      try { p.photo = await loadPhoto(f); touch(); renderEditor(); } catch { toast('Não foi possível ler esta imagem.'); }
    },
  }, p.photo ? h('img', { src: p.photo, alt: 'Foto' }) : icon('camera'));

  const extras = h('div', { class: 'extras' }, (p.extras || []).map(e => {
    const def = PERSONAL_EXTRAS.find(d => d.key === e.key);
    const remove = iconBtn('close', 'Remover campo', () => { p.extras = p.extras.filter(x => x !== e); touch(); renderEditor(); }, 'small');
    const input = e.key === 'birthDate'
      ? textInput(e, 'value', { type: 'date' })
      : textInput(e, 'value', { inputmode: ['website', 'linkedin'].includes(e.key) ? 'url' : undefined });
    if (e.key === 'custom') {
      return h('div', { class: 'grid2 extra-row' },
        field('Rótulo', textInput(e, 'label', { placeholder: 'ex.: Portfólio' })),
        h('div', { class: 'field with-remove' }, h('span', {}, 'Valor'), h('div', { class: 'row' }, input, remove)));
    }
    return h('div', { class: 'field with-remove extra-row' }, h('span', {}, def.label), h('div', { class: 'row' }, input, remove));
  }));

  const used = new Set((p.extras || []).map(e => e.key));
  const chips = h('div', { class: 'chips' }, PERSONAL_EXTRAS.filter(d => d.multiple || !used.has(d.key)).map(d =>
    h('button', {
      type: 'button', class: 'chip',
      onclick: () => { p.extras.push({ id: uid(), key: d.key, value: '', label: '' }); touch(); renderEditor(); },
    }, icon('plus'), d.label)));

  const roleToggle = h('label', { class: 'switch' },
    h('input', { type: 'checkbox', checked: !!p.roleAsTitle, onchange: e => { p.roleAsTitle = e.target.checked; touch(); } }),
    h('span', { class: 'switch-ui', 'aria-hidden': 'true' }), 'Usar como título');

  return panel({
    title: 'Dados pessoais', open: state.personalOpen, cls: 'personal',
    onToggle: v => { state.personalOpen = v; renderEditor(); },
    actions: [p.photo ? iconBtn('trash', 'Remover foto', () => { p.photo = ''; touch(); renderEditor(); }) : null],
  },
  h('div', { class: 'field' }, h('span', {}, 'Foto'), photo),
  h('div', { class: 'grid2' },
    field('Nome', textInput(p, 'firstName', { autocomplete: 'given-name' })),
    field('Sobrenome', textInput(p, 'lastName', { autocomplete: 'family-name' }))),
  h('div', { class: 'field' }, h('div', { class: 'field-top' }, h('span', {}, 'Vaga desejada'), roleToggle),
    textInput(p, 'role', { placeholder: 'ex.: Assistente Administrativo' })),
  h('div', { class: 'grid2' },
    field('E-mail', textInput(p, 'email', { type: 'email', autocomplete: 'email', inputmode: 'email' })),
    field('Telefone', textInput(p, 'phone', { type: 'tel', autocomplete: 'tel', inputmode: 'tel' }))),
  field('Endereço', textInput(p, 'address', { autocomplete: 'street-address' })),
  h('div', { class: 'grid2' },
    field('CEP', textInput(p, 'postalCode', { autocomplete: 'postal-code', inputmode: 'numeric' })),
    field('Cidade', textInput(p, 'city', { autocomplete: 'address-level2' }))),
  extras, chips);
}

const sectionTitle = s => s.title || SECTION_TYPES[s.type].label;

function sectionPanel(s) {
  const def = SECTION_TYPES[s.type];
  const r = state.resume;
  const idx = r.sections.indexOf(s);
  const move = d => {
    const j = idx + d;
    if (j < 0 || j >= r.sections.length) return;
    [r.sections[idx], r.sections[j]] = [r.sections[j], r.sections[idx]];
    touch(); renderEditor();
  };
  const more = iconBtn('more', 'Opções da seção', () => popMenu(more, [
    { label: 'Renomear', icon: 'pen', action: async () => {
      const t = await promptDialog('Título da seção', sectionTitle(s));
      if (t !== null) { s.title = t === def.label ? '' : t; touch(); renderEditor(); }
    } },
    { label: s.hidden ? 'Mostrar no currículo' : 'Ocultar do currículo', icon: s.hidden ? 'eye' : 'eyeOff', action: () => { s.hidden = !s.hidden; touch(); renderEditor(); } },
    idx > 0 && { label: 'Mover para cima', icon: 'up', action: () => move(-1) },
    idx < r.sections.length - 1 && { label: 'Mover para baixo', icon: 'down', action: () => move(1) },
    { label: 'Excluir seção', icon: 'trash', danger: true, action: async () => {
      if (await confirmDialog(`Excluir a seção "${sectionTitle(s)}"?`, { ok: 'Excluir', danger: true })) {
        r.sections = r.sections.filter(x => x !== s); touch(); renderEditor();
      }
    } },
  ]));

  const addItem = () => {
    const it = newItem(s);
    s.items.push(it);
    state.openItems.add(it.id);
    s.collapsed = false;
    touch(); renderEditor();
    requestAnimationFrame(() => document.querySelector(`[data-id="${it.id}"] input, [data-id="${it.id}"] textarea`)?.focus());
  };
  const actions = [more];
  if (s.items) actions.unshift(iconBtn('plus', `Adicionar em ${sectionTitle(s)}`, addItem, 'add'));

  let body;
  if (def.kind === 'text') {
    body = richArea(s, 'content', {
      placeholder: s.type === 'summary'
        ? 'Em 3 a 5 frases: quem você é, sua experiência, principais competências e o que você busca.'
        : 'Texto livre',
    });
  } else if (def.kind === 'signature') {
    body = signatureEditor(s);
  } else if (def.kind === 'levels' || def.kind === 'tags') {
    body = h('div', {},
      h('div', { class: 'simple-items', id: `items-${s.id}` }, s.items.map(it => simpleItem(s, it))),
      h('button', { type: 'button', class: 'btn ghost add-line', onclick: addItem }, icon('plus'), `Adicionar ${def.itemLabel.toLowerCase()}`));
  } else {
    body = h('div', {},
      h('div', { class: 'items', id: `items-${s.id}` }, s.items.map(it => entryItem(s, it))),
      h('button', { type: 'button', class: 'btn ghost add-line', onclick: addItem }, icon('plus'), 'Adicionar item'));
  }

  const el = panel({
    title: h('span', {}, sectionTitle(s), s.hidden ? h('em', { class: 'muted' }, ' · oculta') : null),
    open: !s.collapsed, handle: true, id: s.id, cls: s.hidden ? 'is-hidden' : '',
    onToggle: v => { s.collapsed = !v; touch({ preview: false }); renderEditor(); },
    actions,
  }, body);

  const list = el.querySelector('.items, .simple-items');
  if (list) {
    sortable(list, {
      item: '[data-id]', handle: '.item-handle',
      onEnd: ids => { s.items.sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id)); touch(); },
    });
  }
  return el;
}

function entryItem(s, it) {
  const def = SECTION_TYPES[s.type];
  const open = state.openItems.has(it.id);
  const summaryText = () => [it.title || it.org || '(sem título)', it.title && it.org ? it.org : ''].filter(Boolean).join(' · ');
  const summary = h('span', { class: 'item-title' }, summaryText());
  const dates = () => [it.start, it.current ? 'atual' : it.end].filter(Boolean).map(v => v.split('-').reverse().join('/')).join(' – ');
  const sub = h('span', { class: 'item-sub' }, dates());
  const refresh = () => { summary.textContent = summaryText(); sub.textContent = dates(); };

  const fields = [];
  let half = [];
  const flush = () => { if (half.length) { fields.push(h('div', { class: 'grid2' }, half)); half = []; } };
  for (const f of def.fields) {
    let el;
    if (f.type === 'text') el = field(f.label, textInput(it, f.key, { after: refresh }));
    else if (f.type === 'month') el = monthPicker(it, f.key, f.label, f.key === 'end' && it.current);
    else if (f.type === 'check') {
      el = h('label', { class: 'check' },
        h('input', { type: 'checkbox', checked: !!it.current, onchange: e => { it.current = e.target.checked; touch(); renderEditor(); } }),
        'Até o momento');
    } else if (f.type === 'rich') {
      el = h('div', { class: 'field' }, h('span', {}, f.label), richArea(it, f.key, {
        placeholder: s.type === 'experience' || s.type === 'internships'
          ? '- Principais entregas, com números quando possível\n- Ferramentas e responsabilidades'
          : '',
      }));
    }
    if (f.half) { half.push(el); if (half.length === 2) flush(); } else { flush(); fields.push(el); }
  }
  flush();

  return h('div', { class: `item ${open ? 'open' : ''}`, dataset: { id: it.id } },
    h('div', { class: 'item-head' },
      h('span', { class: 'item-handle drag-handle', title: 'Arraste para reordenar' }, icon('drag')),
      h('button', {
        type: 'button', class: 'item-toggle', 'aria-expanded': String(open),
        onclick: () => { open ? state.openItems.delete(it.id) : state.openItems.add(it.id); renderEditor(); },
      }, summary, sub),
      iconBtn('trash', 'Excluir item', async () => {
        if (!(it.title || it.org || it.description) || await confirmDialog('Excluir este item?', { ok: 'Excluir', danger: true })) {
          s.items = s.items.filter(x => x !== it); touch(); renderEditor();
        }
      }),
      iconBtn('chevron', open ? 'Recolher' : 'Expandir', () => { open ? state.openItems.delete(it.id) : state.openItems.add(it.id); renderEditor(); }, `chev ${open ? 'open' : ''}`)),
    open ? h('div', { class: 'item-body' }, fields) : null);
}

function simpleItem(s, it) {
  const def = SECTION_TYPES[s.type];
  const row = h('div', { class: 'simple-item', dataset: { id: it.id } },
    h('span', { class: 'item-handle drag-handle', title: 'Arraste para reordenar' }, icon('drag')),
    h('input', {
      class: 'input', value: it.name, placeholder: def.itemLabel, 'aria-label': def.itemLabel,
      oninput: e => { it.name = e.target.value; touch(); },
      onkeydown: e => {
        if (e.key === 'Enter') {
          e.preventDefault();
          const n = newItem(s);
          s.items.splice(s.items.indexOf(it) + 1, 0, n);
          touch(); renderEditor();
          requestAnimationFrame(() => document.querySelector(`[data-id="${n.id}"] input`)?.focus());
        }
      },
    }));
  if (def.kind === 'levels') {
    const names = LEVELS[def.levels];
    const sel = h('select', {
      class: 'input level', 'aria-label': 'Nível',
      onchange: e => { it.level = Number(e.target.value); touch(); },
    }, names.map((n, i) => h('option', { value: i, selected: Number(it.level) === i }, i ? n : 'Sem nível')));
    row.append(sel);
  }
  row.append(iconBtn('close', 'Remover', () => { s.items = s.items.filter(x => x !== it); touch(); renderEditor(); }));
  return row;
}

function signatureEditor(s) {
  const preview = s.image ? h('img', { class: 'sign-img', src: s.image, alt: 'Assinatura' }) : null;
  return h('div', {},
    h('div', { class: 'grid2' }, field('Cidade', textInput(s, 'city')), field('Data', textInput(s, 'date', { placeholder: 'ex.: 30 de outubro de 2026' }))),
    field('Nome sob a assinatura', textInput(s, 'name')),
    h('div', { class: 'row gap' },
      preview,
      h('button', { type: 'button', class: 'btn ghost', onclick: () => drawSignature(s) }, icon('pen'), s.image ? 'Refazer assinatura' : 'Desenhar assinatura'),
      s.image ? h('button', { type: 'button', class: 'btn ghost', onclick: () => { s.image = ''; touch(); renderEditor(); } }, 'Remover') : null));
}

function drawSignature(s) {
  const canvas = h('canvas', { class: 'sign-canvas', width: 900, height: 300 });
  const ctx = canvas.getContext('2d');
  ctx.lineWidth = 5; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = '#1b1e23';
  let drawing = false, empty = true;
  const pos = e => { const r = canvas.getBoundingClientRect(); return [(e.clientX - r.left) * canvas.width / r.width, (e.clientY - r.top) * canvas.height / r.height]; };
  canvas.addEventListener('pointerdown', e => { drawing = true; empty = false; canvas.setPointerCapture(e.pointerId); ctx.beginPath(); ctx.moveTo(...pos(e)); });
  canvas.addEventListener('pointermove', e => { if (drawing) { ctx.lineTo(...pos(e)); ctx.stroke(); } });
  canvas.addEventListener('pointerup', () => { drawing = false; });
  const dlg = h('dialog', { class: 'dlg' },
    h('h2', {}, 'Desenhe sua assinatura'),
    canvas,
    h('div', { class: 'dlg-actions' },
      h('button', { type: 'button', class: 'btn ghost', onclick: () => { ctx.clearRect(0, 0, canvas.width, canvas.height); empty = true; } }, 'Limpar'),
      h('button', { type: 'button', class: 'btn ghost', onclick: () => close() }, 'Cancelar'),
      h('button', { type: 'button', class: 'btn primary', onclick: () => { if (!empty) { s.image = canvas.toDataURL('image/png'); touch(); renderEditor(); } close(); } }, 'Salvar')));
  const close = () => { dlg.close(); dlg.remove(); };
  dlg.addEventListener('cancel', close);
  document.body.append(dlg);
  dlg.showModal();
}

function addSectionChips() {
  const present = new Set(state.resume.sections.map(s => s.type));
  const all = ['experience', 'education', 'skills', 'languages', 'hobbies', ...ADDABLE];
  return h('div', { class: 'add-sections' },
    h('h3', {}, 'Adicionar seção'),
    h('div', { class: 'chips' }, all.filter(t => SECTION_TYPES[t].multiple || !present.has(t)).map(t => h('button', {
      type: 'button', class: 'chip',
      onclick: () => {
        const s = newSection(t);
        if (s.items) { const it = newItem(s); s.items.push(it); state.openItems.add(it.id); }
        state.resume.sections.push(s);
        touch(); renderEditor();
        requestAnimationFrame(() => document.querySelector(`[data-id="${s.id}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
      },
    }, icon('plus'), SECTION_TYPES[t].label))));
}

// ======================= Visualização =======================

function renderPreview() {
  if (!state.resume) return;
  const page = $('#page');
  page.innerHTML = renderResume(buildDoc(state.resume));
  fitPreview();
}

function fitPreview() {
  const wrap = $('#page-wrap'), page = $('#page');
  const cv = page.firstElementChild;
  if (!cv || !wrap.clientWidth) return;
  const avail = $('#preview-scroll').clientWidth - 32;
  const scale = Math.min(1.25, avail / cv.offsetWidth);
  cv.style.minHeight = '';
  const pages = Math.max(1, Math.ceil((cv.offsetHeight - 2) / A4_PX));
  cv.style.minHeight = `${pages * 297}mm`;
  page.style.transform = `scale(${scale})`;
  wrap.style.width = `${cv.offsetWidth * scale}px`;
  wrap.style.height = `${cv.offsetHeight * scale}px`;
  wrap.querySelectorAll('.page-break').forEach(el => el.remove());
  for (let i = 1; i < pages; i++) {
    wrap.append(h('div', { class: 'page-break', style: `top:${i * A4_PX * scale}px` }, h('span', {}, `Página ${i + 1}`)));
  }
  $('#page-count').textContent = pages === 1 ? '1 página' : `${pages} páginas`;
}

const SWATCHES = ['#2f5d8a', '#1f7a8c', '#2e7d5b', '#5b8c3a', '#b5651d', '#b23a48', '#8e3b8e', '#5a4fcf', '#3d4451', '#111111', '#7d6b5d', '#c08a2b'];

function renderStylePanel() {
  const r = state.resume;
  const st = r.style;
  const tab = state.styleTab;
  document.querySelectorAll('.style-tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
  const panelEl = $('#style-panel');
  const set = (k, v, rerender = true) => { st[k] = v; touch(); if (rerender) renderStylePanel(); };
  const opt = (label, active, onclick, attrs = {}) => h('button', { type: 'button', class: `opt ${active ? 'active' : ''}`, onclick, ...attrs }, label);
  const slider = (label, key, min, max, step, fmt) => {
    const out = h('output', {}, fmt(st[key]));
    return h('label', { class: 'slider' }, h('span', {}, label), h('input', {
      type: 'range', min, max, step, value: st[key],
      oninput: e => { st[key] = Number(e.target.value); out.textContent = fmt(st[key]); touch(); },
    }), out);
  };

  let content;
  if (tab === 'templates') {
    const sample = r.personal.firstName || r.sections.some(s => s.items?.length) ? r : { ...EXAMPLE(), style: st };
    content = h('div', { class: 'tpl-strip' }, Object.entries(TEMPLATES).map(([id, t]) => {
      const thumb = h('div', { class: 'thumb' });
      thumb.innerHTML = renderResume(buildDoc({ ...sample, style: { ...st, template: id } }));
      return h('button', {
        type: 'button', class: `tpl ${st.template === id ? 'active' : ''}`, 'aria-pressed': String(st.template === id),
        onclick: () => set('template', id),
      }, h('div', { class: 'thumb-box' }, thumb), h('span', {}, t.label, t.ats ? h('em', { class: 'ats-tag' }, 'ATS') : null));
    }));
  } else if (tab === 'font') {
    content = h('div', { class: 'opts-col' },
      h('div', { class: 'opts' }, Object.entries(FONTS).map(([id, f]) => opt(f.label, st.font === id, () => set('font', id), { style: `font-family:${f.css}` }))),
      slider('Tamanho do texto', 'fontSize', 8.5, 12, 0.5, v => `${String(v).replace('.', ',')} pt`));
  } else if (tab === 'spacing') {
    content = h('div', { class: 'opts-col' },
      slider('Altura das linhas', 'lineHeight', 1.15, 1.8, 0.05, v => v.toFixed(2).replace('.', ',')),
      slider('Espaço entre seções', 'spacing', 0.4, 2, 0.1, v => `${Math.round(v * 100)}%`));
  } else if (tab === 'layout') {
    const levelOpts = { auto: 'Do modelo', bar: 'Barra', dots: 'Pontos', text: 'Texto', none: 'Ocultar' };
    const shapes = { round: 'Redonda', rounded: 'Arredondada', square: 'Quadrada' };
    content = h('div', { class: 'opts-col' },
      h('div', { class: 'opt-group' }, h('span', {}, 'Níveis de competência e idioma'),
        h('div', { class: 'opts' }, Object.entries(levelOpts).map(([k, l]) => opt(l, st.levelStyle === k, () => set('levelStyle', k))))),
      h('div', { class: 'opt-group' }, h('span', {}, 'Formato da foto'),
        h('div', { class: 'opts' }, Object.entries(shapes).map(([k, l]) => opt(l, st.photoShape === k, () => set('photoShape', k))))),
      h('div', { class: 'opt-group' }, h('span', {}, 'Idioma dos títulos e datas'),
        h('div', { class: 'opts' }, Object.entries(RESUME_LANGS).map(([k, l]) => opt(l, st.lang === k, () => set('lang', k))))));
  } else {
    const custom = h('input', { type: 'color', value: st.color, 'aria-label': 'Cor personalizada', oninput: e => set('color', e.target.value, false), onchange: () => renderStylePanel() });
    content = h('div', { class: 'swatches' },
      SWATCHES.map(c => h('button', {
        type: 'button', class: `swatch ${st.color.toLowerCase() === c ? 'active' : ''}`, style: `--c:${c}`, 'aria-label': `Cor ${c}`,
        onclick: () => set('color', c),
      })),
      h('label', { class: 'swatch custom', title: 'Outra cor' }, custom, icon('plus')));
  }
  panelEl.replaceChildren(content);
  if (tab === 'templates') {
    requestAnimationFrame(() => panelEl.querySelector('.tpl.active')?.scrollIntoView({ inline: 'center', block: 'nearest' }));
  }
}

// ======================= Exportar =======================

async function printPdf() {
  const doc = buildDoc(state.resume);
  const root = $('#print-root');
  root.innerHTML = renderResume(doc);
  const prevTitle = document.title;
  document.title = fileBase(doc);
  await document.fonts.ready;
  await Promise.all([...root.querySelectorAll('img')].map(img => img.decode().catch(() => {})));
  const restore = () => { document.title = prevTitle; root.replaceChildren(); window.removeEventListener('afterprint', restore); };
  window.addEventListener('afterprint', restore);
  window.print();
}

async function shareOrDownload(blob, name) {
  const file = new File([blob], name, { type: blob.type });
  if (navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], title: name }); return; } catch (e) { if (e.name === 'AbortError') return; }
  }
  download(blob, name);
}

function openExport() {
  const r = state.resume;
  const doc = buildDoc(r);
  const base = fileBase(doc);
  const meta = { title: doc.name ? `${doc.L.resume} — ${doc.name}` : doc.L.resume, author: doc.name };
  const css = () => fetch('css/resume.css').then(res => res.text());
  const canShare = !!navigator.canShare;

  const options = [
    { icon: 'file', title: 'PDF', badge: 'Visual', desc: 'Igual à visualização, com o modelo escolhido. Na janela de impressão, escolha "Salvar como PDF".',
      run: () => printPdf() },
    { icon: 'target', title: 'PDF para ATS', badge: 'Recomendado', desc: 'Uma coluna, texto puro e sem foto: o formato mais seguro para candidaturas online.',
      run: () => download(buildPdf(doc, meta), `${base}-ATS.pdf`),
      share: () => shareOrDownload(buildPdf(doc, meta), `${base}-ATS.pdf`) },
    { icon: 'file', title: 'Word (.docx)', desc: 'Editável no Word, LibreOffice ou Google Docs. Também é bem lido por ATS.',
      run: () => download(buildDocx(doc, meta), `${base}.docx`),
      share: () => shareOrDownload(buildDocx(doc, meta), `${base}.docx`) },
    { icon: 'list', title: 'Texto (.txt)', desc: 'Texto simples para colar em formulários de vagas.',
      run: () => download(new Blob([toText(doc)], { type: 'text/plain;charset=utf-8' }), `${base}.txt`),
      extra: { label: 'Copiar texto', action: async () => { await navigator.clipboard.writeText(toText(doc)); toast('Texto copiado'); } } },
    { icon: 'edit', title: 'Markdown (.md)', desc: 'Para GitHub, sites pessoais e editores de notas.',
      run: () => download(new Blob([toMarkdown(doc)], { type: 'text/markdown;charset=utf-8' }), `${base}.md`) },
    { icon: 'eye', title: 'Página HTML', desc: 'Um único arquivo que abre em qualquer navegador.',
      run: async () => download(new Blob([toHtml(doc, renderResume(doc), await css())], { type: 'text/html;charset=utf-8' }), `${base}.html`) },
    { icon: 'copy', title: 'JSON Resume', desc: 'Padrão aberto (jsonresume.org) para levar seus dados a outras ferramentas.',
      run: () => download(new Blob([toJsonResume(r)], { type: 'application/json' }), `${base}.resume.json`) },
    { icon: 'download', title: 'Backup do app', desc: 'Tudo, inclusive modelo, cores e foto. Importe de volta em qualquer aparelho.',
      run: () => exportBackup([r]) },
  ];

  const dlg = h('dialog', { class: 'dlg export' },
    h('div', { class: 'dlg-head' }, h('h2', {}, 'Baixar currículo'), iconBtn('close', 'Fechar', () => close())),
    h('div', { class: 'export-list' }, options.map(o => h('div', { class: 'export-opt' },
      h('button', {
        type: 'button', class: 'export-main',
        onclick: async () => { try { await o.run(); } catch (e) { console.error(e); toast('Falha ao exportar.'); } if (o.title !== 'PDF') toast(`${o.title} gerado`); },
      }, icon(o.icon), h('span', { class: 'export-text' },
        h('strong', {}, o.title, o.badge ? h('em', { class: `badge ${o.badge === 'Recomendado' ? 'good' : ''}` }, o.badge) : null),
        h('small', {}, o.desc))),
      o.extra ? h('button', { type: 'button', class: 'btn ghost small', onclick: o.extra.action }, o.extra.label) : null,
      canShare && o.share ? iconBtn('share', `Compartilhar ${o.title}`, o.share) : null))),
    h('p', { class: 'muted small' }, 'Os arquivos são gerados no aparelho, sem internet e sem enviar seus dados.'));
  const close = () => { dlg.close(); dlg.remove(); };
  dlg.addEventListener('cancel', close);
  dlg.addEventListener('click', e => { if (e.target === dlg) close(); });
  document.body.append(dlg);
  dlg.showModal();
}

// ======================= Análise ATS =======================

function openAts() {
  const r = state.resume;
  const doc = buildDoc(r);
  const res = analyze(r, doc);
  const color = res.score >= 80 ? 'good' : res.score >= 55 ? 'mid' : 'bad';
  const kwOut = h('div', { class: 'kw-out' });
  const job = h('textarea', { class: 'input', rows: 5, placeholder: 'Cole aqui o texto da vaga para comparar as palavras-chave…' });
  const runKw = () => {
    if (!job.value.trim()) { kwOut.replaceChildren(); return; }
    const m = matchKeywords(doc, job.value);
    kwOut.replaceChildren(
      h('p', {}, h('strong', {}, `${m.pct}%`), ' das palavras-chave da vaga aparecem no seu currículo.'),
      m.missing.length ? h('div', {}, h('span', { class: 'kw-label' }, 'Faltando (inclua as que forem verdade):'),
        h('div', { class: 'kw' }, m.missing.map(k => h('span', { class: 'kw-miss' }, k)))) : null,
      m.found.length ? h('div', {}, h('span', { class: 'kw-label' }, 'Encontradas:'),
        h('div', { class: 'kw' }, m.found.map(k => h('span', { class: 'kw-hit' }, k)))) : null);
  };
  job.addEventListener('input', debounce(runKw, 250));

  const dlg = h('dialog', { class: 'dlg ats' },
    h('div', { class: 'dlg-head' }, h('h2', {}, 'Análise ATS'), iconBtn('close', 'Fechar', () => close())),
    h('div', { class: 'score-row' },
      h('div', { class: `score ${color}`, style: `--p:${res.score}` }, h('span', {}, res.score)),
      h('p', {}, 'ATS são os sistemas que empresas usam para filtrar currículos. Esta nota mede o que costuma ajudar ou atrapalhar a leitura e a triagem. ',
        h('span', { class: 'muted' }, 'A análise é feita no aparelho.'))),
    h('ul', { class: 'checks' }, res.checks.map(c => h('li', { class: c.level },
      icon(c.ok ? 'check' : c.level === 'info' ? 'info' : 'close'),
      h('div', {}, h('strong', {}, c.text), !c.ok && c.tip ? h('small', {}, c.tip) : null)))),
    h('h3', {}, 'Comparar com uma vaga'),
    job, kwOut);
  const close = () => { dlg.close(); dlg.remove(); };
  dlg.addEventListener('cancel', close);
  document.body.append(dlg);
  dlg.showModal();
}

// ======================= Inicialização =======================

function bindChrome() {
  $('#btn-new').onclick = () => createResume(false);
  $('#btn-example').onclick = () => createResume(true);
  $('#btn-import').onclick = () => importFlow(false);
  $('#btn-backup-all').onclick = async () => {
    const all = await store.listResumes();
    if (!all.length) return toast('Nenhum currículo para salvar.');
    exportBackup(all);
  };
  $('#btn-back').onclick = () => { if (state.previewing) openResume(state.resume.id); else location.hash = ''; };
  $('#doc-title').onclick = async () => {
    await renameResume(state.resume);
    $('#doc-title').textContent = state.resume.name;
  };
  $('#btn-download').onclick = openExport;
  $('#btn-ats').onclick = openAts;
  $('#btn-preview-fab').onclick = () => openResume(state.resume.id, true);
  $('#btn-doc-menu').onclick = e => popMenu(e.currentTarget, [
    { label: 'Análise ATS', icon: 'target', action: openAts },
    { label: 'Renomear', icon: 'pen', action: () => $('#doc-title').click() },
    { label: 'Duplicar', icon: 'copy', action: async () => openResume((await duplicateResume(state.resume)).id) },
    { label: 'Importar dados neste currículo', icon: 'upload', action: () => importFlow(true) },
    { label: 'Baixar backup (.json)', icon: 'download', action: () => exportBackup([state.resume]) },
    { label: 'Excluir currículo', icon: 'trash', danger: true, action: async () => { if (await removeResume(state.resume)) { state.resume = null; location.hash = ''; } } },
  ]);
  document.querySelectorAll('.style-tabs button').forEach(b => {
    b.onclick = () => { state.styleTab = b.dataset.tab; renderStylePanel(); };
  });
  new ResizeObserver(() => fitPreview()).observe($('#preview-scroll'));
  window.addEventListener('hashchange', route);
  window.addEventListener('beforeunload', () => saveSoon.flush());
  document.addEventListener('visibilitychange', () => { if (document.hidden) saveSoon.flush(); });
}

async function init() {
  document.querySelectorAll('[data-app-name]').forEach(el => { el.textContent = APP_NAME; });
  bindChrome();
  store.persist();
  await route();
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('sw.js').catch(err => console.warn('Service worker:', err));
  }
}

init();
