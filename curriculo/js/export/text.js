// Exportações em texto: TXT (ATS), Markdown, HTML independente e JSON Resume.

import { plain } from '../doc.js';
import { newResume, newSection, newItem, normalize, uid } from '../model.js';

const levelSuffix = (doc, it) => it.level && doc.style.levelStyle !== 'none' ? ` (${it.levelText})` : '';

// Texto puro, uma coluna, sem enfeites: cole direto em formulários de candidatura.
export function toText(doc) {
  const out = [];
  if (doc.name) out.push(doc.name.toUpperCase());
  const role = doc.title !== doc.L.resume ? doc.title : doc.role;
  if (role) out.push(role);
  for (const c of doc.contact) out.push(['email', 'phone', 'address', 'website', 'linkedin'].includes(c.key) ? c.value : `${c.label}: ${c.value}`);
  const blocks = bs => bs.forEach(b => b.type === 'p' ? out.push(plain(b.runs)) : b.items.forEach(i => out.push(`- ${plain(i)}`)));
  for (const s of doc.sections) {
    out.push('');
    if (s.title) out.push(s.title.toUpperCase());
    if (s.kind === 'text') blocks(s.blocks);
    else if (s.kind === 'entries') {
      s.items.forEach((it, i) => {
        if (i) out.push('');
        out.push([it.title, it.dates].filter(Boolean).join(' | '));
        const meta = [it.org, it.city].filter(Boolean).join(', ');
        if (meta) out.push(meta);
        blocks(it.blocks);
      });
    } else if (s.kind === 'levels' || s.kind === 'tags') {
      s.items.forEach(it => out.push(`- ${it.name}${levelSuffix(doc, it)}`));
    } else if (s.kind === 'references') {
      s.items.forEach(r => out.push([r.title, r.org, r.city, r.phone, r.email].filter(Boolean).join(' | ')));
    } else if (s.kind === 'signature') {
      if (s.place) out.push(s.place);
      if (s.name) out.push(s.name);
    }
  }
  return out.join('\n').replace(/\n{3,}/g, '\n\n').trim() + '\n';
}

const mdRuns = runs => runs.map(r => r.bold ? `**${r.text}**` : r.italic ? `*${r.text}*` : r.text).join('');

export function toMarkdown(doc) {
  const out = [];
  if (doc.name) out.push(`# ${doc.name}`, '');
  const role = doc.title !== doc.L.resume ? doc.title : doc.role;
  if (role) out.push(`**${role}**`, '');
  if (doc.contact.length) {
    out.push(doc.contact.map(c => c.href && !c.href.startsWith('tel:') ? `[${c.value}](${c.href})` : c.value).join(' · '), '');
  }
  const blocks = bs => bs.forEach(b => {
    if (b.type === 'p') out.push(mdRuns(b.runs), '');
    else { b.items.forEach(i => out.push(`- ${mdRuns(i)}`)); out.push(''); }
  });
  for (const s of doc.sections) {
    if (s.title) out.push(`## ${s.title}`, '');
    if (s.kind === 'text') blocks(s.blocks);
    else if (s.kind === 'entries') {
      for (const it of s.items) {
        out.push(`### ${it.title || it.org}`);
        const meta = [it.title ? it.org : '', it.city, it.dates].filter(Boolean).join(' · ');
        if (meta) out.push(`*${meta}*`);
        out.push('');
        blocks(it.blocks);
      }
    } else if (s.kind === 'levels' || s.kind === 'tags') {
      s.items.forEach(it => out.push(`- ${it.name}${levelSuffix(doc, it)}`));
      out.push('');
    } else if (s.kind === 'references') {
      s.items.forEach(r => out.push(`- **${r.title}** — ${[r.org, r.city, r.phone, r.email].filter(Boolean).join(' · ')}`));
      out.push('');
    } else if (s.kind === 'signature') {
      out.push([s.place, s.name].filter(Boolean).join('  \n'), '');
    }
  }
  return out.join('\n').replace(/\n{3,}/g, '\n\n').trim() + '\n';
}

export function toHtml(doc, articleHtml, css) {
  return `<!doctype html>
<html lang="${doc.lang}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${(doc.name || doc.title).replace(/</g, '&lt;')}</title>
<style>
${css}
body { margin: 0; background: #e9ebef; display: flex; justify-content: center; padding: 24px 0; }
.cv { box-shadow: 0 2px 16px rgba(0,0,0,.12); }
@page { size: A4; margin: 0; }
@media print { body { background: none; padding: 0; display: block; } .cv { box-shadow: none; } }
</style></head><body>
${articleHtml}
</body></html>
`;
}

// ===== JSON Resume (https://jsonresume.org), padrão aberto =====

const descToJr = (text = '') => {
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  const highlights = lines.filter(l => /^[-•*·]\s+/.test(l)).map(l => l.replace(/^[-•*·]\s+/, ''));
  const summary = lines.filter(l => !/^[-•*·]\s+/.test(l)).join('\n');
  return { ...(summary && { summary }), ...(highlights.length && { highlights }) };
};
const jrToDesc = o => [o.summary || o.description || '', ...(o.highlights || []).map(h => `- ${h}`)].filter(Boolean).join('\n');
const dateOf = i => ({ ...(i.start && { startDate: i.start }), ...(!i.current && i.end && { endDate: i.end }) });
const ym = d => (d || '').slice(0, 7);

export function toJsonResume(resume) {
  const p = resume.personal;
  const ex = Object.fromEntries((p.extras || []).map(e => [e.key, e.value]));
  const sec = type => resume.sections.filter(s => s.type === type).flatMap(s => s.items || []);
  const summary = resume.sections.find(s => s.type === 'summary')?.content || '';
  const url = v => !v ? undefined : /^https?:\/\//.test(v) ? v : `https://${v}`;

  const jr = {
    $schema: 'https://raw.githubusercontent.com/jsonresume/resume-schema/v1.0.0/schema.json',
    basics: {
      name: [p.firstName, p.lastName].filter(Boolean).join(' '),
      label: p.role || undefined, image: p.photo || undefined,
      email: p.email || undefined, phone: p.phone || undefined, url: url(ex.website),
      summary: summary || undefined,
      location: { address: p.address || undefined, postalCode: p.postalCode || undefined, city: p.city || undefined },
      profiles: ex.linkedin ? [{ network: 'LinkedIn', url: url(ex.linkedin) }] : [],
    },
    work: [...sec('experience'), ...sec('internships')].map(i => ({
      name: i.org, position: i.title, location: i.city || undefined, ...dateOf(i), ...descToJr(i.description),
    })),
    volunteer: sec('extracurricular').map(i => ({ organization: i.org, position: i.title, ...dateOf(i), ...descToJr(i.description) })),
    education: [...sec('education'), ...sec('courses')].map(i => ({
      institution: i.org, area: i.title, ...dateOf(i), ...descToJr(i.description),
    })),
    awards: sec('achievements').map(i => ({ title: i.title, ...(i.start && { date: i.start }), summary: i.description || undefined })),
    certificates: sec('certificates').map(i => ({ name: i.title, issuer: i.org || undefined, ...(i.start && { date: i.start }) })),
    skills: sec('skills').map(i => ({ name: i.name, ...(i.level && { level: String(i.level) }) })),
    languages: sec('languages').map(i => ({ language: i.name, ...(i.level && { fluency: String(i.level) }) })),
    interests: sec('hobbies').map(i => ({ name: i.name })),
    references: sec('references').map(i => ({ name: i.title, reference: [i.org, i.phone, i.email].filter(Boolean).join(' | ') })),
    meta: { version: 'v1.0.0', lastModified: new Date(resume.updatedAt).toISOString() },
  };
  return JSON.stringify(jr, null, 2);
}

const LEVEL_WORDS = [
  [/nativ|native|mother/i, 5], [/fluent|fluente|fluido|c2/i, 5], [/avan|advanced|c1/i, 4],
  [/expert|especialista|master/i, 5], [/inter|b1|b2/i, 3], [/bás|basic|elementary|a2/i, 2], [/inic|begin|a1/i, 1],
];
const levelFrom = v => {
  if (v == null || v === '') return 0;
  const n = Number(v);
  if (n >= 1 && n <= 5) return Math.round(n);
  for (const [re, l] of LEVEL_WORDS) if (re.test(v)) return l;
  return 0;
};

export function fromJsonResume(jr) {
  const r = newResume();
  const b = jr.basics || {};
  const [firstName, ...rest] = (b.name || '').split(' ');
  r.name = b.name ? `Currículo — ${b.name}` : 'Currículo importado';
  Object.assign(r.personal, {
    firstName: firstName || '', lastName: rest.join(' '), role: b.label || '', photo: b.image || '',
    email: b.email || '', phone: b.phone || '',
    address: b.location?.address || '', postalCode: b.location?.postalCode || '',
    city: [b.location?.city, b.location?.region].filter(Boolean).join(', '),
  });
  if (b.url) r.personal.extras.push({ id: uid(), key: 'website', value: b.url });
  for (const p of b.profiles || []) {
    const isLi = /linkedin/i.test(p.network || p.url || '');
    r.personal.extras.push({ id: uid(), key: isLi ? 'linkedin' : 'custom', label: isLi ? '' : p.network, value: p.url || p.username || '' });
  }

  const sections = [];
  const make = (type, list, map) => {
    if (!list?.length) return;
    const s = newSection(type);
    s.items = list.map(x => ({ ...newItem(s), ...map(x) }));
    sections.push(s);
  };
  if (b.summary) { const s = newSection('summary'); s.content = b.summary; sections.push(s); }
  const dated = x => ({ start: ym(x.startDate), end: ym(x.endDate), current: !!x.startDate && !x.endDate, description: jrToDesc(x) });
  make('experience', jr.work, x => ({ title: x.position || '', org: x.name || x.company || '', city: x.location || '', ...dated(x) }));
  make('education', jr.education, x => ({ title: [x.studyType, x.area].filter(Boolean).join(' em '), org: x.institution || '', ...dated(x), description: (x.courses || []).map(c => `- ${c}`).join('\n') }));
  make('extracurricular', jr.volunteer, x => ({ title: x.position || '', org: x.organization || '', ...dated(x) }));
  make('skills', jr.skills, x => ({ name: x.name || '', level: levelFrom(x.level) }));
  make('languages', jr.languages, x => ({ name: x.language || '', level: levelFrom(x.fluency) }));
  make('certificates', jr.certificates, x => ({ title: x.name || '', org: x.issuer || '', start: ym(x.date) }));
  make('achievements', jr.awards, x => ({ title: x.title || '', start: ym(x.date), description: x.summary || '' }));
  make('custom', jr.projects, x => ({ title: x.name || '', org: x.url || '', ...dated(x) }));
  make('hobbies', jr.interests, x => ({ name: x.name || '' }));
  make('references', jr.references, x => ({ title: x.name || '', org: x.reference || '' }));
  const projects = sections.find(s => s.type === 'custom');
  if (projects) projects.title = 'Projetos';
  r.sections = sections.length ? sections : r.sections;
  return normalize(r);
}
