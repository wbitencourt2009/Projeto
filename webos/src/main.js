// LG webOS version of the IPTV player: same screens and flows as the Android app.
import { h, lazyImg, placeholder, fmtTime, fmtDate, fmtDay, fmtFullDate, weekday, fmtCount, normalise, sleep } from './util.js';
import * as nav from './nav.js';
import * as api from './api.js';
import * as store from './store.js';
import { createVideo } from './player.js';
import { demoContent, demoEpg, demoSeries } from './demo.js';

const DEMO = /[?&]demo=1/.test(location.search);
const ALL = '__all__';
const RECENT = '__recent__';
const FAV = '__fav__';
const NONE = '__none__';
const KINDS = { live: 'Ao vivo', movie: 'Filmes', series: 'Séries' };

const S = {
  profile: null,
  content: null,
  favourites: store.loadFavourites(),
  recent: store.loadRecent(),
  stack: [],
  loginError: null,
  loadingStep: '',
  epg: new Map(),
};

const app = document.getElementById('app');

// ---------- router ----------

const top = () => S.stack[S.stack.length - 1];
function push(screen) { S.stack.push(screen); render(); }
function reset(screen) { S.stack = [screen]; render(); }
function back() {
  if (closeDialog()) return true;
  if (S.stack.length <= 1) return false;
  const leaving = S.stack.pop();
  if (leaving.dispose) leaving.dispose();
  render();
  return true;
}

let keyHandler = null;
nav.setHandlers({ back, key: (e) => (keyHandler ? keyHandler(e) : false) });
window.__onFocus = (el) => { const t = top(); if (t && el.dataset.key) t.focusKey = el.dataset.key; };

function render() {
  const t = top();
  keyHandler = null;
  if (S.dispose) { S.dispose(); S.dispose = null; }
  app.innerHTML = '';
  const view = SCREENS[t.name](t);
  app.appendChild(view);
  const keyed = t.focusKey && app.querySelector('[data-key="' + cssEscape(t.focusKey) + '"]');
  if (!(keyed && nav.focus(keyed, { silent: true }))) nav.focusFirst(app);
}

const cssEscape = (s) => String(s).replace(/["\\]/g, '\\$&');

// ---------- data helpers ----------

const tileKey = (kind, id) => kind + ':' + id;

function tiles(kind) {
  const c = S.content;
  if (!c) return [];
  if (kind === 'live') return c.channels.map((x) => ({ kind, id: x.id, title: x.name, image: x.logo, categoryId: x.categoryId, number: x.number }));
  if (kind === 'movie') return c.movies.map((x) => ({ kind, id: x.id, title: x.name, image: x.poster, categoryId: x.categoryId }));
  return c.series.map((x) => ({ kind, id: x.id, title: x.name, image: x.cover, categoryId: x.categoryId }));
}

function categoriesOf(kind) {
  const c = S.content;
  const list = kind === 'live' ? c.categories : kind === 'movie' ? c.movieCategories : c.seriesCategories;
  const items = tiles(kind);
  const counts = {};
  for (const t of items) { const k = t.categoryId || NONE; counts[k] = (counts[k] || 0) + 1; }
  const out = list.filter((x) => counts[x.id]).map((x) => ({ id: x.id, name: x.name, count: counts[x.id] }));
  if (counts[NONE]) out.push({ id: NONE, name: 'Outros', count: counts[NONE] });
  return out;
}

function toggleFavourite(key) {
  const i = S.favourites.indexOf(key);
  if (i >= 0) S.favourites.splice(i, 1); else S.favourites.push(key);
  store.saveFavourites(S.favourites);
}
function markWatched(key) {
  S.recent = [key].concat(S.recent.filter((k) => k !== key)).slice(0, store.MAX_RECENT);
  store.saveRecent(S.recent);
}
function removeRecent(key) { S.recent = S.recent.filter((k) => k !== key); store.saveRecent(S.recent); }
function clearRecent(kind) { S.recent = S.recent.filter((k) => k.indexOf(kind + ':') !== 0); store.saveRecent(S.recent); }

async function loadEpg(channelId) {
  if (S.epg.has(channelId)) return S.epg.get(channelId);
  const ch = S.content.channels.find((c) => c.id === channelId);
  let list = [];
  try { list = DEMO ? demoEpg(channelId) : await api.epg(S.profile, ch); } catch (e) { list = []; }
  S.epg.set(channelId, list);
  return list;
}

function openTile(tile, list) {
  const p = S.profile;
  if (tile.kind === 'live') {
    const byId = {};
    S.content.channels.forEach((c) => { byId[c.id] = c; });
    const items = list.filter((t) => t.kind === 'live').map((t) => byId[t.id]).filter(Boolean).map((c) => ({
      key: tileKey('live', c.id), channelId: c.id, title: c.name, image: c.logo, number: c.number,
      url: api.liveUrl(p, c, 'm3u8'), fallback: api.isXtream(p) ? api.liveUrl(p, c, 'ts') : null,
    }));
    const index = Math.max(0, items.findIndex((i) => i.key === tileKey('live', tile.id)));
    markWatched(tileKey('live', tile.id));
    push({ name: 'player', items, index, live: true });
  } else if (tile.kind === 'movie') {
    const m = S.content.movies.find((x) => x.id === tile.id);
    markWatched(tileKey('movie', tile.id));
    push({ name: 'player', items: [{ key: tileKey('movie', m.id), title: m.name, image: m.poster, url: api.movieUrl(p, m) }], index: 0, live: false });
  } else {
    const s = S.content.series.find((x) => x.id === tile.id);
    markWatched(tileKey('series', tile.id));
    if (s.url) push({ name: 'player', items: [{ key: tileKey('series', s.id), title: s.name, image: s.cover, url: s.url }], index: 0, live: false });
    else push({ name: 'series', series: s });
  }
}

// ---------- shared pieces ----------

function background() {
  return h('div', { class: 'bg' }, h('div', { class: 'plane p1' }), h('div', { class: 'plane p2' }), h('div', { class: 'plane p3' }), h('div', { class: 'glow' }));
}

function button(label, onclick, opts) {
  opts = opts || {};
  return h('div', { class: 'btn' + (opts.primary ? ' primary' : '') + (opts.cls ? ' ' + opts.cls : ''), focusable: true, onclick, 'data-key': opts.key, 'data-autofocus': opts.autofocus }, label);
}

function clock() {
  const el = h('div', { class: 'clock' }, h('div', { class: 'time' }), h('div', { class: 'date' }));
  const tick = () => { el.children[0].textContent = fmtTime(Date.now()); el.children[1].textContent = fmtDate(Date.now()); };
  tick();
  const id = setInterval(tick, 15000);
  const prev = S.dispose;
  S.dispose = () => { clearInterval(id); if (prev) prev(); };
  return el;
}

let dialogEl = null;
function closeDialog() {
  if (!dialogEl) return false;
  const restore = dialogEl._restore;
  dialogEl.remove();
  dialogEl = null;
  if (restore) nav.focus(restore, { silent: true });
  return true;
}
function optionsDialog(title, options) {
  closeDialog();
  const restore = nav.focused();
  dialogEl = h('div', { class: 'dialog' },
    h('div', { class: 'dialog-box' },
      h('div', { class: 'dialog-title' }, title),
      options.map((o, i) => button(o[0], () => { closeDialog(); o[1](); }, { primary: i === 0 })),
      button('Cancelar', closeDialog)));
  dialogEl._restore = restore;
  document.body.appendChild(dialogEl);
  nav.focusFirst(dialogEl);
}

function tileCard(t, list, onChanged) {
  const key = tileKey(t.kind, t.id);
  const live = t.kind === 'live';
  const fav = S.favourites.indexOf(key) >= 0;
  return h('div', {
    class: 'tile ' + (live ? 'tile-live' : 'tile-poster'),
    focusable: true,
    'data-key': key,
    onclick: () => openTile(t, list),
    onlong: () => {
      const opts = [[fav ? '♥  Remover dos favoritos' : '♡  Adicionar aos favoritos', () => { toggleFavourite(key); onChanged(); }]];
      if (S.recent.indexOf(key) >= 0) opts.push(['🗑  Remover de assistidos recentemente', () => { removeRecent(key); onChanged(); }]);
      optionsDialog(t.title, opts);
    },
  },
  h('div', { class: 'tile-art' },
    lazyImg(t.image, t.title, live ? 'logo' : 'poster'),
    live && t.number != null ? h('span', { class: 'tile-num' }, t.number) : null,
    fav ? h('span', { class: 'tile-fav' }, '♥') : null),
  h('div', { class: 'tile-title' }, t.title));
}

// ---------- screens ----------

const SCREENS = {
  loading: (t) => h('div', { class: 'screen center' }, background(),
    h('div', { class: 'loading' }, h('div', { class: 'spinner' }), h('div', { class: 'title' }, t.step || S.loadingStep || 'Abrindo'),
      h('div', { class: 'muted' }, 'Listas grandes podem levar alguns segundos.'))),

  login: (t) => {
    t.type = t.type || 'xtream';
    const f = t.fields = t.fields || { name: 'Minha lista', server: '', user: '', pass: '', m3u: '' };
    const input = (key, label, type) => {
      const el = h('input', { class: 'input', type: type || 'text', placeholder: label, focusable: true, 'data-key': 'in-' + key, autocomplete: 'off' });
      el.value = f[key];
      el.oninput = () => { f[key] = el.value; };
      return h('label', { class: 'field' }, h('span', null, label), el);
    };
    const err = t.error || S.loginError;
    const submit = () => {
      t.error = null;
      if (t.type === 'xtream') {
        if (!f.server.trim() || !f.user.trim() || !f.pass) { t.error = 'Preencha servidor, usuário e senha.'; render(); return; }
        login({ name: f.name || 'Minha lista', type: 'xtream', server: api.normaliseServer(f.server), username: f.user.trim(), password: f.pass });
      } else {
        if (!/^https?:\/\//i.test(f.m3u.trim())) { t.error = 'O link da lista deve começar com http:// ou https://'; render(); return; }
        login({ name: f.name || 'Minha lista', type: 'm3u', m3uUrl: f.m3u.trim() });
      }
    };
    return h('div', { class: 'screen center' }, background(),
      h('div', { class: 'card login' },
        h('div', { class: 'display' }, 'Bem-vindo'),
        h('div', { class: 'muted' }, 'Entre com os dados que o seu provedor de IPTV enviou. O app não vem com canais: ele toca a sua assinatura.'),
        h('div', { class: 'row gap' },
          button('Xtream Codes', () => { t.type = 'xtream'; render(); }, { primary: t.type === 'xtream', key: 'type-x', autofocus: true }),
          button('Lista M3U', () => { t.type = 'm3u'; render(); }, { primary: t.type === 'm3u', key: 'type-m' })),
        input('name', 'Nome da lista'),
        t.type === 'xtream' ? [input('server', 'Servidor (ex.: http://exemplo.com:8080)', 'url'), input('user', 'Usuário'), input('pass', 'Senha', 'password')]
          : input('m3u', 'Link da lista M3U', 'url'),
        err ? h('div', { class: 'error' }, err) : null,
        h('div', { class: 'muted small' }, 'Selecione um campo e aperte OK para digitar.'),
        button('Entrar', submit, { primary: true, key: 'submit' })));
  },

  home: () => {
    const c = S.content;
    const collage = (images, names, logos) => h('div', { class: 'collage' + (logos ? ' logos' : '') },
      Array.from({ length: 9 }, (_, i) => h('div', { class: 'cell' }, lazyImg(images[i], names[i] || '', logos ? 'logo' : 'poster'))));
    const hubTile = (kind, label, count, images, names, logos) => h('div', { class: 'hub-tile', focusable: true, 'data-key': 'hub-' + kind, 'data-autofocus': kind === 'live', onclick: () => push({ name: 'browse', kind, categoryId: ALL }) },
      collage(images, names, logos), h('div', { class: 'hub-shade' }),
      h('div', { class: 'hub-label' }, h('div', { class: 'hub-name' }, label), h('div', { class: 'muted' }, count)));
    const side = (label, fn, key) => h('div', { class: 'side-btn', focusable: true, onclick: fn, 'data-key': key }, label);
    const exp = c.account && c.account.expDate;
    return h('div', { class: 'screen home' }, background(),
      h('div', { class: 'topbar' },
        h('div', { class: 'brand' }, h('span', { class: 'brand-dot' }, '◉'), 'PLAYER'),
        h('div', { class: 'spacer' }),
        clock(),
        button('⌕  Busca geral', () => push({ name: 'search', query: '' }), { key: 'search' }),
        button('⟳', refresh, { key: 'refresh-top' })),
      h('div', { class: 'hub' },
        hubTile('live', 'AO VIVO', fmtCount(c.channels.length) + ' canais', c.channels.map((x) => x.logo).filter(Boolean).slice(0, 9), c.channels.slice(0, 9).map((x) => x.name), true),
        hubTile('movie', 'FILMES', fmtCount(c.movies.length) + ' filmes', c.movies.map((x) => x.poster).filter(Boolean).slice(0, 9), c.movies.slice(0, 9).map((x) => x.name), false),
        hubTile('series', 'SÉRIES', fmtCount(c.series.length) + ' séries', c.series.map((x) => x.cover).filter(Boolean).slice(0, 9), c.series.slice(0, 9).map((x) => x.name), false),
        h('div', { class: 'side' },
          side('☰  Guia de TV', () => push({ name: 'guide', categoryId: ALL }), 'side-guide'),
          side('♥  Favoritos', () => push({ name: 'browse', kind: 'live', categoryId: FAV }), 'side-fav'),
          side('⟳  Atualizar lista', refresh, 'side-refresh'),
          side('⏻  Sair', logout, 'side-logout'))),
      S.loginError ? h('div', { class: 'warning' }, S.loginError) : null,
      h('div', { class: 'footer' },
        h('div', null, exp ? 'Expira em: ' + fmtFullDate(exp * 1000) : 'Assinatura sem data de expiração'),
        h('div', { class: 'spacer' }),
        h('div', null, 'Conectado: ', h('b', null, (S.profile && (S.profile.username || S.profile.name)) || ''))));
  },

  browse: (t) => {
    const kind = t.kind;
    const all = tiles(kind);
    const byKey = {};
    all.forEach((x) => { byKey[tileKey(kind, x.id)] = x; });
    const prefix = kind + ':';
    const recent = S.recent.filter((k) => k.indexOf(prefix) === 0).map((k) => byKey[k]).filter(Boolean);
    const favs = S.favourites.filter((k) => k.indexOf(prefix) === 0).map((k) => byKey[k]).filter(Boolean);
    const cats = categoriesOf(kind);
    const entries = [
      { id: ALL, icon: '▦', name: 'Todos', count: all.length },
      { id: RECENT, icon: '◷', name: 'Assistidos recentemente', count: recent.length },
      { id: FAV, icon: '♥', name: 'Favoritos', count: favs.length },
    ].concat(cats.map((x) => ({ id: x.id, icon: '›', name: x.name, count: x.count })));
    t.query = t.query || '';

    const listFor = (id) => {
      let base = id === ALL ? all : id === RECENT ? recent : id === FAV ? favs : all.filter((x) => (x.categoryId || NONE) === id);
      const q = normalise(t.query.trim());
      if (q) base = base.filter((x) => normalise(x.title).indexOf(q) >= 0);
      return base;
    };

    const grid = h('div', { class: 'grid ' + (kind === 'live' ? 'grid-live' : 'grid-poster') });
    const head = h('div', { class: 'grid-title' });
    const sub = h('div', { class: 'muted small' });
    const actions = h('div', { class: 'row gap' });
    let shown = 0;
    let current = [];
    const CHUNK = 60;

    function fill(more) {
      const end = Math.min(current.length, shown + more);
      for (let i = shown; i < end; i++) {
        const card = tileCard(current[i], current, () => render());
        if (i >= end - 12) card._onfocus = () => { if (shown < current.length) fill(CHUNK); };
        grid.appendChild(card);
      }
      shown = end;
    }

    function showCategory(id) {
      t.categoryId = id;
      current = listFor(id);
      grid.innerHTML = '';
      shown = 0;
      const entry = entries.find((e) => e.id === id) || entries[0];
      head.textContent = entry.name;
      sub.textContent = fmtCount(current.length) + ' itens · segure OK para mais opções';
      actions.innerHTML = '';
      if (id === RECENT && recent.length) actions.appendChild(button('🗑  Limpar histórico', () => optionsDialog('Limpar todo o histórico de ' + KINDS[kind] + '?', [['🗑  Sim, limpar', () => { clearRecent(kind); render(); }]]), { key: 'clear' }));
      if (kind === 'live') actions.appendChild(button('☰  Guia', () => push({ name: 'guide', categoryId: t.categoryId }), { key: 'guide' }));
      actions.appendChild(button('⌕', () => { t.searching = !t.searching; if (!t.searching) t.query = ''; render(); }, { key: 'filter' }));
      if (!current.length) {
        grid.appendChild(h('div', { class: 'empty' },
          h('div', { class: 'title' }, id === FAV ? 'Nada nos favoritos ainda' : id === RECENT ? 'Nada assistido ainda' : 'Nenhum item aqui'),
          h('div', { class: 'muted' }, id === FAV ? 'Segure OK em qualquer item para adicionar.' : id === RECENT ? 'O que você assistir aparece aqui.' : 'Escolha outra categoria.')));
      } else {
        fill(CHUNK);
      }
      catList.querySelectorAll('.cat').forEach((el) => el.classList.toggle('selected', el.dataset.id === id));
    }

    let pending = null;
    const catList = h('div', { class: 'cats' }, entries.map((e) => h('div', {
      class: 'cat' + (e.id === (t.categoryId || ALL) ? ' selected' : ''), focusable: true, 'data-key': 'cat-' + e.id, 'data-id': e.id, 'data-autofocus': e.id === t.categoryId,
      onfocus: () => { clearTimeout(pending); pending = setTimeout(() => { if (t.categoryId !== e.id) showCategory(e.id); }, 180); },
      onclick: () => showCategory(e.id),
    }, h('span', { class: 'cat-icon' }, e.icon), h('span', { class: 'cat-name' }, e.name), h('span', { class: 'cat-count' }, fmtCount(e.count)))));

    let filter = null;
    if (t.searching) {
      filter = h('input', { class: 'input', placeholder: 'Buscar em ' + KINDS[kind], focusable: true, 'data-key': 'filter-in' });
      filter.value = t.query;
      filter.oninput = () => { t.query = filter.value; showCategory(t.categoryId); };
    }

    const view = h('div', { class: 'screen' }, background(),
      h('div', { class: 'panel browse' },
        h('div', { class: 'browse-left' },
          h('div', { class: 'back', focusable: true, onclick: back, 'data-key': 'back' }, '‹  ' + KINDS[kind]),
          catList),
        h('div', { class: 'browse-right' },
          h('div', { class: 'row' }, h('div', { class: 'grow' }, head, sub), actions),
          filter,
          grid)));
    showCategory(t.categoryId || ALL);
    return view;
  },

  series: (t) => {
    const s = t.series;
    const view = h('div', { class: 'screen series' });
    const backdrop = h('div', { class: 'backdrop' });
    const body = h('div', { class: 'series-body' });
    view.appendChild(backdrop);
    view.appendChild(h('div', { class: 'series-shade' }));
    const key = tileKey('series', s.id);
    const fav = S.favourites.indexOf(key) >= 0;
    view.appendChild(h('div', { class: 'series-layout' },
      h('div', { class: 'series-left' },
        h('div', { class: 'series-cover' }, lazyImg(s.cover, s.name, 'poster')),
        button(fav ? '♥  Favorito' : '♡  Favoritar', () => { toggleFavourite(key); render(); }, { primary: fav, key: 'fav' })),
      body));

    const paint = () => {
      body.innerHTML = '';
      body.appendChild(h('div', { class: 'headline' }, s.name));
      if (s.rating) body.appendChild(h('div', { class: 'rating' }, '★ ' + s.rating));
      const d = t.detail;
      const plot = (d && d.plot) || s.plot;
      if (plot) body.appendChild(h('div', { class: 'muted plot' }, plot));
      if (t.error) { body.appendChild(h('div', { class: 'error' }, t.error)); body.appendChild(button('Tentar novamente', () => { t.error = null; t.detail = null; render(); }, { primary: true })); return; }
      if (!d) { body.appendChild(h('div', { class: 'spinner' })); return; }
      const seasons = Object.keys(d.seasons).map(Number).sort((a, b) => a - b);
      if (!seasons.length) { body.appendChild(h('div', { class: 'muted' }, 'Esta série não tem episódios no servidor.')); return; }
      if (t.season == null || seasons.indexOf(t.season) < 0) t.season = seasons[0];
      const eps = d.seasons[t.season] || [];
      const list = h('div', { class: 'episodes' }, eps.map((e, i) => h('div', {
        class: 'episode', focusable: true, 'data-key': 'ep-' + e.id, 'data-autofocus': i === 0,
        onclick: () => push({ name: 'player', live: false, index: i, items: eps.map((x) => ({ key: 'episode:' + x.id, title: 'T' + x.season + ' E' + x.number + ' · ' + x.title, subtitle: s.name, image: x.image || s.cover, url: api.episodeUrl(S.profile, x) })) }),
      }, h('div', { class: 'ep-thumb' }, lazyImg(e.image || s.cover, e.title, 'poster'), h('span', null, '▶')),
      h('div', { class: 'grow' }, h('div', { class: 'accent small' }, 'Episódio ' + e.number), h('div', { class: 'ep-title' }, e.title),
        e.plot ? h('div', { class: 'muted small clamp1' }, e.plot) : null),
      e.duration ? h('div', { class: 'faint small' }, e.duration) : null)));
      body.appendChild(h('div', { class: 'row gap seasons' }, seasons.map((n) => h('div', {
        class: 'btn' + (n === t.season ? ' primary' : ''), focusable: true, 'data-key': 'season-' + n,
        onfocus: () => { if (t.season !== n) { t.season = n; t.focusKey = 'season-' + n; render(); } },
        onclick: () => {},
      }, 'Temporada ' + n))));
      body.appendChild(list);
      backdrop.style.backgroundImage = d.backdrop ? 'url("' + d.backdrop + '")' : '';
    };
    if (!t.detail && !t.error && !t.loading) {
      t.loading = true;
      (DEMO ? Promise.resolve(demoSeries(s)) : api.seriesDetail(S.profile, s))
        .then((d) => { t.detail = d; }, (e) => { t.error = (e && e.message) || 'Não foi possível carregar os episódios.'; })
        .then(() => { t.loading = false; if (top() === t) render(); });
    }
    backdrop.style.backgroundImage = s.cover ? 'url("' + s.cover + '")' : '';
    paint();
    return view;
  },

  search: (t) => {
    const index = t.index || (t.index = ['live', 'movie', 'series'].map((k) => [k, tiles(k).map((x) => [normalise(x.title), x])]));
    const results = h('div', { class: 'results' });
    const input = h('input', { class: 'input', placeholder: 'Nome do canal, filme ou série', focusable: true, 'data-key': 'q', 'data-autofocus': true });
    input.value = t.query || '';
    const paint = () => {
      results.innerHTML = '';
      const q = normalise((t.query || '').trim());
      if (q.length < 2) { results.appendChild(h('div', { class: 'empty' }, h('div', { class: 'title' }, 'Digite pelo menos 2 letras'), h('div', { class: 'muted' }, 'A busca procura em canais, filmes e séries ao mesmo tempo. Selecione o campo e aperte OK para digitar.'))); return; }
      let any = false;
      for (const [kind, list] of index) {
        const found = list.filter((x) => x[0].indexOf(q) >= 0).map((x) => x[1]);
        if (!found.length) continue;
        any = true;
        const shown = found.slice(0, 40);
        results.appendChild(h('div', { class: 'result-row' },
          h('div', { class: 'title' }, KINDS[kind] + ' (' + fmtCount(found.length) + ')'),
          h('div', { class: 'strip ' + (kind === 'live' ? 'grid-live' : 'grid-poster') }, shown.map((x) => tileCard(x, shown, paint)))));
      }
      if (!any) results.appendChild(h('div', { class: 'empty' }, h('div', { class: 'title' }, 'Nada encontrado'), h('div', { class: 'muted' }, 'Tente outro nome ou confira a ortografia.')));
    };
    let timer = null;
    input.oninput = () => { t.query = input.value; clearTimeout(timer); timer = setTimeout(paint, 250); };
    paint();
    return h('div', { class: 'screen' }, background(), h('div', { class: 'page' }, h('div', { class: 'headline' }, 'Busca geral'), input, results));
  },

  guide: (t) => {
    const channels = channelsFor(t.categoryId);
    if (t.channelId == null && channels.length) t.channelId = channels[0].id;
    t.day = t.day == null ? 2 : t.day;
    const days = dayStarts();
    const progs = h('div', { class: 'progs' });
    const dayRow = h('div', { class: 'row gap days' });
    let token = 0;

    days.forEach((d, i) => dayRow.appendChild(h('div', {
      class: 'day' + (i === t.day ? ' selected' : ''), focusable: true, 'data-key': 'day-' + i,
      onfocus: () => {
        if (t.day === i) return;
        t.day = i;
        Array.prototype.forEach.call(dayRow.children, (el, j) => el.classList.toggle('selected', j === i));
        paintProgs();
      },
      onclick: () => {},
    }, h('div', { class: 'day-name' }, dayLabel(d)), h('div', { class: 'small' }, fmtDay(d)))));

    const paintProgs = async () => {
      const my = ++token;
      progs.innerHTML = '';
      if (!api.isXtream(S.profile) && !DEMO) { progs.appendChild(h('div', { class: 'empty' }, h('div', { class: 'title' }, 'Guia indisponível'), h('div', { class: 'muted' }, 'O guia funciona com login Xtream Codes. Listas M3U ainda não têm guia.'))); return; }
      progs.appendChild(h('div', { class: 'spinner' }));
      await sleep(200);
      if (my !== token) return;
      const list = await loadEpg(t.channelId);
      if (my !== token) return;
      const start = days[t.day];
      const end = start + 86400000;
      const now = Date.now();
      const today = list.filter((p) => p.end > start && p.start < end);
      progs.innerHTML = '';
      if (!today.length) { progs.appendChild(h('div', { class: 'empty' }, h('div', { class: 'title' }, 'Sem programação'), h('div', { class: 'muted' }, 'O servidor não enviou o guia deste canal para este dia.'))); return; }
      let nowEl = null;
      today.forEach((p) => {
        const live = now >= p.start && now < p.end;
        const past = p.end <= now;
        const desc = h('div', { class: 'prog-desc muted small', style: { display: 'none' } }, p.description || 'Sem sinopse.');
        const el = h('div', {
          class: 'prog' + (live ? ' live' : past ? ' past' : ''), focusable: true, 'data-key': 'prog-' + p.start,
          onclick: () => {
            if (live) playChannel(channels, t.channelId);
            else desc.style.display = desc.style.display === 'none' ? 'block' : 'none';
          },
        }, h('div', { class: 'row' },
          h('div', { class: 'prog-time' }, fmtTime(p.start) + ' – ' + fmtTime(p.end)),
          h('div', { class: 'prog-title grow' }, (live ? '▶  ' : '') + p.title),
          live ? h('div', null, Math.round(((now - p.start) / (p.end - p.start)) * 100) + '%') : p.hasArchive && past ? h('div', { class: 'accent' }, '⟲') : null),
        desc);
        if (live) nowEl = el;
        progs.appendChild(el);
      });
      if (nowEl && nowEl.scrollIntoViewIfNeeded) nowEl.scrollIntoViewIfNeeded(true);
    };

    const chList = h('div', { class: 'guide-channels' }, channels.map((c) => h('div', {
      class: 'guide-ch' + (c.id === t.channelId ? ' selected' : ''), focusable: true, 'data-key': 'gch-' + c.id, 'data-autofocus': c.id === t.channelId,
      onfocus: () => {
        if (t.channelId === c.id) return;
        t.channelId = c.id;
        chList.querySelectorAll('.guide-ch').forEach((el) => el.classList.toggle('selected', el === nav.focused()));
        paintProgs();
      },
      onclick: () => playChannel(channels, c.id),
    }, h('div', { class: 'guide-logo' }, lazyImg(c.logo, c.name, 'logo')), h('span', { class: 'faint' }, c.number != null ? c.number : ''), h('span', { class: 'guide-name' }, c.name))));

    paintProgs();
    return h('div', { class: 'screen' }, background(),
      h('div', { class: 'page' },
        h('div', { class: 'row' }, h('div', { class: 'headline grow' }, 'Guia de programação'), clock()),
        h('div', { class: 'panel guide' }, chList, h('div', { class: 'guide-right' }, dayRow, progs)),
        h('div', { class: 'faint small' }, '▲ ▼ canais  ·  ◀ ▶ dias e programas  ·  OK no programa atual assiste o canal  ·  OK em outro mostra a sinopse')));
  },

  player: (t) => playerScreen(t),
};

function channelsFor(id) {
  const c = S.content;
  const byKey = (prefix, list) => list.filter((k) => k.indexOf(prefix) === 0).map((k) => c.channels.find((x) => x.id === k.slice(prefix.length))).filter(Boolean);
  if (id === FAV) return byKey('live:', S.favourites);
  if (id === RECENT) return byKey('live:', S.recent);
  if (id === ALL || id == null) return c.channels;
  return c.channels.filter((x) => (x.categoryId || NONE) === id);
}

function playChannel(channels, id) {
  const list = channels.map((x) => ({ kind: 'live', id: x.id, title: x.name, image: x.logo, categoryId: x.categoryId, number: x.number }));
  const tile = list.find((x) => x.id === id);
  if (tile) openTile(tile, list);
}

function dayStarts() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - 2);
  const out = [];
  for (let i = 0; i < 8; i++) { out.push(d.getTime()); d.setDate(d.getDate() + 1); }
  return out;
}
function dayLabel(ms) {
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const diff = Math.round((ms - today.getTime()) / 86400000);
  return diff === 0 ? 'Hoje' : diff === -1 ? 'Ontem' : diff === 1 ? 'Amanhã' : weekday(ms);
}

// ---------- player ----------

function playerScreen(t) {
  const video = createVideo();
  const overlay = h('div', { class: 'overlay' });
  const spinner = h('div', { class: 'spinner big' });
  const errorBox = h('div', { class: 'player-error', style: { display: 'none' } });
  const view = h('div', { class: 'screen player' }, video.el, spinner, overlay, errorBox,
    h('div', { focusable: true, 'data-autofocus': true, class: 'player-focus' }));
  let hideTimer = null;
  let usedFallback = false;
  let programmes = [];

  const item = () => t.items[t.index];

  function showOverlay(ms) {
    overlay.style.opacity = '1';
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => { overlay.style.opacity = '0'; }, ms || 4000);
  }

  function paintOverlay() {
    const it = item();
    overlay.innerHTML = '';
    if (t.live) {
      const now = Date.now();
      const cur = programmes.find((p) => now >= p.start && now < p.end);
      const next = programmes.find((p) => p.start >= (cur ? cur.end : now));
      overlay.className = 'overlay live';
      overlay.appendChild(h('div', { class: 'row gap' },
        h('div', { class: 'ov-logo' }, lazyImg(it.image, it.title, 'logo')),
        h('div', { class: 'grow' },
          h('div', { class: 'row gap' }, h('span', { class: 'live-badge' }, '● AO VIVO'),
            h('span', { class: 'accent small' }, (it.number != null ? 'Canal ' + it.number + '  ·  ' : '') + (t.index + 1) + ' de ' + t.items.length)),
          h('div', { class: 'headline clamp1' }, it.title),
          cur ? h('div', { class: 'label clamp1' }, 'Agora: ' + fmtTime(cur.start) + ' – ' + fmtTime(cur.end) + '  ' + cur.title) : null,
          cur ? h('div', { class: 'progress' }, h('div', { style: { width: Math.round(((now - cur.start) / (cur.end - cur.start)) * 100) + '%' } })) : null,
          next ? h('div', { class: 'muted small clamp1' }, 'A seguir: ' + fmtTime(next.start) + '  ' + next.title) : null,
          h('div', { class: 'faint small' }, '▲ ▼ trocar de canal  ·  OK mostrar/ocultar  ·  Voltar sair')),
        h('div', { class: 'ov-time' }, fmtTime(now))));
    } else {
      const v = video.el;
      const dur = isFinite(v.duration) ? v.duration : 0;
      overlay.className = 'overlay vod';
      overlay.appendChild(h('div', null,
        it.subtitle ? h('div', { class: 'accent small' }, it.subtitle) : null,
        h('div', { class: 'title clamp1' }, it.title),
        h('div', { class: 'progress' }, h('div', { style: { width: dur ? (v.currentTime / dur) * 100 + '%' : '0%' } })),
        h('div', { class: 'row' }, h('div', { class: 'small' }, clockOf(v.currentTime)), h('div', { class: 'grow' }), h('div', { class: 'small' }, dur ? clockOf(dur) : '')),
        h('div', { class: 'faint small' }, (v.paused ? '▶ OK continuar' : '❚❚ OK pausar') + '  ·  ◀ ▶ voltar/avançar 10 s  ·  Voltar sair')));
    }
  }

  function load() {
    const it = item();
    errorBox.style.display = 'none';
    spinner.style.display = 'block';
    usedFallback = false;
    programmes = [];
    start(it.url);
    paintOverlay();
    showOverlay(t.live ? 4000 : 5000);
    if (t.live) {
      markWatched(it.key);
      loadEpg(it.channelId).then((list) => { if (item() === it) { programmes = list; paintOverlay(); } });
    }
  }

  function start(url) {
    video.play(url, () => {
      const it = item();
      if (!usedFallback && it.fallback) { usedFallback = true; start(it.fallback); return; }
      spinner.style.display = 'none';
      errorBox.style.display = 'block';
      errorBox.innerHTML = '';
      errorBox.appendChild(h('div', { class: 'title' }, 'Não foi possível reproduzir este conteúdo.'));
      errorBox.appendChild(h('div', { class: 'muted' }, t.live ? 'OK tenta de novo · ▲ ▼ trocam de canal · Voltar sai' : 'OK tenta de novo · Voltar sai'));
      t.failed = true;
    });
  }

  const v = video.el;
  v.addEventListener('playing', () => { spinner.style.display = 'none'; t.failed = false; });
  v.addEventListener('waiting', () => { spinner.style.display = 'block'; });
  v.addEventListener('ended', () => { if (!t.live && t.index < t.items.length - 1) { t.index++; load(); } });
  v.addEventListener('timeupdate', () => { if (!t.live && overlay.style.opacity === '1') paintOverlay(); });

  const zap = (d) => { t.index = (t.index + d + t.items.length) % t.items.length; load(); };

  keyHandler = (e) => {
    const k = e.keyCode;
    if (k === nav.KEY.BACK || k === nav.KEY.ESC || k === nav.KEY.BACKSPACE) return false;
    if (t.live) {
      if (k === nav.KEY.UP || k === nav.KEY.CH_UP) { zap(-1); return true; }
      if (k === nav.KEY.DOWN || k === nav.KEY.CH_DOWN) { zap(1); return true; }
      if (k === nav.KEY.ENTER) {
        if (t.failed) load(); else if (overlay.style.opacity === '1') overlay.style.opacity = '0'; else { paintOverlay(); showOverlay(); }
        return true;
      }
      return k === nav.KEY.LEFT || k === nav.KEY.RIGHT;
    }
    if (k === nav.KEY.LEFT || k === nav.KEY.RW) { v.currentTime = Math.max(0, v.currentTime - 10); paintOverlay(); showOverlay(); return true; }
    if (k === nav.KEY.RIGHT || k === nav.KEY.FF) { v.currentTime = v.currentTime + 10; paintOverlay(); showOverlay(); return true; }
    if (k === nav.KEY.ENTER || k === nav.KEY.PLAY_PAUSE || k === nav.KEY.PLAY || k === nav.KEY.PAUSE) {
      if (t.failed) { load(); return true; }
      if (k === nav.KEY.PLAY) v.play(); else if (k === nav.KEY.PAUSE) v.pause(); else if (v.paused) v.play(); else v.pause();
      paintOverlay(); showOverlay(); return true;
    }
    if (k === nav.KEY.UP || k === nav.KEY.DOWN) { paintOverlay(); showOverlay(); return true; }
    return false;
  };

  t.dispose = () => { clearTimeout(hideTimer); video.stop(); };
  S.dispose = t.dispose;
  setTimeout(load, 0);
  return view;
}

function clockOf(sec) {
  sec = Math.floor(sec || 0);
  const hh = Math.floor(sec / 3600);
  const mm = Math.floor((sec % 3600) / 60);
  const ss = sec % 60;
  const p = (n) => (n < 10 ? '0' + n : n);
  return (hh ? hh + ':' + p(mm) : mm) + ':' + p(ss);
}

// ---------- account ----------

async function load(profile, isNewLogin) {
  S.stack = [{ name: 'loading' }];
  render();
  const step = (s) => { S.loadingStep = s; const el = app.querySelector('.loading .title'); if (el) el.textContent = s; };
  try {
    const content = DEMO ? demoContent() : await api.fetchContent(profile, step);
    store.saveProfile(profile);
    await store.idbSet('content', content);
    S.profile = profile;
    S.content = content;
    S.epg.clear();
    S.loginError = null;
    reset({ name: 'home' });
  } catch (e) {
    S.loginError = (e && e.message) || 'Erro ao carregar.';
    reset(!isNewLogin && S.content ? { name: 'home' } : { name: 'login' });
  }
}

function login(profile) { S.loginError = null; load(profile, true); }
function refresh() { if (S.profile) load(S.profile, false); }
function logout() {
  optionsDialog('Sair e apagar os dados desta lista?', [['⏻  Sim, sair', async () => {
    await store.clearAll();
    S.profile = null; S.content = null; S.favourites = []; S.recent = [];
    reset({ name: 'login' });
  }]]);
}

async function boot() {
  nav.install();
  S.stack = [{ name: 'loading', step: 'Abrindo' }];
  render();
  if (DEMO) { await load({ name: 'Demonstração', type: 'xtream', server: 'http://demo', username: 'demo', password: 'demo' }, true); return; }
  const profile = store.loadProfile();
  if (!profile) { reset({ name: 'login' }); return; }
  S.profile = profile;
  const cached = await store.idbGet('content');
  if (cached) { S.content = cached; reset({ name: 'home' }); } else load(profile, false);
}

boot();
