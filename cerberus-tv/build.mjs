import { build } from '/Users/Shared/Projects/personal/cerberus/frontend/node_modules/esbuild/lib/main.js';
import fs from 'fs';
const C = '/Users/Shared/Projects/personal/cerberus/frontend';
const here = new URL('.', import.meta.url).pathname;
const out = here + '../app/assets/cerberus/';
fs.mkdirSync(out + 'fonts', { recursive: true });
await build({
  entryPoints: [here + 'src/tv.tsx'], bundle: true, minify: true, outfile: out + 'tv.js', jsx: 'automatic',
  alias: { '@c': C + '/src' }, nodePaths: [C + '/node_modules'],
  external: ['/fonts/*', '/cerberus-logo.svg', '/logo.png'],
  loader: { '.png': 'dataurl', '.svg': 'dataurl' }, logLevel: 'warning',
  define: { 'import.meta.env': '{"DEV":false,"PROD":true,"MODE":"production","BASE_URL":"/"}', 'process.env.NODE_ENV': '"production"' },
});
for (const f of ['manrope-latin.woff2', 'manrope-latin-ext.woff2', 'inter-latin.woff2']) fs.copyFileSync(C + '/public/fonts/' + f, out + 'fonts/' + f);
fs.copyFileSync(C + '/public/cerberus-logo.svg', out + 'cerberus-logo.svg');
fs.copyFileSync(here + 'index.html', out + 'index.html');
console.log('built');
