import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js';
import cfg from './scene.json';

// The housedash house, rendered once for the TV home. Port of House3DView/HouseModel: same fit, material
// overrides + presets, mesh rules, lights (fill, groups, screen glow), sky and grade per time of day.
// State (alarm, TVs, lights, AC, Frigate) arrives as JSON; the scene re-renders when it changes.

const q = new URLSearchParams(location.search);
const PR = 2; // the picture is baked once and shown as an image by the app, so render it big

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' });
renderer.setPixelRatio(PR);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
document.getElementById('stage').appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(45, 16 / 9, 0.5, 150);
const vp = cfg.viewport;
camera.position.set(...vp.position);
camera.lookAt(new THREE.Vector3(...vp.target));
// framing tweaks for the TV layout: scale about the centre, then shift (fractions of the view)
const ZOOM = parseFloat(q.get('zoom') ?? '0.8');
const SHIFT_X = parseFloat(q.get('sx') ?? '0'), SHIFT_Y = parseFloat(q.get('sy') ?? '0');

// ── time of day ───────────────────────────────────────────────────────────────
const RAD = Math.PI / 180, DEG = 180 / Math.PI, LAT = -23.531277, LON = -47.496924;
function sunCalc(N, lngHour, lat, tz, rising) {
  const t = N + ((rising ? 6 : 18) - lngHour) / 24;
  const M = 0.9856 * t - 3.289;
  let L = M + 1.916 * Math.sin(M * RAD) + 0.020 * Math.sin(2 * M * RAD) + 282.634;
  L = ((L % 360) + 360) % 360;
  let RA = DEG * Math.atan(0.91764 * Math.tan(L * RAD));
  RA = ((RA % 360) + 360) % 360;
  RA += Math.floor(L / 90) * 90 - Math.floor(RA / 90) * 90;
  RA /= 15;
  const sinDec = 0.39782 * Math.sin(L * RAD), cosDec = Math.cos(Math.asin(sinDec));
  const cosH = (Math.cos(90.833 * RAD) - sinDec * Math.sin(lat * RAD)) / (cosDec * Math.cos(lat * RAD));
  if (cosH > 1 || cosH < -1) return null;
  let H = rising ? 360 - DEG * Math.acos(cosH) : DEG * Math.acos(cosH);
  H /= 15;
  const T = H + RA - 0.06571 * t - 6.622;
  const UT = ((T - lngHour) % 24 + 24) % 24;
  return ((UT + tz) % 24 + 24) % 24;
}
function autoPeriod(when = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(when);
  const get = (t) => Number(parts.find((p) => p.type === t).value);
  const y = get('year'), m = get('month'), d = get('day');
  let H = get('hour'); const M = get('minute');
  if (H === 24) H = 0;
  const N1 = Math.floor(275 * m / 9), N2 = Math.floor((m + 9) / 12), N3 = 1 + Math.floor((y - 4 * Math.floor(y / 4) + 2) / 3);
  const N = N1 - N2 * N3 + d - 30, lngHour = LON / 15;
  const sunrise = sunCalc(N, lngHour, LAT, -3, true), sunset = sunCalc(N, lngHour, LAT, -3, false);
  if (sunrise == null || sunset == null) return 'day';
  const hour = H + M / 60;
  if (hour < sunrise) return 'night';
  if (hour < sunrise + 1) return 'dawn';
  if (hour < sunset - 0.6) return 'day';
  if (hour < sunset + 0.6) return 'dusk';
  return 'night';
}

// ── sky ───────────────────────────────────────────────────────────────────────
function drawSky(c, w, h) {
  const { skyTop: top, skyMid: mid, skyBot: bot, skyBot2: bot2 } = c;
  const canvas = document.createElement('canvas'), dome = document.createElement('canvas');
  canvas.width = w; canvas.height = h;
  const M = 140;
  dome.width = w + 2 * M; dome.height = h + 2 * M;
  const ctx = canvas.getContext('2d'), dctx = dome.getContext('2d');
  const sky = ctx.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, top); sky.addColorStop(0.14, mid); sky.addColorStop(1, mid);
  ctx.fillStyle = sky; ctx.fillRect(0, 0, w, h);
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  const glow = ctx.createRadialGradient(w * 0.5, h * 0.22, 0, w * 0.5, h * 0.22, h * 0.8);
  glow.addColorStop(0, `${mid}55`); glow.addColorStop(1, `${mid}00`);
  ctx.fillStyle = glow; ctx.fillRect(0, 0, w, h);
  ctx.restore();
  dctx.save();
  dctx.translate(w / 2 + M, h * 1.15 + M);
  dctx.scale(w * 1.6, h * 1.3);
  const vert = dctx.createLinearGradient(0, -1, 0, 1);
  vert.addColorStop(0, bot); vert.addColorStop(1, bot2);
  dctx.fillStyle = vert;
  dctx.beginPath(); dctx.arc(0, 0, 1, 0, Math.PI * 2); dctx.fill();
  dctx.globalCompositeOperation = 'destination-in';
  const mask = dctx.createRadialGradient(0, 0, 0, 0, 0, 1);
  mask.addColorStop(0, 'rgba(0,0,0,1)'); mask.addColorStop(0.4, 'rgba(0,0,0,1)'); mask.addColorStop(1, 'rgba(0,0,0,0)');
  dctx.fillStyle = mask;
  dctx.beginPath(); dctx.arc(0, 0, 1, 0, Math.PI * 2); dctx.fill();
  dctx.restore();
  ctx.drawImage(dome, -M, -M);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

// ── rule engine (meshRules.ts) ────────────────────────────────────────────────
function condTrue(cond, st) {
  if (cond.kind === 'device') return (st.devices[cond.device] ?? false) === cond.on;
  if (cond.kind === 'deviceValue') {
    const on = st.devices[cond.device] ?? false;
    if (cond.equals === 'OFF') return !on;
    return on && (st.values[cond.device] ?? null) === cond.equals;
  }
  if (cond.kind === 'alarm') return st.armed === cond.armed;
  return (st.frigate[cond.classifier] ?? null) === cond.equals;
}
function resolve(st) {
  const overrides = {}, hidden = new Set(), presetByMesh = {};
  const names = new Set([...Object.keys(cfg.meshOverrides), ...Object.keys(cfg.meshRules)]);
  for (const name of names) {
    const rules = cfg.meshRules[name] ?? [];
    const presetRules = rules.filter((r) => r.action.type === 'preset');
    const visRules = rules.filter((r) => r.action.type === 'visibility');
    if (presetRules.length > 0) {
      const hit = presetRules.find((r) => condTrue(r.cond, st));
      if (hit) {
        const p = cfg.presets[hit.action.preset];
        if (p) { overrides[name] = { color: p.color, opacity: p.opacity, glow: p.glow }; presetByMesh[name] = hit.action.preset; }
      }
    } else if (cfg.meshOverrides[name]) {
      const ov = cfg.meshOverrides[name];
      if (ov.preset && cfg.presets[ov.preset]) {
        const p = cfg.presets[ov.preset];
        overrides[name] = { color: p.color, opacity: p.opacity, glow: p.glow };
        presetByMesh[name] = ov.preset;
      } else overrides[name] = ov;
    }
    const vHit = visRules.find((r) => condTrue(r.cond, st));
    if (vHit && vHit.action.visible === false) hidden.add(name);
    if (cfg.meshOverrides[name]?.hidden) hidden.add(name);
  }
  return { overrides, hidden, presetByMesh };
}
const SCREEN_DEVICES = new Set(['tvSala', 'tvEscritorio', 'monitores', 'ipad']);
const unlit = new Set();
for (const [name, rules] of Object.entries(cfg.meshRules)) {
  if (rules.some((r) => r.cond.kind === 'device' && SCREEN_DEVICES.has(r.cond.device) && r.action.type === 'preset')) unlit.add(name);
}
for (const [name, dev] of Object.entries(cfg.meshTapDevice)) if (SCREEN_DEVICES.has(dev)) unlit.add(name);
const ruled = new Set([...Object.keys(cfg.meshOverrides), ...Object.keys(cfg.meshRules)]);

// ── state ─────────────────────────────────────────────────────────────────────
const DEFAULT_STATE = { armed: false, devices: {}, values: {}, frigate: {}, frigateKnown: false };
let state = JSON.parse(JSON.stringify(DEFAULT_STATE));
let period = q.get('period') || autoPeriod();
let model = null, house = null;
const glowLights = new Map();
const objectsGroup = new THREE.Group();
const origins = new Map(); // mesh uuid -> original std material
const basics = new Map();  // mesh uuid -> unlit basic material
const lightRig = new THREE.Group();
scene.add(lightRig, objectsGroup);

// ── model ─────────────────────────────────────────────────────────────────────
const isBlackName = (name) => {
  const n = name.toLowerCase();
  return name === 'IPAD' || name === 'tela_ipad' || name === 'molduraiPad' || name === 'TV65' || name === 'TV' || name === 'tv' ||
    name === 'monitores' || name === 'numeros' || n.includes('numero') || n.includes('tvzona') || n.includes('tv_65') || n.includes('tv65') ||
    n.includes('monitor') || n.includes('ipad');
};
const GLASS = new Set(['vidro', 'vidro_cozinha', 'porta_vidro', 'muro_transparente']);

function buildMaterials(root) {
  root.traverse((obj) => {
    if (!obj.isMesh) return;
    const orig = Array.isArray(obj.material) ? obj.material[0] : obj.material;
    const lum = orig?.color ? (orig.color.r + orig.color.g + orig.color.b) / 3 : 1;
    if (GLASS.has(obj.name)) {
      const wall = obj.name === 'muro_transparente';
      obj.material = new THREE.MeshStandardMaterial({
        color: new THREE.Color(wall ? '#cfe3ff' : '#dbe8ff'), roughness: wall ? 0.1 : 0.08,
        metalness: 0, transparent: true, opacity: wall ? 0.16 : 0.28, depthWrite: false, side: THREE.DoubleSide,
      });
      origins.set(obj.uuid, obj.material);
      if (wall) obj.add(new THREE.LineSegments(new THREE.EdgesGeometry(obj.geometry, 15), new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.5 })));
      return;
    }
    obj.material = new THREE.MeshStandardMaterial({
      color: new THREE.Color(isBlackName(obj.name) ? '#000000' : (lum < 0.15 ? '#050608' : '#1a1d28')),
      roughness: 0.75, metalness: 0.08, emissive: new THREE.Color('#000000'), emissiveIntensity: 0,
    });
    origins.set(obj.uuid, obj.material);
  });
}

function fitModel(g) {
  g.position.set(0, 0, 0); g.scale.set(1, 1, 1); g.updateMatrixWorld(true);
  const boxes = [];
  g.traverse((o) => { if (o.isMesh) { const b = new THREE.Box3().setFromObject(o); if (!b.isEmpty()) boxes.push(b); } });
  const n = boxes.length, lo = Math.max(0, Math.floor(n * 0.1)), hi = Math.min(n - 1, Math.floor(n * 0.9));
  const asc = (a, b) => a - b;
  const pMin = new THREE.Vector3(boxes.map((b) => b.min.x).sort(asc)[lo], boxes.map((b) => b.min.y).sort(asc)[lo], boxes.map((b) => b.min.z).sort(asc)[lo]);
  const pMax = new THREE.Vector3(boxes.map((b) => b.max.x).sort(asc)[hi], boxes.map((b) => b.max.y).sort(asc)[hi], boxes.map((b) => b.max.z).sort(asc)[hi]);
  const size = new THREE.Vector3().subVectors(pMax, pMin), centre = new THREE.Vector3().addVectors(pMin, pMax).multiplyScalar(0.5);
  const scale = 13.10 / Math.max(size.x, size.y, size.z);
  g.scale.setScalar(scale);
  g.position.set(-centre.x * scale, -centre.y * scale + 0.79, -centre.z * scale - 2);
  g.updateMatrixWorld(true);
}

function applyOverrides(res) {
  const seen = new Set();
  for (const [name, ov] of Object.entries(res.overrides)) {
    const node = model.getObjectByName(name);
    if (!node) continue;
    const isUnlit = unlit.has(name);
    node.traverse((mesh) => {
      if (!mesh.isMesh) return;
      const std = origins.get(mesh.uuid);
      if (!std) return;
      seen.add(mesh.uuid);
      const col = ov.color ?? '#' + std.color.getHexString();
      const op = ov.opacity ?? 1, glow = ov.glow ?? 0;
      if (isUnlit) {
        let b = basics.get(mesh.uuid);
        if (!b) { b = new THREE.MeshBasicMaterial(); basics.set(mesh.uuid, b); }
        b.color.set(col); b.transparent = op < 1; b.opacity = op; b.depthWrite = op >= 1;
        b.side = op < 1 ? THREE.DoubleSide : THREE.FrontSide;
        b.needsUpdate = true;
        mesh.material = b;
        return;
      }
      mesh.material = std;
      std.color.set(col); std.opacity = op;
      std.transparent = op < 1; std.depthWrite = op >= 1;
      if (op < 1) std.side = THREE.DoubleSide;
      if (glow > 0) { std.emissive.set(col); std.emissiveIntensity = glow; } else { std.emissive.set('#000000'); std.emissiveIntensity = 0; }
      std.needsUpdate = true;
    });
  }
  model.traverse((mesh) => {
    if (!mesh.isMesh || seen.has(mesh.uuid)) return;
    const std = origins.get(mesh.uuid);
    if (std && mesh.material !== std) mesh.material = std;
  });
}

function applyHidden(res) {
  for (const name of ruled) { const m = model.getObjectByName(name); if (m) m.visible = !res.hidden.has(name); }
}

function mergeGlow(preset, mesh) {
  if (!preset) return mesh ?? {};
  if (!mesh) return preset;
  const out = { ...preset };
  for (const k of Object.keys(mesh)) if (mesh[k] !== undefined) out[k] = mesh[k];
  return out;
}

function applyGlowLights(res) {
  for (const l of glowLights.values()) { l.target.parent?.remove(l.target); l.parent?.remove(l); }
  glowLights.clear();
  for (const [name, ov] of Object.entries(res.overrides)) {
    const glow = ov.glow ?? 0;
    if (glow <= 0) continue;
    const mesh = model.getObjectByName(name);
    if (!mesh || !mesh.isMesh || !mesh.geometry) continue;
    const g = mergeGlow(cfg.presets[res.presetByMesh[name]]?.light, cfg.meshGlow[name]);
    if (g.off) continue;
    const geo = mesh.geometry;
    if (!geo.boundingBox) geo.computeBoundingBox();
    const size = geo.boundingBox.getSize(new THREE.Vector3()), center = geo.boundingBox.getCenter(new THREE.Vector3());
    const dims = [[size.x, new THREE.Vector3(1, 0, 0)], [size.y, new THREE.Vector3(0, 1, 0)], [size.z, new THREE.Vector3(0, 0, 1)]];
    dims.sort((a, b) => a[0] - b[0]);
    const normal = dims[0][1].clone();
    const nAttr = geo.attributes.normal;
    if (nAttr && new THREE.Vector3(nAttr.getX(0), nAttr.getY(0), nAttr.getZ(0)).dot(normal) < 0) normal.negate();
    if (g.flip) normal.negate();
    const light = new THREE.SpotLight();
    light.angle = 1.1; light.penumbra = 0.9; light.decay = 1.4;
    mesh.add(light); mesh.add(light.target);
    const off = g.offset ?? Math.max(0.02, dims[0][0] * 0.6 + 0.02);
    light.position.copy(center).addScaledVector(normal, off);
    light.target.position.copy(center).addScaledVector(normal, off + 2);
    light.color.set(g.color ?? ov.color ?? '#ffffff');
    light.intensity = g.intensity ?? glow * 22;
    light.distance = g.distance ?? 9;
    glowLights.set(name, light);
  }
}

// ── lights ────────────────────────────────────────────────────────────────────
function beamDirs(n, yawDeg, pitchDeg, sepDeg) {
  const out = [];
  const pitch = pitchDeg * RAD;
  for (let k = 0; k < n; k++) {
    const yaw = (yawDeg + k * sepDeg) * RAD;
    const down = Math.sin(pitch), side = Math.cos(pitch);
    out.push([Math.sin(yaw) * side, -down, Math.cos(yaw) * side]);
  }
  return out;
}
const BEAM = { yaw: 0, pitch: 60, cone: 45, penumbra: 0.5 };
function emitter(e, color, intensity, distance, decay) {
  const group = new THREE.Group();
  const n = e.beams >= 1 ? Math.min(4, Math.round(e.beams)) : 0;
  if (n === 0) {
    const l = new THREE.PointLight(color, intensity, distance, decay);
    l.position.set(...e.pos);
    group.add(l);
    return group;
  }
  const angle = Math.min(Math.PI / 2 - 0.01, Math.max(0.05, (e.cone ?? BEAM.cone) * RAD));
  const sep = e.sep ?? (n >= 2 ? Math.min(180, 360 / n) : 0);
  for (const dir of beamDirs(n, e.yaw ?? BEAM.yaw, e.pitch ?? BEAM.pitch, sep)) {
    const s = new THREE.SpotLight(color, intensity, distance, angle, e.penumbra ?? BEAM.penumbra, decay);
    s.position.set(...e.pos);
    const t = new THREE.Object3D();
    t.position.set(e.pos[0] + dir[0], e.pos[1] + dir[1], e.pos[2] + dir[2]);
    group.add(s, t);
    s.target = t;
  }
  return group;
}

function buildLights(L) {
  lightRig.clear();
  lightRig.add(new THREE.AmbientLight('#c8cce8', L.ambient));
  lightRig.add(new THREE.HemisphereLight(L.sky, L.ground, L.hemi));
  for (const e of cfg.fillLights) lightRig.add(emitter(e, e.color ?? '#ffffff', (e.intensity ?? 2) * L.fill, e.distance ?? 6, e.decay ?? 1));
  const up = new THREE.Vector3(0, 1, 0), dirv = new THREE.Vector3();
  camera.getWorldDirection(dirv);
  const right = new THREE.Vector3().crossVectors(dirv, up).normalize();
  const tgt = new THREE.Object3D(); lightRig.add(tgt);
  const key = new THREE.DirectionalLight('#ffffff', L.sun);
  key.position.copy(camera.position).addScaledVector(up, 14).addScaledVector(right, -6); key.target = tgt;
  const fill = new THREE.DirectionalLight('#ffffff', L.sun * 0.55);
  fill.position.copy(camera.position); fill.target = tgt;
  lightRig.add(key, fill);
  const s1 = new THREE.SpotLight('#ffffff', 12, 0, 0.6, 0.7); s1.position.set(0, 6, 0);
  const s2 = new THREE.SpotLight('#dde4ff', 8, 0, 0.5, 0.8); s2.position.set(2, 5, -1);
  lightRig.add(s1, s2);
  const lg = { garden: 'jardim', quarto: 'quarto', corridor: 'corredor', top: 'fundo' };
  for (const g of cfg.lightGroups) {
    if (!state.devices[lg[g.id]]) continue;
    for (const e of g.lights) lightRig.add(emitter(e, e.color ?? g.tint, e.intensity ?? g.intensity, e.distance ?? g.distance ?? 4, e.decay ?? g.decay ?? 2));
  }
}

// ── objects (cars) ────────────────────────────────────────────────────────────
const loader = new GLTFLoader();
const draco = new DRACOLoader();
draco.setDecoderPath('draco/');
loader.setDRACOLoader(draco);
const objCache = new Map();
function loadObj(file) {
  if (!objCache.has(file)) objCache.set(file, new Promise((res, rej) => loader.load('objects/' + file, (g) => res(g.scene), undefined, rej)));
  return objCache.get(file);
}
async function buildObjects() {
  objectsGroup.clear();
  for (const o of cfg.objects) {
    if (o.visible === false) continue;
    if (state.frigateKnown && o.vaga && o.estado && state.frigate[o.vaga] !== o.estado) continue; // each car answers to its own spot
    let src;
    try { src = await loadObj(o.file); } catch (e) { console.log('obj fail', o.file, String(e && e.message || e)); continue; }
    const m = src.clone(true);
    const total = new THREE.Box3().setFromObject(m);
    const span = Math.max(total.max.x - total.min.x, total.max.z - total.min.z);
    const flat = [];
    m.traverse((x) => {
      if (!x.isMesh) return;
      const b = new THREE.Box3().setFromObject(x);
      const sp = Math.max(b.max.x - b.min.x, b.max.z - b.min.z);
      if (b.max.y - b.min.y <= 0.02 * sp && sp >= 0.8 * span) flat.push(x);
    });
    flat.forEach((x) => x.removeFromParent());
    const colors = o.meshColors ?? {};
    m.traverse((x) => {
      if (!x.isMesh) return;
      x.material = new THREE.MeshStandardMaterial({ color: new THREE.Color(colors[x.name] ?? '#24262b'), roughness: 1, metalness: 0 });
    });
    const outer = new THREE.Group();
    outer.position.set(...o.pos);
    outer.rotation.y = (o.rotY * Math.PI) / 180;
    outer.scale.setScalar(o.scale || 1);
    const ground = new THREE.Group();
    ground.add(m);
    outer.add(ground);
    outer.updateMatrixWorld(true);
    ground.updateWorldMatrix(true, true);
    const box = new THREE.Box3().setFromObject(ground);
    if (Number.isFinite(box.min.y)) ground.position.y = (o.pos[1] - box.min.y) / (o.scale || 1);
    objectsGroup.add(outer);
  }
}

// ── render ────────────────────────────────────────────────────────────────────

// ── projected ground shadows (port of housedash DirectionalShadow) ────────────────────────────────────────
// Each shadow of the period's lighting preset bakes the house + objects silhouette, projected along the sun direction, into a
// top-down target, and shows it on a plane under the floor with its own colour / strength / blur / fade.
const SH_SIZE = 120, SH_RES = 2048, SH_H = 22;
const shBakeMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
const shCam = new THREE.OrthographicCamera(-SH_SIZE / 2, SH_SIZE / 2, SH_SIZE / 2, -SH_SIZE / 2, -200, 200);
shCam.position.set(0, 0, 0); shCam.up.set(0, 0, -1); shCam.lookAt(0, -1, 0); shCam.updateMatrixWorld();
const shGroup = new THREE.Group();
scene.add(shGroup);
const shSlots = [];
let shKey = '';
function shSlot(i) {
  if (shSlots[i]) return shSlots[i];
  const rt = new THREE.WebGLRenderTarget(SH_RES, SH_RES);
  rt.texture.minFilter = THREE.LinearFilter; rt.texture.magFilter = THREE.LinearFilter; rt.texture.generateMipmaps = false;
  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false,
    uniforms: {
      uMap: { value: rt.texture }, uColor: { value: new THREE.Color('#0a0716') }, uOpacity: { value: 0 }, uSize: { value: SH_SIZE },
      uDir: { value: new THREE.Vector2() }, uFadeNear: { value: 1e6 }, uFadeFar: { value: 1e6 + 1 }, uBlur: { value: 0 },
    },
    vertexShader: `varying vec3 vW; void main(){ vec4 wp = modelMatrix * vec4(position,1.0); vW = wp.xyz; gl_Position = projectionMatrix * viewMatrix * wp; }`,
    fragmentShader: `
      uniform sampler2D uMap; uniform vec3 uColor; uniform float uOpacity; uniform float uSize; uniform vec2 uDir;
      uniform float uFadeNear; uniform float uFadeFar; uniform float uBlur; varying vec3 vW;
      void main(){
        vec2 uv = vec2(0.5 + vW.x / uSize, 0.5 - vW.z / uSize);
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) discard;
        float s = 0.0;
        for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) s += texture2D(uMap, uv + vec2(float(i), float(j)) * uBlur).a;
        s /= 25.0;
        float fade = 1.0 - smoothstep(uFadeNear, uFadeFar, dot(vW.xz, uDir));
        float a = s * uOpacity * fade;
        if (a <= 0.001) discard;
        gl_FragColor = vec4(uColor, a);
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(SH_SIZE, SH_SIZE), mat);
  mesh.rotation.x = -Math.PI / 2; mesh.position.y = -0.05; mesh.raycast = () => null;
  shGroup.add(mesh);
  return (shSlots[i] = { rt, mat, mesh });
}
function visibleChain(o) { for (let n = o; n; n = n.parent) if (!n.visible) return false; return true; }
function updateShadows(L) {
  const defs = (L.shadows || []).filter((d) => d.on);
  const casters = [];
  const collect = (root) => root.traverse((o) => { if (o.isMesh && o.geometry && visibleChain(o)) casters.push(o); });
  collect(house); collect(objectsGroup);
  const key = period + '|' + JSON.stringify(defs) + '|' + casters.map((m) => m.uuid).join(',');
  shSlots.forEach((sl) => { sl.mesh.visible = false; });
  defs.forEach((sh, i) => {
    const sl = shSlot(i);
    sl.mesh.visible = true;
    const rad = sh.dir * RAD, sp = [Math.cos(rad) * sh.reach, SH_H, Math.sin(rad) * sh.reach];
    const len = Math.hypot(sp[0], sp[1], sp[2]) || 1, lx = -sp[0] / len, ly = -sp[1] / len, lz = -sp[2] / len;
    const u = sl.mat.uniforms, fr = sh.fadeAngle * RAD;
    u.uOpacity.value = sh.strength;
    u.uDir.value.set(Math.cos(fr), Math.sin(fr));
    u.uFadeNear.value = sh.fadeOn ? sh.fadeStart : 1e6;
    u.uFadeFar.value = sh.fadeOn ? sh.fadeEnd : 1e6 + 1;
    u.uBlur.value = sh.blur / SH_RES;
    if (key === shKey) return;
    const sm = new THREE.Matrix4().set(1, -lx / ly, 0, 0, 0, 0, 0, 0, 0, -lz / ly, 1, 0, 0, 0, 0, 1);
    const tmp = new THREE.Scene();
    scene.updateMatrixWorld(true);
    for (const m of casters) {
      const c = new THREE.Mesh(m.geometry, shBakeMat);
      c.matrixAutoUpdate = false;
      c.matrix.copy(sm).multiply(m.matrixWorld);
      c.frustumCulled = false;
      tmp.add(c);
    }
    tmp.updateMatrixWorld(true);
    const prevRT = renderer.getRenderTarget(), prevClear = new THREE.Color(), prevA = renderer.getClearAlpha();
    renderer.getClearColor(prevClear);
    renderer.setRenderTarget(sl.rt); renderer.setClearColor(0x000000, 0); renderer.clear(true, true, true);
    renderer.render(tmp, shCam);
    renderer.setRenderTarget(prevRT); renderer.setClearColor(prevClear, prevA);
  });
  shKey = key;
}

const grade = document.getElementById('grade');
let skyKey = '';
function render() {
  if (!model) return;
  const L = cfg.lighting[period];
  const w = 1920, h = 1080; // fixed 16:9 canvas (the app scales the picture to the screen)
  renderer.setSize(w, h, false);
  renderer.domElement.style.width = w + 'px'; renderer.domElement.style.height = h + 'px';
  camera.aspect = w / h;
  camera.zoom = ZOOM;
  camera.setViewOffset(w, h, -SHIFT_X * w, -SHIFT_Y * h, w, h);
  camera.updateProjectionMatrix();
  const key = period + w + h;
  if (key !== skyKey) { scene.background = drawSky(L, Math.round(w), Math.round(h)); skyKey = key; }
  grade.style.display = L.gradeOn ? 'block' : 'none';
  grade.style.background = L.gradeColor;
  grade.style.mixBlendMode = L.gradeBlend;
  grade.style.opacity = String(L.gradeStrength);
  const res = resolve(state);
  applyOverrides(res);
  applyHidden(res);
  applyGlowLights(res);
  buildLights(L);
  updateShadows(L);
  renderer.render(scene, camera);
  document.body.dataset.period = period;
  document.body.dataset.ready = '1';
  snapshot(L, w, h);
}

const BULB = new Path2D('M12 2a7 7 0 0 1 4 12.74V17a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1v-2.26A7 7 0 0 1 12 2z');
function drawBulb(ctx, cx, cy, size, on) {
  ctx.save();
  ctx.globalAlpha = on ? 1 : 0.8;
  ctx.translate(cx - size / 2, cy - size / 2);
  ctx.scale(size / 24, size / 24);
  ctx.strokeStyle = 'rgba(0,0,0,0.55)'; ctx.lineWidth = 1.6; ctx.lineJoin = 'round'; ctx.stroke(BULB); // contrast on bright floors
  ctx.shadowColor = on ? 'rgba(255,208,96,0.8)' : 'rgba(0,0,0,0)';
  ctx.shadowBlur = on ? 6 : 0;
  ctx.fillStyle = on ? '#ffe4a0' : 'rgba(255,255,255,0.9)';
  ctx.fill(BULB);
  ctx.shadowBlur = 0;
  ctx.fillStyle = on ? '#ffd060' : 'rgba(255,255,255,0.6)';
  ctx.beginPath(); ctx.roundRect(9, 18, 6, 1.5, 0.5); ctx.fill();
  ctx.fillStyle = on ? '#ffc030' : 'rgba(255,255,255,0.4)';
  ctx.beginPath(); ctx.roundRect(10, 19.5, 4, 1.5, 0.5); ctx.fill();
  if (on) {
    ctx.strokeStyle = '#ffe4a0'; ctx.lineWidth = 1.5; ctx.lineCap = 'round';
    for (const [x1, y1, x2, y2] of [[12, 0.5, 12, 1.5], [4.2, 3.2, 5, 4], [19.8, 3.2, 19, 4], [2, 10, 3, 10], [22, 10, 21, 10]]) {
      ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
    }
  }
  ctx.restore();
}

// Bake the frame (WebGL + the time-of-day grade) into one JPEG and hand it to the app, which shows it as a plain
// image and drops this page: no WebGL cost while the home is in use.
// When the sky next changes period (dawn/day/dusk/night): the picture stays valid until then, so the app re-renders only
// when the house state changes or this moment passes, not on a timer.
function validUntil() {
  if (q.get('period') || forcedPeriod) return Date.now() + 24 * 3600e3;
  const p0 = autoPeriod();
  for (let k = 1; k <= 288; k++) if (autoPeriod(new Date(Date.now() + k * 5 * 60000)) !== p0) return Date.now() + k * 5 * 60000;
  return Date.now() + 24 * 3600e3;
}
const LAMP_NAMES = { garden: 'Jardim', quarto: 'Quarto', corridor: 'Corredor', top: 'Fundo' };
function dbgDraw(o, L, w, h) {
  o.width = renderer.domElement.width; o.height = renderer.domElement.height;
  const ctx = o.getContext('2d');
  ctx.drawImage(renderer.domElement, 0, 0);
  camera.updateMatrixWorld();
  const lg = { garden: 'jardim', quarto: 'quarto', corridor: 'corredor', top: 'fundo' };
  const k = o.width / 1920;
  for (const g of cfg.lightGroups) {
    const v = new THREE.Vector3(g.iconPoint[0], g.iconPoint[1] + 0.2, g.iconPoint[2]).project(camera);
    drawBulb(ctx, (v.x * 0.5 + 0.5) * o.width, (-v.y * 0.5 + 0.5) * o.height, 44 * (({ garden: 1.1, quarto: 1.04, corridor: 1.02, top: 1.02 })[g.id] ?? 1) * k, !!state.devices[lg[g.id]]);
  }
}
function snapshot(L, w, h) {
  if (!(window.Android && Android.snapshot)) { if (q.get('dbg')) { const o = document.getElementById('dbg') ?? document.body.appendChild(Object.assign(document.createElement('canvas'), { id: 'dbg' })); o.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;z-index:9'; dbgDraw(o, L, w, h); } return; }
  const out = document.createElement('canvas');
  out.width = renderer.domElement.width; out.height = renderer.domElement.height;
  const ctx = out.getContext('2d');
  ctx.drawImage(renderer.domElement, 0, 0);
  if (L.gradeOn) {
    ctx.globalCompositeOperation = L.gradeBlend;
    ctx.globalAlpha = L.gradeStrength;
    ctx.fillStyle = L.gradeColor;
    ctx.fillRect(0, 0, out.width, out.height);
  }
  // device icons (lamps only for now), projected from the scene: housedash's BulbIcon, view only
  camera.updateMatrixWorld();
  const lg = { garden: 'jardim', quarto: 'quarto', corridor: 'corredor', top: 'fundo' };
  const scales = { garden: 1.1, quarto: 1.04, corridor: 1.02, top: 1.02 }; // the back light is the same size as the corridor one
  const k = out.width / 1920;
  if (!q.get('nobulbs')) for (const g of cfg.lightGroups) { // `nobulbs`: the app draws the lamp icons itself (live state, no render wait)
    const on = !!state.devices[lg[g.id]];
    const v = new THREE.Vector3(g.iconPoint[0], g.iconPoint[1] + 0.2, g.iconPoint[2]).project(camera);
    const x = (v.x * 0.5 + 0.5) * out.width, y = (-v.y * 0.5 + 0.5) * out.height;
    drawBulb(ctx, x, y, 44 * (scales[g.id] ?? 1) * k, on);
  }
  const icons = cfg.lightGroups.map((g) => {
    const v = new THREE.Vector3(g.iconPoint[0], g.iconPoint[1] + 0.2, g.iconPoint[2]).project(camera);
    return { id: g.id, name: LAMP_NAMES[g.id], x: (v.x * 0.5 + 0.5) * 1920, y: (-v.y * 0.5 + 0.5) * 1080, size: 44 * (scales[g.id] ?? 1), on: !!state.devices[lg[g.id]] };
  });
  Android.snapshot(out.toDataURL('image/jpeg', 0.92), period, JSON.stringify(icons), String(validUntil()));
}
let pending = false;
function schedule() { if (pending) return; pending = true; requestAnimationFrame(() => { pending = false; render(); }); }

window.setState = async (s) => {
  window.__last = s;
  const next = JSON.parse(JSON.stringify(DEFAULT_STATE));
  if (s) {
    next.armed = !!s.alarmArmed;
    next.devices = s.devices ?? {};
    next.values = s.values ?? {};
    if (s.frigate) { next.frigate = s.frigate; next.frigateKnown = true; }
  }
  const objChanged = JSON.stringify([next.frigate, next.frigateKnown]) !== JSON.stringify([state.frigate, state.frigateKnown]);
  state = next;
  if (objChanged || !objectsGroup.children.length) await buildObjects();
  schedule();
};
let forcedPeriod = null; // debug: the renderer pins the sky to a period (override.json); null = by the sun
window.setPeriod = (p) => { forcedPeriod = p || null; period = p || autoPeriod(); schedule(); };
addEventListener('resize', schedule);
window.__dbg = () => ({ objs: objectsGroup.children.length, pos: objectsGroup.children.map((c) => c.position.toArray()), cache: [...objCache.keys()] });
setInterval(() => { if (!q.get('period') && !forcedPeriod) { const p = autoPeriod(); if (p !== period) { period = p; schedule(); } } }, 60000);

loader.load('casa.glb', async (g) => {
  house = new THREE.Group();
  model = g.scene;
  house.add(model);
  buildMaterials(model);
  scene.add(house);
  fitModel(house);
  const st = q.get('state');
  if (window.__last !== undefined) await window.setState(window.__last);
  else if (st) await window.setState(JSON.parse(st));   // no app attached (browser test): render the URL state
  schedule();
});
