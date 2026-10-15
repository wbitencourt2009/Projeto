// Login and library in localStorage; the big channel/VOD list in IndexedDB
// (localStorage is capped at ~5 MB, a full provider list can be larger).

const LS_PROFILE = 'player.profile';
const LS_FAV = 'player.favourites';
const LS_RECENT = 'player.recent';
export const MAX_RECENT = 60;

const read = (k, d) => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } };
const write = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* storage full */ } };

export const loadProfile = () => read(LS_PROFILE, null);
export const saveProfile = (p) => write(LS_PROFILE, p);
export const loadFavourites = () => read(LS_FAV, []);
export const saveFavourites = (v) => write(LS_FAV, v);
export const loadRecent = () => read(LS_RECENT, []);
export const saveRecent = (v) => write(LS_RECENT, v);

export function clearAll() {
  [LS_PROFILE, LS_FAV, LS_RECENT].forEach((k) => localStorage.removeItem(k));
  return idbSet('content', null);
}

function db() {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) { reject(new Error('no idb')); return; }
    const req = indexedDB.open('player', 1);
    req.onupgradeneeded = () => req.result.createObjectStore('kv');
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export async function idbGet(key) {
  try {
    const d = await db();
    return await new Promise((resolve) => {
      const r = d.transaction('kv', 'readonly').objectStore('kv').get(key);
      r.onsuccess = () => resolve(r.result || null);
      r.onerror = () => resolve(null);
    });
  } catch (e) { return null; }
}

export async function idbSet(key, value) {
  try {
    const d = await db();
    await new Promise((resolve) => {
      const tx = d.transaction('kv', 'readwrite');
      if (value == null) tx.objectStore('kv').delete(key); else tx.objectStore('kv').put(value, key);
      tx.oncomplete = resolve;
      tx.onerror = resolve;
    });
  } catch (e) { /* ignore */ }
}
