// Exporta para Word (.docx) escrevendo o Office Open XML à mão.
// Uma coluna, estilos de título reais e listas de verdade: bom para ATS e
// fácil de editar no Word, LibreOffice ou Google Docs.

import { zip } from './zip.js';

const xml = s => String(s ?? '')
  .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const TEXT_W = 11906 - 2 * 1134; // largura útil em twips (A4, margens de 2 cm)

const run = (text, { bold, italic, color, size } = {}) => {
  const props = [bold && '<w:b/>', italic && '<w:i/>', color && `<w:color w:val="${color}"/>`,
    size && `<w:sz w:val="${size}"/><w:szCs w:val="${size}"/>`].filter(Boolean).join('');
  return `<w:r>${props ? `<w:rPr>${props}</w:rPr>` : ''}<w:t xml:space="preserve">${xml(text)}</w:t></w:r>`;
};

const para = (content, { style, spacingAfter, tabs, keepNext } = {}) => {
  const props = [style && `<w:pStyle w:val="${style}"/>`, keepNext && '<w:keepNext/>',
    tabs && `<w:tabs>${tabs}</w:tabs>`,
    spacingAfter != null && `<w:spacing w:after="${spacingAfter}"/>`].filter(Boolean).join('');
  return `<w:p>${props ? `<w:pPr>${props}</w:pPr>` : ''}${content}</w:p>`;
};

const runsXml = runs => runs.map(r => run(r.text, { bold: r.bold, italic: r.italic })).join('');

export function buildDocx(doc, { title = 'Currículo', author = '' } = {}) {
  const accent = (doc.style.color || '#2f5d8a').replace('#', '').toUpperCase();
  const rels = [];
  const linkRel = url => {
    const id = `rIdL${rels.length + 1}`;
    rels.push(`<Relationship Id="${id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${xml(url)}" TargetMode="External"/>`);
    return id;
  };

  const body = [];
  const blocks = bs => {
    for (const b of bs) {
      if (b.type === 'p') body.push(para(runsXml(b.runs)));
      else for (const item of b.items) body.push(para(runsXml(item), { style: 'ListBullet' }));
    }
  };

  if (doc.name) body.push(para(run(doc.name), { style: 'Title' }));
  const role = doc.title !== doc.L.resume ? doc.title : doc.role;
  if (role) body.push(para(run(role, { color: accent, size: 24 }), { spacingAfter: 60 }));

  if (doc.contact.length) {
    const parts = doc.contact.map((c, i) => {
      const labelled = !['email', 'phone', 'address', 'website', 'linkedin'].includes(c.key);
      const text = labelled && c.label ? `${c.label}: ${c.value}` : c.value;
      const sep = i ? run('  |  ', { color: 'A0A4AA' }) : '';
      const inner = c.href
        ? `<w:hyperlink r:id="${linkRel(c.href)}">${run(text, { color: '5F6670' })}</w:hyperlink>`
        : run(text, { color: '5F6670' });
      return sep + inner;
    });
    body.push(para(parts.join(''), { style: 'Contact' }));
  }

  for (const s of doc.sections) {
    if (s.type === 'footer') continue;
    if (s.title) body.push(para(run(s.title), { style: 'Heading1' }));

    if (s.kind === 'text') {
      blocks(s.blocks);
    } else if (s.kind === 'entries') {
      for (const it of s.items) {
        const head = run(it.title || it.org, { bold: true }) + (it.dates ? `<w:r><w:tab/></w:r>${run(it.dates, { color: '5F6670', size: 18 })}` : '');
        body.push(para(head, { style: 'EntryTitle', keepNext: true, tabs: `<w:tab w:val="right" w:pos="${TEXT_W}"/>` }));
        const meta = [it.title ? it.org : '', it.city].filter(Boolean).join(', ');
        if (meta) body.push(para(run(meta, { italic: true, color: '5F6670' }), { keepNext: it.blocks.length > 0 }));
        blocks(it.blocks);
      }
    } else if (s.kind === 'levels' || s.kind === 'tags') {
      const text = s.items.map(i => i.level && doc.style.levelStyle !== 'none' ? `${i.name} (${i.levelText})` : i.name).join('  •  ');
      body.push(para(run(text)));
    } else if (s.kind === 'references') {
      for (const r of s.items) {
        body.push(para(run(r.title, { bold: true }), { style: 'EntryTitle', keepNext: true }));
        const meta = [r.org, r.city].filter(Boolean).join(', ');
        if (meta) body.push(para(run(meta, { italic: true, color: '5F6670' })));
        const contact = [r.phone, r.email].filter(Boolean).join('  |  ');
        if (contact) body.push(para(run(contact)));
      }
    } else if (s.kind === 'signature') {
      if (s.place) body.push(para(run(s.place)));
      body.push(para(run('_______________________________'), { spacingAfter: 0 }));
      if (s.name) body.push(para(run(s.name, { bold: true })));
    }
  }
  for (const f of doc.sections.filter(s => s.type === 'footer')) {
    for (const b of f.blocks) {
      for (const r of b.type === 'p' ? [b.runs] : b.items) body.push(para(runsXml(r), { style: 'Footer' }));
    }
  }

  const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>${body.join('')}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1020" w:right="1134" w:bottom="1020" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/></w:sectPr></w:body>
</w:document>`;

  const lang = { pt: 'pt-BR', en: 'en-US', es: 'es-ES' }[doc.lang] || 'pt-BR';
  const stylesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="21"/><w:szCs w:val="21"/><w:color w:val="23262B"/><w:lang w:val="${lang}"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="40" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="20"/></w:pPr><w:rPr><w:b/><w:sz w:val="44"/><w:szCs w:val="44"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Contact"><w:name w:val="Contact"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="120"/></w:pPr><w:rPr><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="280" w:after="100"/><w:pBdr><w:bottom w:val="single" w:sz="6" w:space="2" w:color="${accent}"/></w:pBdr><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:caps/><w:color w:val="${accent}"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="EntryTitle"><w:name w:val="Entry Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="120" w:after="0"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="ListBullet"><w:name w:val="List Bullet"/><w:basedOn w:val="Normal"/><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr><w:spacing w:after="20"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Footer"><w:name w:val="footer"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/><w:spacing w:before="240"/></w:pPr><w:rPr><w:color w:val="5F6670"/><w:sz w:val="17"/><w:szCs w:val="17"/></w:rPr></w:style>
<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:rPr><w:color w:val="5F6670"/></w:rPr></w:style>
</w:styles>`;

  const numberingXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="singleLevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="360" w:hanging="240"/></w:pPr><w:rPr><w:color w:val="${accent}"/></w:rPr></w:lvl></w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
</w:numbering>`;

  const now = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
  const files = [
    { name: '[Content_Types].xml', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>` },
    { name: '_rels/.rels', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>` },
    { name: 'docProps/core.xml', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:title>${xml(title)}</dc:title><dc:creator>${xml(author)}</dc:creator><dc:language>${lang}</dc:language>
<dcterms:created xsi:type="dcterms:W3CDTF">${now}</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">${now}</dcterms:modified>
</cp:coreProperties>` },
    { name: 'word/_rels/document.xml.rels', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
${rels.join('\n')}
</Relationships>` },
    { name: 'word/document.xml', data: documentXml },
    { name: 'word/styles.xml', data: stylesXml },
    { name: 'word/numbering.xml', data: numberingXml },
  ];
  return zip(files, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
}
