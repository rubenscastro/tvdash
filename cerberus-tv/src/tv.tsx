import { useCallback, useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import "@c/index.css";
import "./tvx.css";
import { api, EventDetail, EventItem } from "@c/api";
import { Capa } from "@c/components/CapaDeEvento";
import { Icone } from "@c/components/Icone";
import { descreverReconhecimento, descreverSituacao, motivoLimpo, nomeDaCamera, nomeDaZona, nomeDoEvento, traduzirLabel } from "@c/vocabulario";

// The Cerberus screen for the TV: the same header (tabs left, logo in the middle), the same covers and colours as the Cerberus
// panel, driven by the remote. Live is a native layer (RTSP on the hardware decoder) that the activity shows under this page;
// events and alerts come from the Cerberus API (the activity adds the key), and the clip plays in a native video layer placed
// over the modal's main image.

type Tab = "live" | "eventos" | "alertas";
const TABS: { id: Tab; rotulo: string; icone: "monitor" | "olho" | "sino" }[] = [
  { id: "live", rotulo: "Live", icone: "monitor" },
  { id: "eventos", rotulo: "Eventos", icone: "olho" },
  { id: "alertas", rotulo: "Alertas", icone: "sino" },
];
const COLS = 3, PAGE = 24;

declare const TvBridge: {
  setTab(t: string): void; focusLive(i: number): void; exit(): void;
  playClip(id: number, x: number, y: number, w: number, h: number, innerWidth: number): void; toggleClip(): void; stopClip(): void;
};

type Zone = "tabs" | "live" | "grid" | "modal" | "lightbox" | "drawer";

// ── filters (the Cerberus ones, in a drawer) ────────────────────────────────────────────────────────────────
type Filt = { camera: string[]; cameraExcl: boolean; label: string[]; labelExcl: boolean; recortes: string[]; status: string[]; dia: string; hora: string[] };
const OCULTAS = ["fundo", "corredor"];   // hidden by default: the back and hallway cameras
const NOFILT: Filt = { camera: OCULTAS, cameraExcl: true, label: [], labelExcl: false, recortes: ["com"], status: [], dia: "", hora: [] };
const CAMERAS = ["cam_b23ede0c", "rua", "garagem", "corredor", "fundo"];
const OBJETOS = ["person", "car", "motorcycle", "bicycle", "dog", "cat", "package", "bus", "truck"];
const RECORTES = [{ value: "com", label: "Registrados" }, { value: "sem", label: "Arquivados" }, { value: "fav", label: "Favoritos" }];
const GRUPO: Record<string, string> = { com: "with_zone", sem: "without_zone", fav: "favorites" };
const STATUS = [{ value: "active", label: "Em andamento" }, { value: "ended", label: "Finalizados" }];
const DIAS = [{ value: "", label: "Todo o período" }, { value: "hoje", label: "Hoje" }, { value: "ontem", label: "Ontem" }, { value: "7d", label: "Últimos 7 dias" }, { value: "30d", label: "Últimos 30 dias" }];
const HORAS = [{ value: "00:00-06:00", label: "Madrugada (00–06)" }, { value: "06:00-12:00", label: "Manhã (06–12)" }, { value: "12:00-18:00", label: "Tarde (12–18)" }, { value: "18:00-23:59", label: "Noite (18–24)" }];

type Row = { kind: "toggle" | "radio" | "mode" | "clear" | "apply"; sec: string; value: string; label: string; on: boolean };

function drawerRows(f: Filt): { title: string; rows: Row[] }[] {
  const multi = (sec: string, opts: { value: string; label: string }[], sel: string[]): Row[] => opts.map((o) => ({ kind: "toggle", sec, value: o.value, label: o.label, on: sel.includes(o.value) }));
  const single = (sec: string, opts: { value: string; label: string }[], sel: string): Row[] => opts.map((o) => ({ kind: "radio", sec, value: o.value, label: o.label, on: sel === o.value }));
  return [
    { title: "Câmeras", rows: [{ kind: "mode", sec: "camera", value: "", label: f.cameraExcl ? "Todas menos as marcadas" : "Somente as marcadas", on: f.cameraExcl }, ...multi("camera", CAMERAS.map((c) => ({ value: c, label: nomeDaCamera(c) })), f.camera)] },
    { title: "Objetos", rows: [{ kind: "mode", sec: "label", value: "", label: f.labelExcl ? "Todos menos os marcados" : "Somente os marcados", on: f.labelExcl }, ...multi("label", OBJETOS.map((o) => ({ value: o, label: traduzirLabel(o) })), f.label)] },
    { title: "Eventos", rows: multi("recortes", RECORTES, f.recortes) },
    { title: "Status", rows: multi("status", STATUS, f.status) },
    { title: "Dia", rows: single("dia", DIAS, f.dia) },
    { title: "Horário", rows: multi("hora", HORAS, f.hora) },
    { title: "", rows: [{ kind: "clear", sec: "", value: "", label: "Limpar filtros", on: false }, { kind: "apply", sec: "", value: "", label: "Aplicar", on: false }] },
  ];
}

// the filter screen is three columns of groups plus a footer with two buttons
const COLUNAS = [["Câmeras", "Eventos"], ["Objetos"], ["Dia", "Horário"]];
function colunasDe(f: Filt): { title: string; rows: Row[] }[][] {
  const g = drawerRows(f);
  return COLUNAS.map((titles) => titles.map((ti) => g.find((x) => x.title === ti)!));
}
const rowsDaColuna = (f: Filt, c: number): Row[] => colunasDe(f)[c]!.flatMap((x) => x.rows);
const RODAPE: Row[] = [{ kind: "clear", sec: "", value: "", label: "Limpar filtros", on: false }, { kind: "apply", sec: "", value: "", label: "Aplicar", on: false }];

// one query per selected hour window (the API takes a single window); no window selected = one query
function paramsList(f: Filt, tab: Tab): Record<string, string | number | boolean | undefined>[] {
  const sel = (v: string[], excl: boolean) => (v.length ? `${excl ? "!" : ""}${v.join(",")}` : "");
  const base: Record<string, string | number | boolean | undefined> = {
    camera: sel(f.camera, f.cameraExcl), label: sel(f.label, f.labelExcl), status: sel(f.status, false),
    groups: (f.recortes.length ? f.recortes : ["com", "sem"]).map((r) => GRUPO[r]).join(","), only_with_image: true,
  };
  if (tab === "alertas") base.outcome = "alert";
  const hoje = new Date(); hoje.setHours(0, 0, 0, 0);
  const iso = (d: Date) => d.toISOString();
  if (f.dia === "hoje") base.since = iso(hoje);
  if (f.dia === "ontem") { const o = new Date(hoje); o.setDate(o.getDate() - 1); base.since = iso(o); base.until = iso(hoje); }
  if (f.dia === "7d") { const o = new Date(hoje); o.setDate(o.getDate() - 6); base.since = iso(o); }
  if (f.dia === "30d") { const o = new Date(hoje); o.setDate(o.getDate() - 29); base.since = iso(o); }
  if (!f.hora.length) return [base];
  return f.hora.map((h) => { const [a, b] = h.split("-"); return { ...base, time_from: a, time_to: b, utc_offset_minutes: -new Date().getTimezoneOffset() }; });
}

function quandoPt(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso.endsWith("Z") || /[+-]\d\d:\d\d$/.test(iso) ? iso : iso + "Z");
  return d.toLocaleString("pt-BR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

type Midia = { k: "snapshot" | "frame" | "thumbnail" | "clip"; rotulo: string };
function midiasDe(e: EventItem): Midia[] {
  const m: Midia[] = [];
  if (e.has_snapshot) m.push({ k: "snapshot", rotulo: "Foto" });
  m.push({ k: "frame", rotulo: "Cena" });
  if (e.has_clip) m.push({ k: "clip", rotulo: "Vídeo" });
  return m;
}

function App() {
  const urlTab = new URLSearchParams(location.search).get("tab") as Tab | null;
  const [tab, setTab] = useState<Tab>(urlTab || "live");
  const [zone, setZone] = useState<Zone>("tabs");
  const [tf, setTf] = useState(TABS.findIndex((x) => x.id === (urlTab || "live")));  // focus in the header: 0..2 tabs, 3 filters, 4 exit
  const [idx, setIdx] = useState(0);
  const [items, setItems] = useState<EventItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState<EventItem | null>(null);
  const [detail, setDetail] = useState<EventDetail | null>(null);
  const [mf, setMf] = useState(0);                 // -1 = the back button, else the media strip
  const [clip, setClip] = useState<"" | "loading" | "playing" | "paused">("");
  const [light, setLight] = useState<string | null>(null);
  const [filt, setFilt] = useState<Filt>(NOFILT);
  const [df, setDf] = useState({ c: 0, r: 0 });    // focus in the filters screen: column, row (column 3 = the footer buttons)
  const page = useRef(1), more = useRef(true), busy = useRef(0);
  const mainRef = useRef<HTMLDivElement>(null);
  const selRef = useRef(0);                         // last media thumb that was on the main image
  const S = useRef({ tab, zone, tf, idx, items, open, mf, light, filt, df, clip });
  S.current = { tab, zone, tf, idx, items, open, mf, light, filt, df, clip };

  const load = useCallback(async (t: Tab, f: Filt, reset: boolean) => {
    if (t === "live") return;
    if (reset) { busy.current += 1; page.current = 1; more.current = true; setItems([]); setIdx(0); }
    if (!more.current) return;
    const mine = busy.current;
    setLoading(true);
    try {
      const rs = await Promise.all(paramsList(f, t).map((p) => api.events({ page: page.current, size: PAGE, ...p })));
      if (mine !== busy.current) return;
      const novos = rs.flatMap((r) => r.items).sort((a, b) => (a.start_time < b.start_time ? 1 : -1));
      more.current = rs.some((r) => r.items.length === PAGE);
      page.current += 1;
      setItems((o) => { const vistos = new Set(reset ? [] : o.map((e) => e.id)); return (reset ? [] : o).concat(novos.filter((e) => !vistos.has(e.id))); });
    } catch { more.current = false; }
    setLoading(false);
  }, []);

  useEffect(() => { TvBridge.setTab(tab); void load(tab, S.current.filt, true); }, [tab, load]);

  useEffect(() => {
    setDetail(null);
    if (open) api.event(open.id).then(setDetail).catch(() => {});
  }, [open]);

  useEffect(() => {
    if (zone !== "grid") return;
    document.querySelector(".tv-celula--foco")?.scrollIntoView({ block: "center" });
    if (idx >= items.length - COLS * 2) void load(S.current.tab, S.current.filt, false);
  }, [idx, zone, items.length, load]);

  useEffect(() => {
    const w = window as unknown as Record<string, unknown>;
    w.tvClipState = (s: string) => setClip(s as "" | "loading" | "playing" | "paused");
    w.tvFocusTabs = (n?: number) => { if (typeof n === "number") setTf(n); setZone("tabs"); };
    w.tvKey = (k: string): boolean => {
      const s = S.current;
      const closeModal = () => { TvBridge.stopClip(); setClip(""); setOpen(null); setZone("grid"); };
      if (s.zone === "lightbox") { if (k === "back" || k === "ok") { setLight(null); setZone("modal"); } return true; }

      if (s.zone === "drawer") {
        const { c, r } = s.df;
        const fechar = () => { setZone("tabs"); void load(s.tab, s.filt, true); };
        if (k === "back") { fechar(); return true; }
        if (c === 4) {                                      // the close button, top right
          if (k === "ok") fechar();
          else if (k === "down" || k === "left") setDf({ c: 2, r: 0 });
          return true;
        }
        if (c === 3) {                                      // footer buttons
          if (k === "left") setDf({ c: 3, r: 0 });
          else if (k === "right") setDf({ c: 3, r: 1 });
          else if (k === "up") setDf({ c: 1, r: 0 });
          else if (k === "ok") { if (r === 0) setFilt(NOFILT); else fechar(); }
          return true;
        }
        const rows = rowsDaColuna(s.filt, c);
        if (k === "up") { if (r > 0) setDf({ c, r: r - 1 }); else setDf({ c: 4, r: 0 }); return true; }
        if (k === "down") { if (r < rows.length - 1) setDf({ c, r: r + 1 }); else setDf({ c: 3, r: 1 }); return true; }
        if (k === "left") { if (c > 0) setDf({ c: c - 1, r: Math.min(r, rowsDaColuna(s.filt, c - 1).length - 1) }); return true; }
        if (k === "right") { if (c < 2) setDf({ c: c + 1, r: Math.min(r, rowsDaColuna(s.filt, c + 1).length - 1) }); return true; }
        if (k === "ok") {
          const row = rows[r]!;
          if (row.kind === "mode") { setFilt({ ...s.filt, ...(row.sec === "camera" ? { cameraExcl: !s.filt.cameraExcl } : { labelExcl: !s.filt.labelExcl }) }); return true; }
          if (row.kind === "radio") { setFilt({ ...s.filt, [row.sec]: row.value } as Filt); return true; }
          const cur = (s.filt as unknown as Record<string, string[]>)[row.sec]!;
          const next = cur.includes(row.value) ? cur.filter((v) => v !== row.value) : [...cur, row.value];
          setFilt({ ...s.filt, [row.sec]: next } as Filt);
        }
        return true;
      }

      if (s.zone === "modal" && s.open) {
        const ms = midiasDe(s.open);
        if (k === "back") { if (s.clip) { TvBridge.stopClip(); setClip(""); } else closeModal(); return true; }
        if (s.mf === -1) {                                  // the close button, top right; up / down scroll the sidebar first
          const info = document.querySelector(".tv-info") as HTMLElement | null;
          if (k === "ok") closeModal();
          else if (k === "down") { if (info && info.scrollTop + info.clientHeight < info.scrollHeight - 2) info.scrollBy(0, 90); else setMf(1001); }
          else if (k === "up") { if (info && info.scrollTop > 0) info.scrollBy(0, -90); }
          else if (k === "left") setMf(0);
          return true;
        }
        if (k === "up") { if (s.clip) { TvBridge.stopClip(); setClip(""); } setMf(-1); return true; }
        if (k === "left" || k === "right") {
          if (s.clip) { TvBridge.stopClip(); setClip(""); }
          const seq = [...ms.map((_, i) => i), 1000, 1001];          // thumbs, then the previous / next event buttons
          const at = seq.indexOf(s.mf);
          setMf(seq[Math.max(0, Math.min(seq.length - 1, at + (k === "left" ? -1 : 1)))]!);
          return true;
        }
        if (s.mf >= 1000) {                                          // previous / next event
          if (k === "ok") {
            const at = s.items.findIndex((e) => e.id === s.open!.id);
            const to = s.mf === 1000 ? at - 1 : at + 1;
            const e = s.items[to];
            if (e) { TvBridge.stopClip(); setClip(""); selRef.current = 0; setOpen(e); setIdx(to); }
            if (to >= s.items.length - 3) void load(s.tab, s.filt, false);
          }
          return true;
        }
        if (k === "ok") {
          const m = ms[s.mf];
          if (!m) return true;
          if (m.k === "clip") {
            if (s.clip === "playing" || s.clip === "paused") TvBridge.toggleClip();
            else if (mainRef.current) {
              const r = mainRef.current.getBoundingClientRect();
              setClip("loading");
              TvBridge.playClip(s.open.id, r.left, r.top, r.width, r.height, window.innerWidth);
            }
          } else setLight(api.mediaUrl(s.open.id, m.k)), setZone("lightbox");
        }
        return true;
      }

      if (s.zone === "tabs") {
        const filtros = s.tab !== "live";
        const last = filtros ? 4 : 4;
        const slots = filtros ? [0, 1, 2, 3, 4] : [0, 1, 2, 4];
        const pos = slots.indexOf(s.tf);
        if (k === "left" && pos > 0) { const n = slots[pos - 1]!; setTf(n); if (n < 3) setTab(TABS[n]!.id); return true; }
        if (k === "right" && pos < slots.length - 1) { const n = slots[pos + 1]!; setTf(n); if (n < 3) setTab(TABS[n]!.id); return true; }
        if (k === "down") {
          const right = s.tf >= 3;                          // Filtros / Sair go down to the top-right cover
          if (s.tab === "live") { TvBridge.focusLive(right ? 1 : 0); setZone("live"); }
          else if (s.items.length) { setIdx(right ? Math.min(COLS - 1, s.items.length - 1) : 0); setZone("grid"); }
          return true;
        }
        if (k === "ok") {
          if (s.tf === 3) { setDf({ c: 0, r: 0 }); setZone("drawer"); }
          else if (s.tf === last) TvBridge.exit();
          return true;
        }
        if (k === "back") { TvBridge.exit(); return true; }
        return true;
      }

      if (s.zone === "live") return true;
      // grid
      const n = s.items.length, i = s.idx;
      if (k === "left") { if (i % COLS > 0) setIdx(i - 1); return true; }
      if (k === "right") { if (i % COLS < COLS - 1 && i + 1 < n) setIdx(i + 1); return true; }
      if (k === "down") { if (i + COLS < n) setIdx(i + COLS); else if (i < n - 1) setIdx(n - 1); return true; }
      if (k === "up") {
        if (i - COLS >= 0) setIdx(i - COLS);
        else { setTf(i === COLS - 1 ? 3 : TABS.findIndex((x) => x.id === s.tab)); setZone("tabs"); window.scrollTo(0, 0); } // top-right cover -> Filtros, the others -> the menu
        return true;
      }
      if (k === "ok") { const e = s.items[i]; if (e) { setOpen(e); setMf(0); setZone("modal"); } return true; }
      if (k === "back") { setTf(TABS.findIndex((x) => x.id === s.tab)); setZone("tabs"); window.scrollTo(0, 0); return true; }
      return true;
    };
  }, [load]);

  const live = tab === "live";
  const cameraPadrao = filt.cameraExcl && filt.camera.length === OCULTAS.length && OCULTAS.every((c) => filt.camera.includes(c));
  const nFilt = (cameraPadrao ? 0 : filt.camera.length) + filt.label.length + filt.status.length + (filt.dia ? 1 : 0) + filt.hora.length + (filt.recortes.length === 1 && filt.recortes[0] === "com" ? 0 : 1);
  const ring = (cond: boolean) => (cond ? " tv-foco" : "");
  const medias = open ? midiasDe(open) : [];
  if (mf >= 0 && mf < 1000) selRef.current = mf;
  const sel = medias[selRef.current] ?? medias[0];
  const mainKind = sel?.k === "clip" ? (open?.has_snapshot ? "snapshot" : "frame") : sel?.k;

  return (
    <div className="app tv">
      <header className="topo">
        <div className="topo__container">
          <nav className="segmentado">
            {TABS.map((t, i) => (
              <span key={t.id} className={"aba" + (t.id === tab ? " aba--ativa" : "") + ring(zone === "tabs" && tf === i)} data-ativo={t.id === tab}>
                <Icone nome={t.icone} className="aba__icone" />
                {t.rotulo}
              </span>
            ))}
          </nav>
          <span className="marca"><img className="marca__logo" src="/cerberus-logo.svg" alt="" /></span>
          <div className="tv-acoes">
            {!live && (
              <span className={"tv-btn" + ring(zone === "tabs" && tf === 3)}>
                <Icone nome="filtro" />Filtros{nFilt > 0 && <b className="tv-btn__n">{nFilt}</b>}
              </span>
            )}
            <span className={"tv-btn" + ring(zone === "tabs" && tf === 4)}><Icone nome="sair" />Sair</span>
          </div>
        </div>
      </header>

      {!live && (
        <main className="conteudo">
          <div className="mosaico">
            {items.map((e, i) => (
              <div key={e.id} className={"tv-celula" + (zone === "grid" && i === idx ? " tv-celula--foco" : "")}>
                <Capa
                  evento={e}
                  titulo={nomeDoEvento(e.label, e.zones, e.sub_label, e.sub_label_score, e.top_score)}
                  quando={e.start_time}
                  para="#"
                  detalhe={nomeDaCamera(e.camera)}
                  emVezDeNavegar={() => {}}
                />
              </div>
            ))}
          </div>
          {!items.length && !loading && <p className="tv-vazio">{tab === "alertas" ? "Nenhum alerta" : "Nenhum evento"}</p>}
          {loading && <p className="tv-vazio">carregando…</p>}
        </main>
      )}

      {open && (
        <div className="tv-modal">
          <div className="tv-modal__corpo">
            <div className="tv-modal__midia">
              <div className="tv-modal__principal" ref={mainRef}>
                {mainKind && <img key={mainKind} src={api.mediaUrl(open.id, mainKind)} alt="" />}
                {sel?.k === "clip" && clip === "" && <span className="tv-play-grande"><Icone nome="monitor" /></span>}
                {sel?.k === "clip" && clip === "loading" && <span className="tv-carregando">carregando vídeo…</span>}
                {sel?.k === "clip" && clip === "paused" && <span className="tv-play-grande tv-play-grande--pausa">❚❚</span>}
              </div>
              <div className="tv-tiras">
                {medias.map((m, i) => (
                  <button key={m.k} className={"tv-tira" + (i === mf ? " tv-tira--ativa" : "") + ring(zone === "modal" && i === mf)}>
                    <img src={api.mediaUrl(open.id, m.k === "clip" ? (open.has_snapshot ? "snapshot" : "frame") : m.k)} alt="" />
                    {m.k === "clip" && <span className="tv-tira__play">▶</span>}
                    <span className="tv-tira__rotulo">{m.rotulo}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="tv-lateral-caixa"><aside className="tv-lateral">
              <div className="tv-lateral__topo">
                <div className="tv-modal__titulo">{nomeDoEvento(open.label, open.zones)}</div>
                <span className={"tv-btn tv-btn--icone" + ring(zone === "modal" && mf === -1)} aria-label="Fechar"><Icone nome="fechar" /></span>
              </div>
            <dl className="tv-info">
              <dt>Objeto</dt><dd>{traduzirLabel(open.label)}{open.top_score != null ? ` · ${Math.round(open.top_score * 100)}%` : ""}</dd>
              <dt>Câmera</dt><dd>{nomeDaCamera(open.camera)}</dd>
              <dt>Quando</dt><dd>{quandoPt(open.start_time)}</dd>
              {open.zones.length > 0 && (<><dt>Zonas</dt><dd>{open.zones.map(nomeDaZona).join(", ")}</dd></>)}
              <dt>Situação</dt><dd>{descreverSituacao(open.status)}</dd>
              {detail?.current_decision && (<><dt>Decisão</dt><dd>{motivoLimpo(detail.current_decision.reason, detail.current_decision.matched_rule_name)}</dd></>)}
              {detail?.enrichments?.map((en) => (<div key={en.id} className="tv-info__linha"><dt>{descreverReconhecimento(en.type)}</dt><dd>{en.value ?? "—"}{en.score != null ? ` · ${Math.round(en.score * 100)}%` : ""}</dd></div>))}
            </dl>
                <span className="tv-navbtns">
                  <span className={"tv-btn tv-btn--icone" + ring(zone === "modal" && mf === 1000)} aria-label="Evento anterior">‹</span>
                  <span className={"tv-btn tv-btn--icone" + ring(zone === "modal" && mf === 1001)} aria-label="Próximo evento">›</span>
                </span>
            </aside></div>
          </div>
        </div>
      )}

      {zone === "drawer" && (
        <div className="tv-filtros">
          <div className="tv-filtros__topo">
            <h3>Filtros{nFilt > 0 && <b className="tv-btn__n">{nFilt}</b>}</h3>
            <span className={"tv-btn tv-btn--icone" + ring(df.c === 4)} aria-label="Fechar"><Icone nome="fechar" /></span>
          </div>
          <div className="tv-filtros__colunas">
            {colunasDe(filt).map((grupos, ci) => {
              let n = -1;
              return (
                <div key={ci} className="tv-filtros__coluna">
                  {grupos.map((g) => (
                    <section key={g.title}>
                      <h4>{g.title}</h4>
                      {g.rows.map((r) => {
                        n += 1;
                        return (
                          <div key={r.sec + r.value + r.kind} className={"tv-linha" + (r.on ? " tv-linha--on" : "") + ring(df.c === ci && df.r === n)}>
                            {(r.kind === "toggle" || r.kind === "radio") && <span className={"tv-marca" + (r.kind === "radio" ? " tv-marca--radio" : "")}>{r.on ? "✓" : ""}</span>}
                            {r.label}
                          </div>
                        );
                      })}
                    </section>
                  ))}
                </div>
              );
            })}
          </div>
          <div className="tv-filtros__rodape">
            {RODAPE.map((r, i) => (
              <span key={r.kind} className={"tv-btn" + (r.kind === "apply" ? " tv-btn--principal" : "") + ring(df.c === 3 && df.r === i)}>{r.label}</span>
            ))}
          </div>
        </div>
      )}

      {light && <div className="tv-lightbox"><img src={light} alt="" /></div>}
    </div>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
