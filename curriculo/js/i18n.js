// Textos que aparecem DENTRO do currículo, no idioma escolhido para ele.
// (A interface do app é em português.)

export const RESUME_LANGS = { pt: 'Português', en: 'English', es: 'Español' };

const T = {
  pt: {
    resume: 'Currículo', present: 'Atual', personal: 'Dados pessoais', contact: 'Contato',
    months: ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'],
    fields: {
      name: 'Nome', email: 'E-mail', phone: 'Telefone', address: 'Endereço', city: 'Cidade',
      birthDate: 'Data de nascimento', birthPlace: 'Naturalidade', driverLicense: 'CNH', gender: 'Gênero',
      nationality: 'Nacionalidade', maritalStatus: 'Estado civil', website: 'Site', linkedin: 'LinkedIn',
    },
    sections: {
      summary: 'Resumo profissional', education: 'Formação', experience: 'Experiência', skills: 'Competências',
      languages: 'Idiomas', hobbies: 'Interesses', courses: 'Cursos', internships: 'Estágios',
      extracurricular: 'Atividades extracurriculares', references: 'Referências', qualities: 'Qualidades',
      certificates: 'Certificados', achievements: 'Realizações', signature: 'Assinatura', footer: '',
      custom: 'Outros', customList: 'Outros',
    },
    levels: {
      skill: ['', 'Iniciante', 'Básico', 'Intermediário', 'Avançado', 'Especialista'],
      language: ['', 'Básico', 'Intermediário', 'Avançado', 'Fluente', 'Nativo'],
    },
  },
  en: {
    resume: 'Résumé', present: 'Present', personal: 'Personal details', contact: 'Contact',
    months: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
    fields: {
      name: 'Name', email: 'Email', phone: 'Phone', address: 'Address', city: 'City',
      birthDate: 'Date of birth', birthPlace: 'Place of birth', driverLicense: "Driver's license", gender: 'Gender',
      nationality: 'Nationality', maritalStatus: 'Marital status', website: 'Website', linkedin: 'LinkedIn',
    },
    sections: {
      summary: 'Professional summary', education: 'Education', experience: 'Experience', skills: 'Skills',
      languages: 'Languages', hobbies: 'Interests', courses: 'Courses', internships: 'Internships',
      extracurricular: 'Extracurricular activities', references: 'References', qualities: 'Strengths',
      certificates: 'Certifications', achievements: 'Achievements', signature: 'Signature', footer: '',
      custom: 'Other', customList: 'Other',
    },
    levels: {
      skill: ['', 'Beginner', 'Basic', 'Intermediate', 'Advanced', 'Expert'],
      language: ['', 'Basic', 'Intermediate', 'Advanced', 'Fluent', 'Native'],
    },
  },
  es: {
    resume: 'Currículum', present: 'Actualidad', personal: 'Datos personales', contact: 'Contacto',
    months: ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'],
    fields: {
      name: 'Nombre', email: 'Correo', phone: 'Teléfono', address: 'Dirección', city: 'Ciudad',
      birthDate: 'Fecha de nacimiento', birthPlace: 'Lugar de nacimiento', driverLicense: 'Licencia de conducir',
      gender: 'Género', nationality: 'Nacionalidad', maritalStatus: 'Estado civil', website: 'Sitio web',
      linkedin: 'LinkedIn',
    },
    sections: {
      summary: 'Perfil profesional', education: 'Formación', experience: 'Experiencia', skills: 'Habilidades',
      languages: 'Idiomas', hobbies: 'Intereses', courses: 'Cursos', internships: 'Prácticas',
      extracurricular: 'Actividades extracurriculares', references: 'Referencias', qualities: 'Cualidades',
      certificates: 'Certificaciones', achievements: 'Logros', signature: 'Firma', footer: '',
      custom: 'Otros', customList: 'Otros',
    },
    levels: {
      skill: ['', 'Principiante', 'Básico', 'Intermedio', 'Avanzado', 'Experto'],
      language: ['', 'Básico', 'Intermedio', 'Avanzado', 'Fluido', 'Nativo'],
    },
  },
};

export const t = lang => T[lang] || T.pt;
