// Importação: backup do app, JSON Resume e o arquivo de dados do LinkedIn (.zip).
// O LinkedIn permite baixar seus dados em Configurações → Privacidade de dados →
// "Obter uma cópia dos seus dados". O ZIP é lido aqui mesmo, sem enviar nada.

import { newResume, newSection, newItem, normalize, uid } from './model.js';
import { fromJsonResume } from './export/text.js';

export async function importFile(file) {
  const name = file.name.toLowerCase();
  if (name.endsWith('.zip')) return [await fromLinkedInZip(await file.arrayBuffer())];
  const data = JSON.parse(await file.text());
  if (data?.app === 'curriculo' && Array.isArray(data.resumes)) return data.resumes.map(r => normalize({ ...r, id: uid() }));
  if (data?.personal && data?.sections) return [normalize({ ...data, id: uid() })];
  if (data?.basics || data?.work) return [fromJsonResume(data)];
  throw new Error('Formato não reconhecido. Use um backup deste app, um JSON Resume ou o ZIP de dados do LinkedIn.');
}

// ===== ZIP (leitura) =====
async function unzip(buf) {
  const view = new DataView(buf);
  let eocd = -1;
  for (let i = buf.byteLength - 22; i >= Math.max(0, buf.byteLength - 65557); i--) {
    if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('Arquivo ZIP inválido.');
  const count = view.getUint16(eocd + 10, true);
  let p = view.getUint32(eocd + 16, true);
  const files = {};
  const dec = new TextDecoder();
  for (let n = 0; n < count; n++) {
    if (view.getUint32(p, true) !== 0x02014b50) break;
    const method = view.getUint16(p + 10, true);
    const size = view.getUint32(p + 20, true);
    const nameLen = view.getUint16(p + 28, true), extraLen = view.getUint16(p + 30, true), commentLen = view.getUint16(p + 32, true);
    const local = view.getUint32(p + 42, true);
    const name = dec.decode(new Uint8Array(buf, p + 46, nameLen));
    p += 46 + nameLen + extraLen + commentLen;
    if (!name.toLowerCase().endsWith('.csv')) continue;
    const start = local + 30 + view.getUint16(local + 26, true) + view.getUint16(local + 28, true);
    const raw = new Uint8Array(buf, start, size);
    let data;
    if (method === 0) data = raw;
    else if (method === 8) data = new Uint8Array(await new Response(new Blob([raw]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).arrayBuffer());
    else continue;
    files[name.split('/').pop()] = dec.decode(data).replace(/^﻿/, '');
  }
  return files;
}

// CSV com aspas e quebras de linha dentro dos campos.
function parseCsv(text) {
  const rows = [];
  let row = [], field = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') q = false;
      else field += c;
    } else if (c === '"') q = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  // O LinkedIn às vezes põe notas antes do cabeçalho; achamos a linha do cabeçalho.
  const headerIdx = rows.findIndex(r => r.length > 1);
  if (headerIdx < 0) return [];
  const head = rows[headerIdx].map(h => h.trim());
  return rows.slice(headerIdx + 1).filter(r => r.some(Boolean)).map(r => Object.fromEntries(head.map((h, i) => [h, (r[i] || '').trim()])));
}

const MONTHS = { jan: 1, feb: 2, fev: 2, mar: 3, apr: 4, abr: 4, may: 5, mai: 5, jun: 6, jul: 7, aug: 8, ago: 8, sep: 9, set: 9, oct: 10, out: 10, nov: 11, dec: 12, dez: 12 };
function liDate(v) {
  if (!v) return '';
  const y = (v.match(/\d{4}/) || [])[0];
  if (!y) return '';
  const m = MONTHS[(v.match(/[A-Za-z]{3}/) || [''])[0].toLowerCase()];
  return m ? `${y}-${String(m).padStart(2, '0')}` : y;
}

async function fromLinkedInZip(buf) {
  const files = await unzip(buf);
  const csv = name => {
    const key = Object.keys(files).find(k => k.toLowerCase() === name.toLowerCase());
    return key ? parseCsv(files[key]) : [];
  };
  const profile = csv('Profile.csv')[0];
  if (!profile && !csv('Positions.csv').length) throw new Error('Não encontrei os dados do LinkedIn neste ZIP.');

  const r = newResume();
  r.sections = [];
  const P = profile || {};
  const email = csv('Email Addresses.csv').find(e => e.Primary === 'Yes') || csv('Email Addresses.csv')[0];
  const phone = csv('PhoneNumbers.csv')[0];
  Object.assign(r.personal, {
    firstName: P['First Name'] || '', lastName: P['Last Name'] || '', role: P.Headline || '',
    city: P['Geo Location'] || '', postalCode: P['Zip Code'] || '',
    email: email?.['Email Address'] || '', phone: phone?.Number || '',
  });
  r.name = `LinkedIn — ${[P['First Name'], P['Last Name']].filter(Boolean).join(' ') || 'importado'}`;
  const site = (P.Websites || '').match(/https?:\/\/[^\s,\]]+/);
  if (site) r.personal.extras.push({ id: uid(), key: 'website', value: site[0] });

  const section = (type, rows, map, title) => {
    if (!rows.length) return;
    const s = newSection(type);
    if (title) s.title = title;
    s.items = rows.map(x => ({ ...newItem(s), ...map(x) }));
    r.sections.push(s);
  };
  const dated = (x, a = 'Started On', b = 'Finished On') => ({ start: liDate(x[a]), end: liDate(x[b]), current: !!x[a] && !x[b] });

  if (P.Summary) { const s = newSection('summary'); s.content = P.Summary; r.sections.push(s); }
  section('experience', csv('Positions.csv'), x => ({ title: x.Title, org: x['Company Name'], city: x.Location, description: x.Description, ...dated(x) }));
  section('education', csv('Education.csv'), x => ({ title: x['Degree Name'], org: x['School Name'], description: x.Notes, ...dated(x, 'Start Date', 'End Date'), current: false }));
  section('skills', csv('Skills.csv'), x => ({ name: x.Name, level: 0 }));
  const prof = v => /native|nativ|bilingual|bil[ií]ng/i.test(v) ? 5 : /full professional|completo/i.test(v) ? 5 : /professional working|profissional/i.test(v) ? 4 : /limited|limitad/i.test(v) ? 3 : /elementary|elementar/i.test(v) ? 2 : 0;
  section('languages', csv('Languages.csv'), x => ({ name: x.Name, level: prof(x.Proficiency || '') }));
  section('certificates', csv('Certifications.csv'), x => ({ title: x.Name, org: x.Authority, start: liDate(x['Started On']) }));
  section('courses', csv('Courses.csv'), x => ({ title: x.Name }));
  section('achievements', csv('Honors.csv'), x => ({ title: x.Title, description: x.Description, start: liDate(x['Issued On']) }));
  section('extracurricular', csv('Volunteering.csv'), x => ({ title: x.Role, org: x['Company Name'], description: x.Description, ...dated(x) }));
  section('custom', csv('Projects.csv'), x => ({ title: x.Title, org: x.Url, description: x.Description, ...dated(x) }), 'Projetos');
  return normalize(r);
}
