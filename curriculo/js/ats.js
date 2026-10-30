// Análise de compatibilidade com ATS (sistemas de triagem de currículos).
// Tudo roda no aparelho: são regras simples e transparentes, não uma "caixa-preta".

import { toText } from './export/text.js';
import { TEMPLATES } from './render.js';

const STOP = new Set(`a o as os um uma uns umas de da do das dos em no na nos nas por para com sem sob sobre entre
e ou mas que se como quando onde qual quais quem cujo seu sua seus suas nosso nossa ele ela eles elas voce voces
ser estar ter haver ir vai sera sao esta estao tem temos foi era sera ja nao sim mais menos muito muita muitos
muitas todo toda todos todas outro outra outros outras este esta isto esse essa isso aquele aquela ao aos pelo pela
pelos pelas num numa ate apos desde cada mesmo tambem bem ainda so pode podem deve devem sera serao area vaga vagas
empresa candidato candidata requisitos requisito desejavel desejaveis diferencial diferenciais experiencia
conhecimento conhecimentos atividades responsabilidades beneficios local horario salario contratacao regime
buscamos procuramos profissional profissionais oportunidade time equipe trabalho trabalhar atuar atuacao
the a an and or of to in on for with by at from as is are be will you your we our this that it its not
role team company job work working experience strong ability skills knowledge requirements preferred plus
el la los las un una y o de del en con por para que se su sus es son al lo como mas experiencia empresa`.split(/\s+/));

export const fold = s => String(s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

function tokens(text) {
  return fold(text).match(/[a-z0-9][a-z0-9+#./-]*[a-z0-9+#]|[a-z0-9]/g) || [];
}

// Termos mais relevantes da descrição da vaga (palavras e pares de palavras).
export function keywordsFrom(jobText, max = 30) {
  const words = tokens(jobText);
  const counts = new Map();
  const bump = (k, n = 1) => counts.set(k, (counts.get(k) || 0) + n);
  words.forEach((w, i) => {
    const ok = x => x && !STOP.has(x) && (x.length > 2 || /[+#]/.test(x)) && !/^\d+$/.test(x);
    if (ok(w)) bump(w);
    const n = words[i + 1];
    if (ok(w) && ok(n)) bump(`${w} ${n}`, 0.75);
  });
  return [...counts.entries()]
    .filter(([k, c]) => (k.includes(' ') ? c >= 1.5 : true))
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, max)
    .map(([k]) => k);
}

export function matchKeywords(doc, jobText) {
  const hay = ` ${tokens(toText(doc)).join(' ')} `;
  const kws = keywordsFrom(jobText);
  const found = [], missing = [];
  for (const k of kws) (hay.includes(` ${k} `) ? found : missing).push(k);
  return { found, missing, pct: kws.length ? Math.round((found.length / kws.length) * 100) : 0 };
}

export function analyze(resume, doc) {
  const checks = [];
  const add = (ok, weight, text, tip = '', level) => checks.push({ ok, weight, text, tip, level: level || (ok ? 'ok' : weight >= 8 ? 'error' : 'warn') });
  const p = resume.personal;
  const secs = type => doc.sections.filter(s => s.type === type);
  const entries = type => secs(type).flatMap(s => s.items);

  add(!!doc.name, 10, 'Nome completo', 'Preencha nome e sobrenome.');
  add(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test((p.email || '').trim()), 10, 'E-mail válido', 'Recrutadores e ATS precisam de um e-mail para contato.');
  add((p.phone || '').replace(/\D/g, '').length >= 8, 8, 'Telefone', 'Inclua um telefone com DDD.');
  add(!!(p.city || '').trim(), 4, 'Cidade', 'Muitos filtros de vaga usam a localização.');
  add(!!(p.role || '').trim(), 6, 'Vaga desejada', 'Um título claro (ex.: "Analista de Dados") ajuda o ATS a classificar o currículo.');

  const summary = secs('summary').map(s => s.blocks.map(b => b.type === 'p' ? b.runs.map(r => r.text).join('') : '').join(' ')).join(' ');
  add(summary.length >= 150 && summary.length <= 900, 6, 'Resumo profissional (150 a 900 caracteres)',
    summary.length ? `Seu resumo tem ${summary.length} caracteres.` : 'Escreva 3 a 5 frases sobre quem você é e o que entrega.');

  const exp = [...entries('experience'), ...entries('internships')];
  add(exp.length > 0, 10, 'Experiência profissional', 'Inclua empregos, estágios ou trabalhos voluntários relevantes.');
  if (exp.length) {
    const complete = exp.filter(e => e.title && e.org && e.dates).length;
    add(complete === exp.length, 7, 'Cargo, empresa e datas em todas as experiências',
      `${exp.length - complete} experiência(s) sem cargo, empresa ou datas.`);
    const withBullets = exp.filter(e => e.blocks.some(b => b.type === 'ul')).length;
    add(withBullets >= Math.ceil(exp.length / 2), 5, 'Descrições em tópicos',
      'Use linhas começando com "- " para listar entregas. ATS e recrutadores leem melhor.');
    const allText = exp.flatMap(e => e.blocks.flatMap(b => b.type === 'ul' ? b.items : [b.runs])).map(r => r.map(x => x.text).join('')).join(' ');
    add(/\d/.test(allText), 5, 'Resultados com números', 'Quantifique: "reduzi custos em 15%", "atendi 40 clientes por dia".');
    const longLines = exp.flatMap(e => e.blocks.flatMap(b => b.type === 'ul' ? b.items : [])).filter(r => r.map(x => x.text).join('').length > 260).length;
    if (longLines) add(false, 2, 'Tópicos curtos', `${longLines} tópico(s) com mais de 260 caracteres. Divida em frases menores.`);
  }
  add(entries('education').length > 0, 6, 'Formação', 'Inclua sua formação, mesmo que em andamento.');
  const skills = entries('skills').length;
  add(skills >= 5, 6, 'Pelo menos 5 competências', `Você tem ${skills}. Liste ferramentas e habilidades pedidas nas vagas.`);

  const words = toText(doc).split(/\s+/).filter(Boolean).length;
  add(words >= 200 && words <= 1100, 4, 'Tamanho adequado (200 a 1.100 palavras)', `Seu currículo tem ${words} palavras.`);

  const tpl = TEMPLATES[resume.style.template];
  add(!!tpl?.ats, 3, 'Modelo de uma coluna',
    'Modelos com coluna lateral ficam bonitos, mas alguns ATS embaralham a ordem. Para candidaturas online, exporte em "PDF para ATS" ou Word.');

  // Avisos informativos (não contam pontos).
  const sensitive = (p.extras || []).filter(e => ['gender', 'maritalStatus', 'birthDate'].includes(e.key) && e.value).length;
  if (sensitive) checks.push({ ok: false, weight: 0, level: 'info', text: 'Dados pessoais sensíveis',
    tip: 'Gênero, estado civil e data de nascimento não são exigidos e podem gerar viés. Inclua só se a vaga pedir.' });
  if (p.photo) checks.push({ ok: false, weight: 0, level: 'info', text: 'Foto', tip: 'O ATS ignora fotos. Nos PDFs para ATS e no Word ela não é incluída.' });

  const total = checks.reduce((n, c) => n + c.weight, 0);
  const got = checks.reduce((n, c) => n + (c.ok ? c.weight : 0), 0);
  return { score: total ? Math.round((got / total) * 100) : 0, checks, words };
}
