// Modelo de dados do currículo: tipos de seção, campos e valores padrão.

export const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);

// Campos opcionais dos dados pessoais (os "chips" abaixo do formulário).
export const PERSONAL_EXTRAS = [
  { key: 'birthDate', label: 'Data de nascimento' },
  { key: 'birthPlace', label: 'Naturalidade' },
  { key: 'driverLicense', label: 'Carteira de motorista' },
  { key: 'gender', label: 'Gênero' },
  { key: 'nationality', label: 'Nacionalidade' },
  { key: 'maritalStatus', label: 'Estado civil' },
  { key: 'website', label: 'Site' },
  { key: 'linkedin', label: 'LinkedIn' },
  { key: 'custom', label: 'Campo personalizado', multiple: true },
];

const DATED = [
  { key: 'start', label: 'Início', type: 'month', half: true },
  { key: 'end', label: 'Fim', type: 'month', half: true },
  { key: 'current', label: 'Atual', type: 'check' },
];

const DESCRIPTION = { key: 'description', label: 'Descrição', type: 'rich' };

// kind: como o item é desenhado.
//   entries  → título, subtítulo, datas e descrição
//   levels   → nome + nível (0 = sem nível)
//   tags     → só nomes
//   text     → um bloco de texto (sem itens)
//   signature, references → específicos
export const SECTION_TYPES = {
  summary: { label: 'Resumo profissional', kind: 'text' },
  education: {
    label: 'Formação', kind: 'entries',
    fields: [
      { key: 'title', label: 'Curso', type: 'text' },
      { key: 'org', label: 'Instituição de ensino', type: 'text' },
      { key: 'city', label: 'Cidade', type: 'text' },
      ...DATED, DESCRIPTION,
    ],
  },
  experience: {
    label: 'Experiência', kind: 'entries',
    fields: [
      { key: 'title', label: 'Cargo', type: 'text' },
      { key: 'org', label: 'Empresa', type: 'text' },
      { key: 'city', label: 'Cidade', type: 'text' },
      ...DATED, DESCRIPTION,
    ],
  },
  skills: { label: 'Competências', kind: 'levels', itemLabel: 'Competência', levels: 'skill' },
  languages: { label: 'Idiomas', kind: 'levels', itemLabel: 'Idioma', levels: 'language' },
  hobbies: { label: 'Interesses', kind: 'tags', itemLabel: 'Interesse' },
  courses: {
    label: 'Cursos', kind: 'entries',
    fields: [
      { key: 'title', label: 'Curso', type: 'text' },
      { key: 'org', label: 'Instituição', type: 'text' },
      ...DATED, DESCRIPTION,
    ],
  },
  internships: {
    label: 'Estágios', kind: 'entries',
    fields: [
      { key: 'title', label: 'Função', type: 'text' },
      { key: 'org', label: 'Empresa', type: 'text' },
      { key: 'city', label: 'Cidade', type: 'text' },
      ...DATED, DESCRIPTION,
    ],
  },
  extracurricular: {
    label: 'Atividades extracurriculares', kind: 'entries',
    fields: [
      { key: 'title', label: 'Função', type: 'text' },
      { key: 'org', label: 'Organização', type: 'text' },
      { key: 'city', label: 'Cidade', type: 'text' },
      ...DATED, DESCRIPTION,
    ],
  },
  references: {
    label: 'Referências', kind: 'references',
    fields: [
      { key: 'title', label: 'Nome', type: 'text' },
      { key: 'org', label: 'Empresa', type: 'text' },
      { key: 'city', label: 'Cidade', type: 'text' },
      { key: 'phone', label: 'Telefone', type: 'text', half: true },
      { key: 'email', label: 'E-mail', type: 'text', half: true },
    ],
  },
  qualities: { label: 'Qualidades', kind: 'tags', itemLabel: 'Qualidade' },
  certificates: {
    label: 'Certificados', kind: 'entries',
    fields: [
      { key: 'title', label: 'Certificado', type: 'text' },
      { key: 'org', label: 'Emissor', type: 'text' },
      { key: 'start', label: 'Data', type: 'month', half: true },
      DESCRIPTION,
    ],
  },
  achievements: {
    label: 'Realizações', kind: 'entries',
    fields: [
      { key: 'title', label: 'Realização', type: 'text' },
      { key: 'start', label: 'Data', type: 'month', half: true },
      DESCRIPTION,
    ],
  },
  signature: { label: 'Assinatura', kind: 'signature' },
  footer: { label: 'Rodapé', kind: 'text' },
  custom: {
    label: 'Seção personalizada', kind: 'entries', multiple: true,
    fields: [
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'org', label: 'Subtítulo', type: 'text' },
      { key: 'city', label: 'Cidade', type: 'text' },
      ...DATED, DESCRIPTION,
    ],
  },
  customList: { label: 'Lista personalizada', kind: 'tags', itemLabel: 'Item', multiple: true },
};

// Seções que podem ser adicionadas (na ordem dos botões).
export const ADDABLE = ['summary', 'courses', 'internships', 'extracurricular', 'references',
  'qualities', 'certificates', 'achievements', 'signature', 'footer', 'custom', 'customList'];

export const LEVELS = {
  skill: ['', 'Iniciante', 'Básico', 'Intermediário', 'Avançado', 'Especialista'],
  language: ['', 'Básico', 'Intermediário', 'Avançado', 'Fluente', 'Nativo'],
};

export const ITEM_DEFAULTS = {
  entries: () => ({ title: '', org: '', city: '', start: '', end: '', current: false, description: '' }),
  references: () => ({ title: '', org: '', city: '', phone: '', email: '' }),
  levels: () => ({ name: '', level: 0 }),
  tags: () => ({ name: '' }),
};

export function newSection(type) {
  const t = SECTION_TYPES[type];
  const s = { id: uid(), type, title: '', collapsed: false };
  if (t.kind === 'text') s.content = '';
  else if (t.kind === 'signature') Object.assign(s, { city: '', date: '', name: '', image: '' });
  else s.items = [];
  return s;
}

export function newItem(section) {
  const kind = SECTION_TYPES[section.type].kind;
  return { id: uid(), ...ITEM_DEFAULTS[kind]() };
}

export const DEFAULT_STYLE = {
  template: 'cronologico',
  color: '#2f5d8a',
  font: 'inter',
  fontSize: 10, // pt
  lineHeight: 1.4,
  spacing: 1, // multiplicador de espaçamento entre seções
  photoShape: 'round',
  levelStyle: 'auto', // auto | bar | dots | text | none
  lang: 'pt',
};

export function newResume() {
  return {
    id: uid(),
    name: 'Currículo sem título',
    createdAt: Date.now(),
    updatedAt: Date.now(),
    style: { ...DEFAULT_STYLE },
    personal: {
      photo: '', firstName: '', lastName: '', role: '', roleAsTitle: false,
      email: '', phone: '', address: '', postalCode: '', city: '',
      extras: [],
    },
    sections: ['summary', 'experience', 'education', 'skills', 'languages', 'hobbies'].map(newSection),
  };
}

// Garante que dados antigos ou importados tenham todos os campos esperados.
export function normalize(r) {
  const base = newResume();
  const out = { ...base, ...r, style: { ...DEFAULT_STYLE, ...(r.style || {}) } };
  out.personal = { ...base.personal, ...(r.personal || {}) };
  out.personal.extras = (out.personal.extras || []).map(e => ({ id: e.id || uid(), ...e }));
  out.sections = (r.sections || base.sections)
    .filter(s => SECTION_TYPES[s.type])
    .map(s => {
      const n = { ...newSection(s.type), ...s, id: s.id || uid() };
      if (n.items) n.items = n.items.map(it => ({ ...newItem(n), ...it, id: it.id || uid() }));
      return n;
    });
  return out;
}

export const EXAMPLE = () => normalize({
  name: 'Exemplo — Analista de dados',
  personal: {
    firstName: 'Marina', lastName: 'Duarte', role: 'Analista de Dados Pleno', roleAsTitle: true,
    email: 'marina.duarte@email.com', phone: '(11) 98765-4321', city: 'São Paulo, SP',
    address: '', postalCode: '',
    extras: [
      { key: 'linkedin', value: 'linkedin.com/in/marinaduarte' },
      { key: 'website', value: 'github.com/marinaduarte' },
    ],
  },
  sections: [
    { type: 'summary', content: 'Analista de dados com 6 anos de experiência em varejo e finanças, transformando dados em decisões. Domínio de SQL, Python e Power BI, com foco em automação de relatórios e modelos de previsão de demanda.' },
    { type: 'experience', items: [
      { title: 'Analista de Dados Pleno', org: 'Varejo Bom Preço', city: 'São Paulo', start: '2021-03', current: true,
        description: '- Reduzi em **40%** o tempo de fechamento mensal automatizando 25 relatórios em Python\n- Criei modelo de previsão de demanda que diminuiu a ruptura de estoque em 18%\n- Lidero squad de 3 analistas em projetos de BI' },
      { title: 'Analista de Dados Júnior', org: 'Banco Horizonte', city: 'São Paulo', start: '2018-06', end: '2021-02',
        description: '- Construí painéis no Power BI usados por 120 gerentes de agência\n- Padronizei a base de clientes, eliminando 9 mil registros duplicados' },
    ] },
    { type: 'education', items: [
      { title: 'Bacharelado em Estatística', org: 'Universidade de São Paulo', city: 'São Paulo', start: '2014-02', end: '2018-12' },
    ] },
    { type: 'skills', items: [
      { name: 'SQL', level: 5 }, { name: 'Python (pandas, scikit-learn)', level: 4 },
      { name: 'Power BI', level: 4 }, { name: 'Estatística aplicada', level: 4 }, { name: 'Git', level: 3 },
    ] },
    { type: 'languages', items: [{ name: 'Português', level: 5 }, { name: 'Inglês', level: 4 }, { name: 'Espanhol', level: 2 }] },
    { type: 'certificates', items: [{ title: 'Microsoft Certified: Power BI Data Analyst', org: 'Microsoft', start: '2022-08' }] },
    { type: 'hobbies', items: [{ name: 'Corrida' }, { name: 'Xadrez' }, { name: 'Fotografia' }] },
  ],
});
