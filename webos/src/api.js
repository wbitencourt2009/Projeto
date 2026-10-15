// Xtream Codes player API and M3U playlists. Same behaviour as the Android app.
import { decode64 } from './util.js';

const TIMEOUT_MS = 60000;

export class LoginError extends Error {}

function withTimeout(promise, ms) {
  let t;
  return Promise.race([
    promise,
    new Promise((_, rej) => { t = setTimeout(() => rej(new LoginError('O servidor demorou demais para responder.')), ms); }),
  ]).then((v) => { clearTimeout(t); return v; }, (e) => { clearTimeout(t); throw e; });
}

async function getJson(url) {
  let res;
  try {
    res = await withTimeout(fetch(url), TIMEOUT_MS);
  } catch (e) {
    if (e instanceof LoginError) throw e;
    throw new LoginError('Não foi possível conectar ao servidor. Confira o endereço e a internet.');
  }
  if (!res.ok) throw new LoginError('O servidor respondeu com erro HTTP ' + res.status + '.');
  try {
    return await res.json();
  } catch (e) {
    throw new LoginError('Resposta inválida do servidor. Confira o endereço.');
  }
}

const enc = encodeURIComponent;
const str = (v) => (v == null ? null : String(v));
const http = (v) => (v && /^https?:/i.test(v) ? v : null);

export function normaliseServer(input) {
  let s = String(input || '').trim();
  if (!/^https?:\/\//i.test(s)) s = 'http://' + s;
  s = s.split('/player_api.php')[0].split('/get.php')[0].split('/xmltv.php')[0];
  return s.replace(/\/+$/, '');
}

const base = (p) => p.server + '/player_api.php?username=' + enc(p.username) + '&password=' + enc(p.password);
const auth = (p) => enc(p.username) + '/' + enc(p.password);

export const isXtream = (p) => p && p.type === 'xtream';

export function liveUrl(p, ch, format) {
  if (!isXtream(p)) return ch.url || '';
  return p.server + '/live/' + auth(p) + '/' + ch.id + '.' + (format || 'm3u8');
}
export function movieUrl(p, m) {
  return isXtream(p) ? p.server + '/movie/' + auth(p) + '/' + m.id + '.' + (m.ext || 'mp4') : m.url || '';
}
export function episodeUrl(p, e) {
  return p.server + '/series/' + auth(p) + '/' + e.id + '.' + (e.ext || 'mp4');
}

async function optional(fn) {
  try { return await fn(); } catch (e) { return []; }
}

const cats = (arr) => (Array.isArray(arr) ? arr : [])
  .filter((o) => o && o.category_id != null)
  .map((o) => ({ id: str(o.category_id), name: (str(o.category_name) || '').trim() || 'Categoria ' + o.category_id }));

export async function fetchContent(p, onStep) {
  return isXtream(p) ? fetchXtream(p, onStep) : fetchM3u(p, onStep);
}

async function fetchXtream(p, onStep) {
  const b = base(p);
  onStep('Conectando ao servidor');
  const login = await getJson(b);
  const user = login && login.user_info;
  if (!user) throw new LoginError('O servidor não respondeu como um painel Xtream Codes. Confira o endereço.');
  if (String(user.auth) === '0') throw new LoginError('Usuário ou senha incorretos.');
  if (user.status && String(user.status).toLowerCase() !== 'active') {
    throw new LoginError('Conta com status "' + user.status + '" no servidor. Fale com o seu provedor.');
  }
  const account = { status: user.status || null, expDate: user.exp_date ? Number(user.exp_date) : null };

  onStep('Baixando canais');
  const categories = cats(await getJson(b + '&action=get_live_categories'));
  const streams = await getJson(b + '&action=get_live_streams');
  const channels = (Array.isArray(streams) ? streams : []).filter((o) => o && o.stream_id != null).map((o) => ({
    id: str(o.stream_id),
    number: o.num != null && !isNaN(Number(o.num)) ? Math.round(Number(o.num)) : null,
    name: (str(o.name) || '').trim() || 'Canal ' + o.stream_id,
    logo: http(o.stream_icon),
    categoryId: str(o.category_id),
  }));

  onStep('Baixando filmes');
  const movieCategories = await optional(async () => cats(await getJson(b + '&action=get_vod_categories')));
  const movies = await optional(async () => (await getJson(b + '&action=get_vod_streams'))
    .filter((o) => o && o.stream_id != null)
    .map((o) => ({
      id: str(o.stream_id),
      name: (str(o.name) || '').trim() || 'Filme ' + o.stream_id,
      poster: http(o.stream_icon),
      categoryId: str(o.category_id),
      ext: str(o.container_extension),
    })));

  onStep('Baixando séries');
  const seriesCategories = await optional(async () => cats(await getJson(b + '&action=get_series_categories')));
  const series = await optional(async () => (await getJson(b + '&action=get_series'))
    .filter((o) => o && o.series_id != null)
    .map((o) => ({
      id: str(o.series_id),
      name: (str(o.name) || '').trim() || 'Série ' + o.series_id,
      cover: http(o.cover),
      categoryId: str(o.category_id),
      rating: o.rating && o.rating !== '0' ? str(o.rating) : null,
      plot: str(o.plot),
    })));

  if (!channels.length && !movies.length && !series.length) throw new LoginError('Login feito, mas o servidor não enviou nenhum conteúdo.');
  return { categories, channels, movieCategories, movies, seriesCategories, series, account, fetchedAt: Date.now() };
}

async function fetchM3u(p, onStep) {
  onStep('Baixando lista M3U');
  let text;
  try {
    const res = await withTimeout(fetch(p.m3uUrl), 120000);
    if (!res.ok) throw new LoginError('O servidor respondeu com erro HTTP ' + res.status + '.');
    text = await res.text();
  } catch (e) {
    if (e instanceof LoginError) throw e;
    throw new LoginError('Não foi possível baixar a lista. Confira o link.');
  }
  onStep('Lendo a lista');
  const channels = []; const movies = []; const series = [];
  const live = new Map(); const mg = new Map(); const sg = new Map();
  let pending = null;
  const attrRe = /([\w-]+)="([^"]*)"/g;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (/^#EXTINF/i.test(line)) {
      const attrs = {};
      let m;
      attrRe.lastIndex = 0;
      while ((m = attrRe.exec(line))) attrs[m[1].toLowerCase()] = m[2];
      const comma = line.lastIndexOf(',');
      pending = { attrs, name: comma >= 0 ? line.slice(comma + 1).trim() : '' };
    } else if (line && line[0] !== '#' && pending) {
      const a = pending.attrs;
      const group = (a['group-title'] || '').trim() || 'Outros';
      const name = pending.name || a['tvg-name'] || 'Item';
      const logo = http(a['tvg-logo']);
      if (line.indexOf('/movie/') >= 0) {
        mg.set(group, { id: group, name: group });
        movies.push({ id: 'm' + movies.length, name, poster: logo, categoryId: group, url: line });
      } else if (line.indexOf('/series/') >= 0) {
        sg.set(group, { id: group, name: group });
        series.push({ id: 's' + series.length, name, cover: logo, categoryId: group, url: line });
      } else {
        live.set(group, { id: group, name: group });
        channels.push({ id: String(channels.length), number: Number(a['tvg-chno']) || channels.length + 1, name, logo, categoryId: group, url: line });
      }
      pending = null;
    }
  }
  if (!channels.length && !movies.length && !series.length) throw new LoginError('A lista M3U está vazia.');
  return {
    categories: [...live.values()], channels,
    movieCategories: [...mg.values()], movies,
    seriesCategories: [...sg.values()], series,
    account: null, fetchedAt: Date.now(),
  };
}

export async function seriesDetail(p, s) {
  if (!isXtream(p)) return { plot: s.plot, backdrop: s.cover, seasons: {} };
  const root = await getJson(base(p) + '&action=get_series_info&series_id=' + enc(s.id));
  const info = (root && root.info) || {};
  const backdrop = (Array.isArray(info.backdrop_path) && http(info.backdrop_path[0])) || http(info.cover) || s.cover;
  const raw = root && root.episodes;
  const lists = Array.isArray(raw) ? raw : raw && typeof raw === 'object' ? Object.keys(raw).map((k) => raw[k]) : [];
  const seasons = {};
  for (const list of lists) {
    for (const e of Array.isArray(list) ? list : []) {
      if (!e || e.id == null) continue;
      const season = Number(e.season) || 1;
      const ep = {
        id: str(e.id), season, number: Number(e.episode_num) || 0,
        title: (str(e.title) || '').trim() || 'Episódio',
        ext: str(e.container_extension),
        image: http(e.info && e.info.movie_image),
        plot: e.info && e.info.plot ? str(e.info.plot) : null,
        duration: e.info && e.info.duration ? str(e.info.duration) : null,
      };
      (seasons[season] = seasons[season] || []).push(ep);
    }
  }
  Object.keys(seasons).forEach((k) => seasons[k].sort((a, b) => a.number - b.number));
  return { plot: info.plot || s.plot, backdrop, seasons };
}

export async function epg(p, ch) {
  if (!isXtream(p)) return [];
  const root = await getJson(base(p) + '&action=get_simple_data_table&stream_id=' + enc(ch.id));
  const list = (root && root.epg_listings) || [];
  const seen = {};
  return list.map((o) => {
    const start = Number(o.start_timestamp);
    const end = Number(o.stop_timestamp || o.end_timestamp);
    if (!start || !end || end <= start || seen[start]) return null;
    seen[start] = true;
    return {
      start: start * 1000,
      end: end * 1000,
      title: decode64(o.title) || 'Sem título',
      description: decode64(o.description) || null,
      hasArchive: String(o.has_archive) === '1',
    };
  }).filter(Boolean).sort((a, b) => a.start - b.start);
}
