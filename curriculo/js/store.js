// Armazenamento no próprio aparelho (IndexedDB). Nada sai do navegador.

import { normalize } from './model.js';

const DB_NAME = 'curriculo', STORE = 'resumes', SETTINGS = 'settings';
let dbPromise;

function db() {
  dbPromise ??= new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      const d = req.result;
      if (!d.objectStoreNames.contains(STORE)) d.createObjectStore(STORE, { keyPath: 'id' });
      if (!d.objectStoreNames.contains(SETTINGS)) d.createObjectStore(SETTINGS);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

async function tx(store, mode, fn) {
  const d = await db();
  return new Promise((resolve, reject) => {
    const t = d.transaction(store, mode);
    const req = fn(t.objectStore(store));
    t.oncomplete = () => resolve(req?.result);
    t.onerror = () => reject(t.error);
  });
}

export const listResumes = async () =>
  ((await tx(STORE, 'readonly', s => s.getAll())) || []).map(normalize).sort((a, b) => b.updatedAt - a.updatedAt);
export const getResume = async id => { const r = await tx(STORE, 'readonly', s => s.get(id)); return r && normalize(r); };
export const saveResume = r => tx(STORE, 'readwrite', s => s.put(r));
export const deleteResume = id => tx(STORE, 'readwrite', s => s.delete(id));
export const getSetting = (key) => tx(SETTINGS, 'readonly', s => s.get(key));
export const setSetting = (key, value) => tx(SETTINGS, 'readwrite', s => s.put(value, key));

// Pede ao navegador para não apagar os dados quando faltar espaço.
export const persist = () => navigator.storage?.persist?.().catch(() => false);
