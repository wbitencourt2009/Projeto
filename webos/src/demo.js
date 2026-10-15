// Fake content for ?demo=1, used only to preview the layout in a browser.
const CATS = ['Notícias', 'Esportes', 'Filmes 24h', 'Infantil', 'Documentários', 'Música', 'Variedades'];
const CH = ['Ciência HD', 'Esporte 1', 'Notícias 24h', 'Kids', 'Música Ao Vivo', 'Cinema Prime', 'Viagem', 'Culinária', 'Arena Sports', 'Mundo Animal', 'Planeta Tech', 'Clássicos'];
const MOV = ['Nebulosa', 'Código Vermelho', 'A Última Estação', 'Horizonte Sintético', 'Deserto de Neon', 'Profundezas', 'Silêncio', 'Fuga', 'Órbita 9', 'Raízes', 'Maré Alta', 'O Farol'];
const SER = ['Fronteira', 'Colônia', 'Sinal Perdido', 'Cidade Alta', 'Os Vigias', 'Estação Zero'];

export function demoContent() {
  const categories = CATS.map((n, i) => ({ id: 'c' + i, name: n }));
  const channels = [];
  for (let i = 0; i < 84; i++) channels.push({ id: String(i), number: 100 + i, name: CH[i % CH.length] + (i >= CH.length ? ' ' + (Math.floor(i / CH.length) + 1) : ''), logo: null, categoryId: 'c' + (i % CATS.length) });
  const movieCategories = ['Ação', 'Drama', 'Ficção', 'Comédia', 'Terror'].map((n, i) => ({ id: 'm' + i, name: n }));
  const movies = [];
  for (let i = 0; i < 120; i++) movies.push({ id: String(i), name: MOV[i % MOV.length] + (i >= MOV.length ? ' ' + (Math.floor(i / MOV.length) + 1) : ''), poster: null, categoryId: 'm' + (i % 5) });
  const seriesCategories = ['Ficção', 'Suspense', 'Drama'].map((n, i) => ({ id: 's' + i, name: n }));
  const series = SER.map((n, i) => ({ id: String(i), name: n, cover: null, categoryId: 's' + (i % 3), rating: '8.' + i, plot: 'Série de exemplo para pré-visualizar o layout.' }));
  return { categories, channels, movieCategories, movies, seriesCategories, series, account: { status: 'Active', expDate: Math.floor(Date.now() / 1000) + 86400 * 150 }, fetchedAt: Date.now() };
}

export function demoEpg(channelId) {
  const out = [];
  const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() - 2);
  let t = d.getTime();
  const titles = ['Jornal da Manhã', 'Programa Local', 'Documentário: Oceanos', 'Esporte Total', 'Sessão da Tarde', 'Cozinha Rápida', 'Jornal da Noite', 'Filme da Noite', 'Madrugada'];
  let i = Number(channelId) || 0;
  const end = t + 8 * 86400000;
  while (t < end) {
    const len = (60 + ((i * 37) % 4) * 30) * 60000;
    out.push({ start: t, end: t + len, title: titles[i % titles.length], description: 'Descrição de exemplo do programa.', hasArchive: i % 3 === 0 });
    t += len; i++;
  }
  return out;
}

export function demoSeries(s) {
  const seasons = {};
  for (let n = 1; n <= 3; n++) {
    seasons[n] = [];
    for (let e = 1; e <= 8; e++) seasons[n].push({ id: s.id + '-' + n + '-' + e, season: n, number: e, title: 'Capítulo ' + e, ext: 'mp4', image: null, plot: 'Episódio de exemplo.', duration: '45 min' });
  }
  return { plot: s.plot, backdrop: null, seasons };
}
