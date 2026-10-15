// Small DOM and formatting helpers. No framework: webOS 4+ (Chromium 53) friendly.

export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  if (attrs) {
    for (const k in attrs) {
      const v = attrs[k];
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
      else if (k === 'text') el.textContent = v;
      else if (k.startsWith('on') && typeof v === 'function') el['_' + k] = v; // handled by nav.js
      else if (k === 'focusable') { if (v) el.setAttribute('data-f', ''); }
      else el.setAttribute(k, v === true ? '' : v);
    }
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children) {
    if (c == null || c === false) continue;
    if (Array.isArray(c)) append(el, c);
    else el.appendChild(typeof c === 'string' || typeof c === 'number' ? document.createTextNode(String(c)) : c);
  }
}

const WEEKDAYS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];
const WEEKDAYS_LONG = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];
const MONTHS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

const pad = (n) => (n < 10 ? '0' + n : '' + n);

export const fmtTime = (ms) => { const d = new Date(ms); return pad(d.getHours()) + ':' + pad(d.getMinutes()); };
export const fmtDate = (ms) => { const d = new Date(ms); return WEEKDAYS_LONG[d.getDay()] + ', ' + d.getDate() + ' de ' + MONTHS[d.getMonth()]; };
export const fmtDay = (ms) => { const d = new Date(ms); return pad(d.getDate()) + '/' + pad(d.getMonth() + 1); };
export const fmtFullDate = (ms) => { const d = new Date(ms); return pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear(); };
export const weekday = (ms) => WEEKDAYS[new Date(ms).getDay()];
export const fmtCount = (n) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.');

export function normalise(s) {
  s = String(s || '').toLowerCase();
  return s.normalize ? s.normalize('NFD').replace(/[̀-ͯ]/g, '') : s;
}

export function initials(name) {
  const parts = String(name || '').split(/[\s\-|:]+/).filter((p) => p && /[A-Za-z0-9\u00C0-\u024F]/.test(p[0]));
  const s = parts.slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  return s || 'TV';
}

const GRADIENTS = [
  ['#22D3EE', '#5B3FD1'], ['#FF4D6D', '#7A1230'], ['#34D399', '#0F5D47'],
  ['#FBBF24', '#8A4A06'], ['#A78BFA', '#3B2585'], ['#60A5FA', '#1E3A8A'],
];

function hash(s) {
  let x = 0;
  for (let i = 0; i < s.length; i++) x = (x * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(x);
}

/** Generated placeholder: gradient + initials. */
export function placeholder(name, cls) {
  const g = GRADIENTS[hash(name || '') % GRADIENTS.length];
  return h('div', { class: 'ph ' + (cls || ''), style: { background: 'linear-gradient(135deg,' + g[0] + ',' + g[1] + ')' } }, initials(name));
}

/** <img> that loads lazily when it comes on screen and falls back to a placeholder. */
let observer = null;
export function lazyImg(src, name, cls) {
  if (!src) return placeholder(name, cls);
  const img = h('img', { class: cls || '', alt: '' });
  img.dataset.src = src;
  img.onerror = () => { const p = placeholder(name, cls); if (img.parentNode) img.parentNode.replaceChild(p, img); };
  if (!observer && 'IntersectionObserver' in window) {
    observer = new IntersectionObserver((entries) => {
      for (const e of entries) {
        if (e.isIntersecting) { e.target.src = e.target.dataset.src; observer.unobserve(e.target); }
      }
    }, { rootMargin: '300px' });
  }
  if (observer) observer.observe(img); else img.src = src;
  return img;
}

/** Xtream sends EPG text in base64; some panels send plain text. */
export function decode64(v) {
  if (!v) return '';
  try {
    const bin = atob(v);
    const text = decodeURIComponent(escape(bin));
    return /[\u0000-\u0008\u000E-\u001F�]/.test(text) ? v : text.trim();
  } catch (e) {
    return v;
  }
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
