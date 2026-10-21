// A tiny stand-in for the Supabase client: serves the table snapshot the TV got from tv-home (window.__tables), so the
// REAL housedash hooks (useWeatherSnapshot, useSolar, useTaurusToday, ...) run unchanged on the TV.
type Row = Record<string, unknown>;
const tables = (): Record<string, Row[]> => (window as unknown as { __tables?: Record<string, Row[]> }).__tables ?? {};

function builder(name: string) {
  let rows: Row[] = [...(tables()[name] ?? [])];
  const cmp = (a: unknown, b: unknown) => (a! < b! ? -1 : a! > b! ? 1 : 0);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const api: any = {
    select() { return api; },
    eq(c: string, v: unknown) { rows = rows.filter((r) => r[c] === v); return api; },
    neq(c: string, v: unknown) { rows = rows.filter((r) => r[c] !== v); return api; },
    gte(c: string, v: unknown) { rows = rows.filter((r) => cmp(r[c], v) >= 0); return api; },
    gt(c: string, v: unknown) { rows = rows.filter((r) => cmp(r[c], v) > 0); return api; },
    lte(c: string, v: unknown) { rows = rows.filter((r) => cmp(r[c], v) <= 0); return api; },
    lt(c: string, v: unknown) { rows = rows.filter((r) => cmp(r[c], v) < 0); return api; },
    in(c: string, vs: unknown[]) { rows = rows.filter((r) => vs.includes(r[c])); return api; },
    is(c: string, v: unknown) { rows = rows.filter((r) => r[c] === v); return api; },
    order(c: string, o?: { ascending?: boolean; nullsFirst?: boolean }) {
      const dir = o?.ascending === false ? -1 : 1;
      rows = [...rows].sort((a, b) => dir * cmp(a[c], b[c]));
      return api;
    },
    limit(n: number) { rows = rows.slice(0, n); return api; },
    range(a: number, b: number) { rows = rows.slice(a, b + 1); return api; },
    maybeSingle() { return Promise.resolve({ data: rows[0] ?? null, error: null }); },
    single() { return Promise.resolve({ data: rows[0] ?? null, error: null }); },
    insert() { return Promise.resolve({ data: null, error: null }); },
    update() { return api; },
    upsert() { return Promise.resolve({ data: null, error: null }); },
    delete() { return api; },
    then(res: (v: { data: Row[]; error: null }) => unknown, rej?: (e: unknown) => unknown) {
      return Promise.resolve({ data: rows, error: null }).then(res, rej);
    },
  };
  return api;
}

const channel = () => {
  const ch = { on() { return ch; }, subscribe() { return ch; }, unsubscribe() { return Promise.resolve('ok'); } };
  return ch;
};

export const supabase = {
  from: builder,
  channel,
  removeChannel() { return Promise.resolve('ok'); },
  rpc() { return Promise.resolve({ data: null, error: null }); },
  functions: { invoke() { return Promise.resolve({ data: null, error: null }); } },
  auth: {
    getSession() { return Promise.resolve({ data: { session: null }, error: null }); },
    getUser() { return Promise.resolve({ data: { user: null }, error: null }); },
    onAuthStateChange() { return { data: { subscription: { unsubscribe() { /* noop */ } } } }; },
  },
};
