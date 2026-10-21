import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SkyBackdropContext } from '@/features/house3d/skyBackdrop';
import { WeatherCard } from '@/features/weather/WeatherCard';
import { SolarHomeCard } from '@/features/solar/SolarHomeCard';
import { TaurusMiniCard } from '@/features/taurus/TaurusMiniCard';
import { useLayoutEffect, useRef, useState } from 'react';
import { useDaylightPeriod, setDaylightSim } from '@/features/house3d/sceneLighting';
import { setWeatherSim, clearWeatherSim } from '@/features/weather/weatherSim';

// The housedash column of cards (weather, solar, Taurus), the same components and the same hooks, over a fake
// Supabase that serves the snapshot pushed by the app. The column is 280 px wide there; here it is scaled to the TV.
const qc = new QueryClient({ defaultOptions: { queries: { refetchOnWindowFocus: false, retry: false, staleTime: 0 } } });
const W = window.innerWidth, H = window.innerHeight; // the slot on the TV home (the WebView is exactly that size)

function Column() {
  const ref = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.62);
  const [width, setWidth] = useState(W / 0.62); // the final values from the first frame: no resize flicker
  const period = useDaylightPeriod();
  // The column is scaled to the slot's HEIGHT (the three cards must fit), and then made as wide in CSS px as it takes to fill
  // the slot's WIDTH at that scale: wider cards, same proportions, no distortion.
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const fit = () => {
      const zoom = Number(getComputedStyle(el.parentElement!).zoom) || 1;
      const h = el.getBoundingClientRect().height / zoom;
      if (h <= 0) return;
      const s = 0.62; // fixed type scale (the slot is no longer filled to its height)
      const w = Math.max(280, W / s);
      setScale((o) => (Math.abs(o - s) / s > 0.01 ? s : o));
      setWidth((o) => (Math.abs(o - w) > 3 ? w : o));
    };
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return (
    // `zoom` rather than `transform: scale`: it lays the column out at the final size, with no composited layers
    <div style={{ position: 'absolute', right: 0, top: 0, bottom: 0, display: 'flex', flexDirection: 'column', justifyContent: 'center', width, zoom: scale } as React.CSSProperties}>
      <div ref={ref} className={(period === 'dawn' ? 'dawn-ink ' : '') + 'p-' + period} style={{ display: 'flex', flexDirection: 'column', gap: 12, width }}>
        <WeatherCard />
        <SolarHomeCard />
        <TaurusMiniCard />
      </div>
    </div>
  );
}

// ── debug simulation (adb: am start ... HomeEntry --es sim solar|taurus|off) ─────────────────────────────
// Freezes the clock at 12:30 BRT for "solar" (the sun must be up for the plant to count as generating) and bends the
// table snapshot: the plant at 6.5 kW is over 100% of October's 6.2 kW ceiling (the purple neon tier); for "taurus" one
// meal an hour ago puts the next one 2 h away.
const RealDate = Date;
let sim = 'off';
function patchClock(ms: number | null) {
  if (ms == null) { (window as unknown as { Date: DateConstructor }).Date = RealDate; return; }
  const shift = ms - RealDate.now();
  class FakeDate extends RealDate {
    constructor(...a: unknown[]) { if (a.length) super(...(a as [number])); else super(RealDate.now() + shift); }
    static now() { return RealDate.now() + shift; }
  }
  (window as unknown as { Date: DateConstructor }).Date = FakeDate as unknown as DateConstructor;
}
function simulated(t: Record<string, Record<string, unknown>[]>) {
  if (sim === 'off') return t;
  const flags = sim.split(',');
  const now = new Date();
  const iso = now.toISOString();
  const out = JSON.parse(JSON.stringify(t));
  if (flags.includes('solar')) {
    out.solar_plants = (out.solar_plants ?? []).map((p: Record<string, unknown>) => ({ ...p, current_power: 6500, today_energy: 21.4, updated_at: iso }));
    out.solar_inverters = (out.solar_inverters ?? []).map((i: Record<string, unknown>) => ({ ...i, ac_power: 2170, energy_today: 7.1, updated_at: iso }));
  }
  if (flags.includes('clear')) {
    out.weather_current = (out.weather_current ?? []).map((w: Record<string, any>) => ({ ...w, current: { ...w.current, weather_code: 0, is_day: 1 } }));
  }
  if (flags.includes('nofeed') || flags.includes('eat')) out.taurus_feed_events = [];
  if (flags.includes('done')) {
    // every scheduled meal fed today (08:00, 12:00, 15:00, 18:00 BRT, local clock of the page)
    out.taurus_feed_events = [8, 12, 15, 18].map((h, i) => { const d = new Date(); d.setHours(h, 0, 0, 0); return { id: 'sim' + i, fed_at: new RealDate(d.getTime()).toISOString(), portion_g: 125, source: 'sim', notes: null, created_at: iso, source_event_id: null }; });
  }
  if (flags.includes('taurus')) {
    out.taurus_feed_events = [{ id: 'sim', fed_at: new RealDate(now.getTime() - 3600_000).toISOString(), portion_g: 125, source: 'sim', notes: null, created_at: iso, source_event_id: null }];
  }
  return out;
}
(window as unknown as { setTables: (t: unknown) => void }).setTables = (t) => {
  (window as unknown as { __raw: unknown }).__raw = t;
  (window as unknown as { __tables: unknown }).__tables = simulated(t as Record<string, Record<string, unknown>[]>);
  refresh();
};
(window as unknown as { setSim: (k: string) => void }).setSim = (k) => {
  sim = k;
  const fl = k.split(',');
  const wt = (['clear', 'rain', 'cloudy', 'storm', 'fog', 'snow'] as const).find((x) => fl.includes(x));
  if (wt) setWeatherSim({ type: wt, night: false }); else clearWeatherSim();
  setDaylightSim((['dawn', 'day', 'dusk', 'night'].find((x) => fl.includes(x)) ?? 'auto') as 'auto');
  // solar: 12:30 BRT (sun up); dawn / nofeed: 06:15 BRT (just after sunrise, before the first scheduled meal)
  const day = new RealDate().toISOString().slice(0, 10);
  patchClock(fl.includes('night') ? RealDate.parse(day + 'T02:00:00Z') + 24 * 3600_000 : (fl.includes('solar') || fl.includes('eat') || fl.includes('day')) ? RealDate.parse(day + 'T15:30:00Z') : (fl.includes('dawn') || fl.includes('nofeed')) ? RealDate.parse(day + 'T09:15:00Z') : null);
  const raw = (window as unknown as { __raw?: unknown }).__raw;
  if (raw) (window as unknown as { setTables: (t: unknown) => void }).setTables(raw);
  (window as unknown as { remount?: () => void }).remount?.();
};

export function refresh() { void qc.invalidateQueries(); }
(window as unknown as { refreshCards: () => void }).refreshCards = refresh;

const root = createRoot(document.getElementById('root')!);
let gen = 0;
function mount() {
  root.render(
    <QueryClientProvider client={qc}>
      <SkyBackdropContext.Provider value={true}>
        <Column key={gen++} />
      </SkyBackdropContext.Provider>
    </QueryClientProvider>,
  );
}
mount();
// the hooks read the clock once when they mount: after the sim moves it, remount so they pick it up at once
(window as unknown as { remount: () => void }).remount = mount;
