// Service worker: guarda todos os arquivos do app para funcionar sem internet.
// Ao mudar qualquer arquivo, aumente VERSION para os aparelhos baixarem a nova versão.
const VERSION = 'v1';
const CACHE = `curriculo-${VERSION}`;
const FILES = [
  './', 'index.html', 'manifest.webmanifest',
  'css/app.css', 'css/resume.css',
  'js/app.js', 'js/model.js', 'js/i18n.js', 'js/doc.js', 'js/render.js', 'js/store.js', 'js/ui.js',
  'js/ats.js', 'js/import.js',
  'js/export/pdf.js', 'js/export/metrics.js', 'js/export/docx.js', 'js/export/zip.js', 'js/export/text.js',
  'fonts/inter-400.woff2', 'fonts/inter-400i.woff2', 'fonts/inter-600.woff2', 'fonts/inter-700.woff2',
  'fonts/manrope-var.woff2', 'fonts/carlito-400.woff2', 'fonts/carlito-400i.woff2', 'fonts/carlito-700.woff2',
  'fonts/caladea-400.woff2', 'fonts/caladea-400i.woff2', 'fonts/caladea-700.woff2',
  'icons/icon.svg', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/maskable-512.png',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k.startsWith('curriculo-') && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Cache primeiro; se não estiver no cache, busca na rede e guarda.
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(
    caches.match(req, { ignoreSearch: true }).then(hit => hit || fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => req.mode === 'navigate' ? caches.match('index.html') : Response.error())),
  );
});
