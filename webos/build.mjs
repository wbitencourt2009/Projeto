// Bundles src/ for webOS 4+ (Chromium 53) into dist/, ready for ares-package.
import { build } from 'esbuild';
import { cpSync, mkdirSync, rmSync, readdirSync } from 'node:fs';

rmSync('dist', { recursive: true, force: true });
mkdirSync('dist');

await build({
  entryPoints: ['src/main.js'],
  bundle: true,
  minify: true,
  format: 'iife',
  target: ['chrome53'],
  outfile: 'dist/app.js',
  legalComments: 'none',
});

for (const f of readdirSync('static')) cpSync('static/' + f, 'dist/' + f, { recursive: true });
console.log('built dist/');
