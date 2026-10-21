// Housedash 3D house renderer for the TV.
// Long-polls tv-home (`?sync=1&only=scene`), and when the house state changes (or the sky is due to change) renders the scene
// in headless Chromium and writes house.jpg + house.json (version, sky validity, lamp icon positions) into OUT_DIR, where the
// tv-home function serves them. The TV only downloads the finished picture.
import puppeteer from 'puppeteer-core';
import http from 'http';
import fs from 'fs';
import path from 'path';

const KEY = process.env.TV_HOME_KEY, SYNC = process.env.SYNC_URL, OUT = process.env.OUT_DIR || '/out';
const PORT = 8099, ROOT = path.resolve('house');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.wasm': 'application/wasm', '.json': 'application/json' };
const log = (...a) => console.log(new Date().toISOString(), ...a);

http.createServer((req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  // /out/* — the finished picture and its metadata, read by the tv-home function over the docker network
  const base = pathname.startsWith('/out/') ? path.resolve(OUT) : ROOT;
  const p = path.join(base, pathname.startsWith('/out/') ? pathname.slice(5) : pathname);
  if (!p.startsWith(base) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || (p.endsWith('.jpg') ? 'image/jpeg' : 'application/octet-stream'), 'Cache-Control': 'no-store' });
  fs.createReadStream(p).pipe(res);
}).listen(PORT, '0.0.0.0');

const browser = await puppeteer.launch({
  executablePath: process.env.CHROMIUM_PATH, headless: 'new',
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
         '--ignore-gpu-blocklist', '--window-size=1920,1080', '--hide-scrollbars'],
});
const page = await browser.newPage();
await page.setViewport({ width: 1920, height: 1080, deviceScaleFactor: 1 });
page.on('pageerror', (e) => log('page error', e.message));
let waiter = null;
await page.exposeFunction('__snap', (dataUrl, period, icons, until) => { if (waiter) waiter({ dataUrl, period, icons, until }); });
await page.evaluateOnNewDocument(() => { window.Android = { snapshot: (...a) => window.__snap(...a) }; });
await page.goto(`http://127.0.0.1:${PORT}/index.html?nobulbs=1&zoom=0.92&sx=-0.10&sy=-0.0386`, { waitUntil: 'load' });
log('page loaded');

async function render(scene) {
  const t0 = Date.now();
  const snap = new Promise((resolve, reject) => { waiter = resolve; setTimeout(() => reject(new Error('render timeout')), 180000); });
  await page.evaluate((s) => window.setState(s), scene);
  const r = await snap;
  waiter = null;
  const jpg = Buffer.from(r.dataUrl.slice(r.dataUrl.indexOf(',') + 1), 'base64');
  const version = Date.now().toString(36);
  fs.writeFileSync(path.join(OUT, 'house.jpg.tmp'), jpg);
  fs.renameSync(path.join(OUT, 'house.jpg.tmp'), path.join(OUT, 'house.jpg'));
  fs.writeFileSync(path.join(OUT, 'house.json.tmp'), JSON.stringify({ version, period: r.period, until: Number(r.until), icons: JSON.parse(r.icons), bytes: jpg.length }));
  fs.renameSync(path.join(OUT, 'house.json.tmp'), path.join(OUT, 'house.json'));
  log(`rendered ${version} ${jpg.length}B in ${Date.now() - t0}ms (${r.period})`);
  return Number(r.until);
}

let haveScene = '', until = 0, lastScene = null;

// debug scenarios: OUT/override.json {"period":"dawn"} pins the sky; deleting the file goes back to the sun. Checked every 2 s.
let lastOverride = '';
let chain = Promise.resolve();
setInterval(() => {
  let ov = '';
  try { ov = fs.readFileSync(path.join(OUT, 'override.json'), 'utf8'); } catch { ov = ''; }
  if (ov === lastOverride) return;
  lastOverride = ov;
  let period = null;
  try { period = JSON.parse(ov || '{}').period || null; } catch { /* ignore */ }
  chain = chain.then(async () => {
    await page.evaluate((p) => window.setPeriod(p), period);
    log('override', period || 'off');
    if (lastScene) until = await render(lastScene);
  }).catch((e) => log('override error', e.message));
}, 2000);
for (;;) {
  try {
    const res = await fetch(`${SYNC}?k=${KEY}&sync=1&only=scene&s=${haveScene}&wait=25`, { signal: AbortSignal.timeout(40000) });
    const j = await res.json();
    let need = false;
    if (j.scene) { lastScene = j.scene; haveScene = j.s; need = true; }
    if (until && Date.now() > until && lastScene) need = true;       // the sky has moved to the next period
    if (need && lastScene) { chain = chain.then(async () => { until = await render(lastScene); }); await chain; }
  } catch (e) {
    log('loop error', e.message);
    await new Promise((r) => setTimeout(r, 5000));
  }
}
