import { build } from '/Users/Shared/Projects/personal/housedash/node_modules/esbuild/lib/main.js';
const H = '/Users/Shared/Projects/personal/housedash';
const here = new URL('.', import.meta.url).pathname;
const stub = {
  name: 'stubs',
  setup(b) {
    const map = [
      [/(^|\/)lib\/supabase(\.ts)?$/, here + 'src/stub-supabase.ts'],
      [/^react-router-dom$/, here + 'src/stub-router.ts'],
      [/(^|\/)hooks\/useIsMobile(\.ts)?$/, here + 'src/stub-ismobile.ts'],
    ];
    // whatever path reaches the real modules, load the stub instead
    b.onLoad({ filter: /housedash\/src\/lib\/supabase\.ts$/ }, async () => ({ contents: await (await import('fs')).promises.readFile(here + 'src/stub-supabase.ts', 'utf8'), loader: 'ts' }));
    b.onLoad({ filter: /housedash\/src\/hooks\/useIsMobile\.ts$/ }, async () => ({ contents: await (await import('fs')).promises.readFile(here + 'src/stub-ismobile.ts', 'utf8'), loader: 'ts' }));
    b.onResolve({ filter: /.*/ }, (a) => { for (const [re, to] of map) if (re.test(a.path)) return { path: to }; });
  },
};
// TV: the storm is too heavy for this GPU: far fewer drops (312*1.6 -> 312*0.6), no blurred back rain, fewer splashes
const lighter = {
  name: 'lighter-storm',
  setup(b) {
    b.onLoad({ filter: /housedash\/src\/features\/weather\/WeatherCard\.tsx$/ }, async (a) => {
      const fs = await import('fs');
      let src = await fs.promises.readFile(a.path, 'utf8');
      const from = "{ drops: 1.6, fall: 1.25, noClouds: true, fromOutside: true, backRain: 260, splash: 44,";
      if (!src.includes(from)) throw new Error('storm tune not found');
      src = src.replace(from, "{ drops: 0.6, fall: 1.25, noClouds: true, fromOutside: true, backRain: 0, splash: 16,");
      return { contents: src, loader: 'tsx' };
    });
  },
};
const r = await build({
  entryPoints: [here + 'src/entry.tsx'], bundle: true, minify: true, outfile: here + 'dist/cards.js',
  jsx: 'automatic', alias: { '@': H + '/src' }, nodePaths: [H + '/node_modules'], plugins: [stub, lighter],
  define: { 'import.meta.env': '{"DEV":false,"PROD":true,"MODE":"production","BASE_URL":"/"}', 'process.env.NODE_ENV': '"production"' },
  loader: { '.png': 'dataurl', '.svg': 'dataurl', '.jpg': 'dataurl', '.webp': 'dataurl' }, logLevel: 'warning', metafile: true,
});

import { writeFileSync } from 'fs';
writeFileSync(here + 'meta.json', JSON.stringify(r.metafile));
