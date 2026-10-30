// Utilitários de interface: criação de elementos, ícones, arrastar para reordenar,
// diálogos, avisos e downloads.

export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'html') el.innerHTML = v;
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else if (k === 'value') el.value = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k in el && typeof v !== 'string') el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

// Ícones de traço desenhados para o app (viewBox 24).
const PATHS = {
  back: 'M15 5l-7 7 7 7',
  plus: 'M12 5v14M5 12h14',
  close: 'M6 6l12 12M18 6L6 18',
  more: 'M12 5.5v.01M12 12v.01M12 18.5v.01',
  chevron: 'M6 9l6 6 6-6',
  drag: 'M9 6v.01M15 6v.01M9 12v.01M15 12v.01M9 18v.01M15 18v.01',
  download: 'M12 4v11M7 10l5 5 5-5M5 20h14',
  upload: 'M12 20V9M7 14l5-5 5 5M5 4h14',
  file: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
  preview: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4M14 3v5h5V11M16.5 13a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zM19 19.5l2 2',
  camera: 'M4 8h3l2-3h6l2 3h3v11H4zM12 10.5a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
  trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13',
  copy: 'M8 8h11v12H8zM5 16V4h11',
  edit: 'M4 20h4L19 9l-4-4L4 16zM14 6l4 4',
  check: 'M5 12l5 5 9-10',
  cloud: 'M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9zM9 13l2 2 4-4',
  cloudBusy: 'M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z',
  templates: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  font: 'M3 19l5-14 5 14M5 14h6M15 19v-5a3 3 0 0 1 6 0v5M21 15.5c-1.5-1-6-1-6 1.5s4 2 6 0',
  spacing: 'M4 6h10M4 12h10M4 18h10M19 4v16M17 6l2-2 2 2M17 18l2 2 2-2',
  layout: 'M4 4h16v16H4zM10 4v16',
  color: 'M5 13l7-8 7 8-7 7zM5 13h14M20 17s1.5 2 1.5 3a1.5 1.5 0 0 1-3 0c0-1 1.5-3 1.5-3',
  target: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 12v.01',
  linkedin: 'M4 4h16v16H4zM8 10v7M8 7v.01M12 17v-4a2 2 0 0 1 4 0v4M12 10v7',
  share: 'M12 15V3M8 7l4-4 4 4M5 12v8h14v-8',
  eyeOff: 'M3 3l18 18M10.6 6.1A10 10 0 0 1 12 6c6 0 10 6 10 6a17 17 0 0 1-3 3.5M6.5 7.5C3.8 9.3 2 12 2 12s4 6 10 6a9.6 9.6 0 0 0 4.5-1.1',
  up: 'M12 19V5M6 11l6-6 6 6',
  down: 'M12 5v14M6 13l6 6 6-6',
  pen: 'M3 21c3-1 5-4 7-4s1 3 4 3 4-3 7-5M15 4l5 5-8 8H7v-5z',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5v.01',
  bold: 'M7 4h6a4 4 0 0 1 0 8H7zM7 12h7a4 4 0 0 1 0 8H7z',
  italic: 'M10 4h8M6 20h8M14 4l-4 16',
  list: 'M9 6h11M9 12h11M9 18h11M4.5 6v.01M4.5 12v.01M4.5 18v.01',
  sun: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
};

export function icon(name, cls = '') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('class', `ico ${cls}`);
  svg.setAttribute('aria-hidden', 'true');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', PATHS[name] || '');
  svg.append(p);
  return svg;
}

export const iconBtn = (name, label, onclick, cls = '') =>
  h('button', { type: 'button', class: `icon-btn ${cls}`, title: label, 'aria-label': label, onclick }, icon(name));

// Reordenação por arrasto (mouse e toque), pela alça indicada.
export function sortable(container, { item, handle, onEnd }) {
  container.addEventListener('pointerdown', e => {
    const hd = e.target.closest(handle);
    if (!hd || !container.contains(hd)) return;
    const el = hd.closest(item);
    if (!el || el.parentElement !== container) return;
    e.preventDefault();
    const startY = e.clientY;
    const rect = el.getBoundingClientRect();
    el.classList.add('dragging');
    hd.setPointerCapture(e.pointerId);
    let moved = false;

    const grab = startY - rect.top;
    const move = ev => {
      if (!moved && Math.abs(ev.clientY - startY) < 4) return;
      moved = true;
      el.style.transform = '';
      const siblings = [...container.children].filter(c => c !== el && c.matches(item));
      const before = siblings.filter(s => el.compareDocumentPosition(s) & Node.DOCUMENT_POSITION_PRECEDING);
      const after = siblings.filter(s => !before.includes(s));
      const above = before.find(s => { const r = s.getBoundingClientRect(); return ev.clientY < r.top + r.height / 2; });
      if (above) above.before(el);
      else {
        const below = after.filter(s => { const r = s.getBoundingClientRect(); return ev.clientY > r.top + r.height / 2; }).pop();
        if (below) below.after(el);
      }
      el.style.transform = `translateY(${ev.clientY - grab - el.getBoundingClientRect().top}px)`;
      // Rola a área perto das bordas.
      const scroller = container.closest('.scroll') || document.scrollingElement;
      const sr = scroller.getBoundingClientRect();
      const top = Math.max(sr.top, 0), bottom = Math.min(sr.bottom, innerHeight);
      if (ev.clientY < top + 60) scroller.scrollBy(0, -12);
      else if (ev.clientY > bottom - 60) scroller.scrollBy(0, 12);
    };
    const up = () => {
      hd.removeEventListener('pointermove', move);
      hd.removeEventListener('pointerup', up);
      hd.removeEventListener('pointercancel', up);
      el.classList.remove('dragging');
      el.style.transform = '';
      if (moved) onEnd([...container.children].filter(c => c.matches(item)).map(c => c.dataset.id));
    };
    hd.addEventListener('pointermove', move);
    hd.addEventListener('pointerup', up);
    hd.addEventListener('pointercancel', up);
  });
}

// Menu suspenso simples, ancorado num botão.
export function popMenu(anchor, items) {
  document.querySelector('.pop-menu')?.remove();
  const menu = h('div', { class: 'pop-menu', role: 'menu' },
    items.filter(Boolean).map(it => h('button', {
      type: 'button', role: 'menuitem', class: it.danger ? 'danger' : '',
      onclick: () => { close(); it.action(); },
    }, it.icon ? icon(it.icon) : null, it.label)));
  document.body.append(menu);
  const r = anchor.getBoundingClientRect();
  const mw = menu.offsetWidth, mh = menu.offsetHeight;
  menu.style.left = `${Math.max(8, Math.min(r.right - mw, innerWidth - mw - 8))}px`;
  menu.style.top = `${r.bottom + mh + 8 > innerHeight ? Math.max(8, r.top - mh - 4) : r.bottom + 4}px`;
  const close = () => { menu.remove(); document.removeEventListener('pointerdown', outside, true); };
  const outside = e => { if (!menu.contains(e.target) && !anchor.contains(e.target)) close(); };
  setTimeout(() => document.addEventListener('pointerdown', outside, true));
  menu.querySelector('button')?.focus();
  menu.addEventListener('keydown', e => { if (e.key === 'Escape') { close(); anchor.focus(); } });
}

// Diálogos nativos (<dialog>) para confirmar e pedir um texto.
export function confirmDialog(message, { ok = 'Confirmar', danger = false } = {}) {
  return new Promise(resolve => {
    const dlg = h('dialog', { class: 'dlg small' },
      h('p', {}, message),
      h('div', { class: 'dlg-actions' },
        h('button', { type: 'button', class: 'btn ghost', onclick: () => done(false) }, 'Cancelar'),
        h('button', { type: 'button', class: `btn ${danger ? 'danger' : 'primary'}`, onclick: () => done(true) }, ok)));
    const done = v => { dlg.close(); dlg.remove(); resolve(v); };
    dlg.addEventListener('cancel', () => done(false));
    document.body.append(dlg);
    dlg.showModal();
  });
}

export function promptDialog(label, value = '') {
  return new Promise(resolve => {
    const input = h('input', { type: 'text', value, class: 'input' });
    const form = h('form', { method: 'dialog', onsubmit: e => { e.preventDefault(); done(input.value.trim()); } },
      h('label', { class: 'field' }, h('span', {}, label), input),
      h('div', { class: 'dlg-actions' },
        h('button', { type: 'button', class: 'btn ghost', onclick: () => done(null) }, 'Cancelar'),
        h('button', { type: 'submit', class: 'btn primary' }, 'Salvar')));
    const dlg = h('dialog', { class: 'dlg small' }, form);
    const done = v => { dlg.close(); dlg.remove(); resolve(v); };
    dlg.addEventListener('cancel', () => done(null));
    document.body.append(dlg);
    dlg.showModal();
    input.select();
  });
}

let toastTimer;
export function toast(msg) {
  let el = document.getElementById('toast');
  if (!el) { el = h('div', { id: 'toast', role: 'status' }); document.body.append(el); }
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
}

export function download(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = h('a', { href: url, download: filename });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}

export function pickFile(accept) {
  return new Promise(resolve => {
    const input = h('input', { type: 'file', accept, onchange: () => resolve(input.files[0] || null) });
    input.click();
  });
}

// Lê uma imagem, recorta o centro em quadrado e reduz (fotos leves no armazenamento).
export async function loadPhoto(file, size = 480) {
  const bmp = await createImageBitmap(file);
  const side = Math.min(bmp.width, bmp.height);
  const c = h('canvas', { width: size, height: size });
  c.getContext('2d').drawImage(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side, 0, 0, size, size);
  return c.toDataURL('image/jpeg', 0.86);
}

export const debounce = (fn, ms) => {
  let t;
  const d = (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
  d.flush = (...a) => { clearTimeout(t); fn(...a); };
  return d;
};
