package com.ansub.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.MutableContextWrapper;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// The home's data layer and the column of three cards (weather, solar, Taurus).
//
// ONE connection feeds everything: a long-poll to the read-only `tv-home` endpoint (`?sync=1`). The server answers the
// moment the house changes (a lamp, a TV, the alarm, a gate, the solar plant, a feeding...) and sends only the sections
// that changed; when nothing changes the request just waits (25 s) and costs ~40 bytes. The `scene` goes to the 3D house,
// the `tables` to the cards. The poll runs only while a home screen is visible.
//
// The cards are the REAL housedash components (assets/cards, a fake Supabase client over the tables). Their WebView is
// created once and moved between the home activities, so opening the home from an app shows them at once.
final class HouseCards {
    static final int W = 400, H = 580; // the slot above the dock (190 .. 770)

    static Activity sceneActivity;                // the activity whose window hosts the house renderer
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static String cfgUrl;

    // ── the single poller ────────────────────────────────────────────────────────────────────────────────────────
    private static volatile String sceneHash = "", tablesHash = "";
    private static volatile String lastSceneJson, lastTables;
    private static volatile int visible;          // home screens currently on screen
    private static volatile boolean running;

    // Called from the home activities' onStart / onStop.
    static synchronized void visible(Activity a, int delta) {
        visible = Math.max(0, visible + delta);
        if (delta > 0) sceneActivity = a;
        if (visible > 0 && !running) startLoop(a.getApplicationContext());
    }

    private static void startLoop(final android.content.Context c) {
        running = true;
        new Thread(() -> {
            try {
                while (visible > 0) {
                    try {
                        String body = get(endpoint(c) + "&sync=1&s=" + sceneHash + "&t=" + tablesHash + "&h=" + HouseView.version() + "&wait=25", 40_000);
                        final JSONObject j = new JSONObject(body);
                        sceneHash = j.optString("s", sceneHash);
                        tablesHash = j.optString("t", tablesHash);
                        final String scene = j.has("scene") ? j.getJSONObject("scene").toString() : null;
                        final String tables = j.has("tables") ? j.getJSONObject("tables").toString() : null;
                        if (j.has("house")) HouseView.onHouse(c, j.getJSONObject("house"), endpoint(c) + "&img=1");
                        ui.post(() -> {
                            if (tables != null) { lastTables = tables; pushTables(); }
                            if (scene != null) { lastSceneJson = scene; applyPending(); }
                        });
                    } catch (Exception e) {
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    }
                }
            } finally { running = false; }
        }, "tv-home-sync").start();
    }

    static String endpoint(android.content.Context c) throws Exception {
        if (cfgUrl == null) {
            try (InputStream in = c.getAssets().open("tvhome.cfg")) { cfgUrl = new String(readAll(in), "UTF-8").trim(); }
        }
        return cfgUrl;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        return o.toByteArray();
    }

    static String get(String url, int readTimeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(readTimeoutMs);
        try (InputStream in = c.getInputStream()) { return new String(readAll(in), "UTF-8"); }
    }

    // ── lamp control ──────────────────────────────────────────────────────────────────────────────────────────────
    private static final java.util.Map<String, Object[]> pendingLamps = new java.util.HashMap<>(); // name -> {on, since}

    // view -> the `ewelink_commands` queue, through tv-home. The house shows the new state at once and holds it for 8 s,
    // like the dash, until the real state arrives.
    static void toggleLamp(final Activity a, final String name, final boolean on) {
        pendingLamps.put(name, new Object[] {on, System.currentTimeMillis()});
        applyPending();
        ui.postDelayed(HouseCards::applyPending, 8100); // when the hold ends, show whatever is true
        postLamp(a, name, on);
        // the bridge listens to Realtime INSERTs and now and then misses one: if the lamp has not followed after 9 s, ask once more
        ui.postDelayed(() -> {
            try {
                if (lastSceneJson != null && new JSONObject(lastSceneJson).getJSONObject("devices").optBoolean(name.toLowerCase(java.util.Locale.ROOT)) != on) postLamp(a, name, on);
            } catch (Exception ignored) {}
        }, 9000);
    }

    private static void postLamp(final Activity a, final String name, final boolean on) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(endpoint(a) + "&cmd=lamp&name=" + name + "&on=" + (on ? "1" : "0")).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                c.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }

    // Lamp state for the icons drawn on the home: the server's scene plus what was just toggled (held 8 s). The 3D picture
    // follows a few seconds later, when the server has drawn it.
    private static final java.util.Map<String, Boolean> lampState = new java.util.HashMap<>();
    private static final java.util.List<java.lang.ref.WeakReference<Runnable>> lampListeners = new java.util.ArrayList<>();
    static boolean lampOn(String name) { return Boolean.TRUE.equals(lampState.get(name)); }
    static void listenLamps(Runnable r) { lampListeners.add(new java.lang.ref.WeakReference<>(r)); r.run(); }

    private static void applyPending() {
        if (lastSceneJson == null) return;
        try {
            JSONObject dev = new JSONObject(lastSceneJson).getJSONObject("devices");
            long now = System.currentTimeMillis();
            for (java.util.Iterator<java.util.Map.Entry<String, Object[]>> it = pendingLamps.entrySet().iterator(); it.hasNext(); ) {
                java.util.Map.Entry<String, Object[]> e = it.next();
                if (now - (Long) e.getValue()[1] > 8000) { it.remove(); continue; }
                dev.put(e.getKey().toLowerCase(java.util.Locale.ROOT), (Boolean) e.getValue()[0]);
            }
            for (String n : new String[] {"Jardim", "Quarto", "Corredor", "Fundo"}) lampState.put(n, dev.optBoolean(n.toLowerCase(java.util.Locale.ROOT)));
            for (java.lang.ref.WeakReference<Runnable> r : lampListeners) { Runnable x = r.get(); if (x != null) x.run(); }
        } catch (Exception ignored) {}
    }

    // ── the cards page (one WebView, moved between the home activities) ─────────────────────────────────────────────
    private static WebView cardsWeb;
    private static MutableContextWrapper cardsCtx;
    private static String simulation = "off";
    private static boolean pageLoaded;
    private static final Runnable resume = () -> { if (cardsWeb != null) cardsWeb.onResume(); };

    // debug: adb shell am start -n com.ansub.tvhome/.HomeEntry --es sim solar|taurus|off
    static void setSim(String s) {
        simulation = s == null ? "off" : s;
        if (cardsWeb != null) cardsWeb.evaluateJavascript("window.setSim && setSim('" + simulation.replaceAll("[^a-z,]", "") + "')", null);
    }

    // The cards animate for real (clouds, stars, rain), which is heavy on this TV's GPU. While the remote is being used the
    // page freezes on its current frame (a paused WebView keeps showing it) and wakes again a moment after the last key.
    // the cards slide in one after the other (right to left, top to bottom) whenever the home opens
    static void enter() {
        if (cardsWeb != null) cardsWeb.evaluateJavascript("window.enter && enter()", null);
    }

    static void calm() {
        // no longer pauses the page: the animations keep running while the focus moves
    }

    private static void pushTables() {
        if (cardsWeb == null || !pageLoaded || lastTables == null) return;
        cardsWeb.evaluateJavascript("window.setTables && setTables(" + lastTables + ")", null);
        if (!simulation.equals("off")) cardsWeb.evaluateJavascript("window.setSim && setSim('" + simulation.replaceAll("[^a-z,]", "") + "')", null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    static FrameLayout build(final Activity a) {
        sceneActivity = a;
        FrameLayout box = new FrameLayout(a);
        box.setClipChildren(false);
        box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); // the page's inner content view is focusable: keep the remote out of it
        if (cardsWeb == null) {
            cardsCtx = new MutableContextWrapper(a);
            final WebView w = new WebView(cardsCtx);
            cardsWeb = w;
            w.setBackgroundColor(Color.TRANSPARENT);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            w.setVerticalScrollBarEnabled(false);
            w.setHorizontalScrollBarEnabled(false);
            w.setOverScrollMode(View.OVER_SCROLL_NEVER);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setAllowFileAccess(false);
            // no automatic darkening: it painted a dark panel behind the transparent page
            if (android.os.Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(false);
            else if (android.os.Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_OFF);
            w.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String url) { pageLoaded = true; pushTables(); }
                @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return HouseView.serve(a.getApplicationContext(), r); }
            });
            w.loadUrl(HouseView.ORIGIN_URL + "cards/index.html");
        } else {
            cardsCtx.setBaseContext(a);
            if (cardsWeb.getParent() instanceof ViewGroup) ((ViewGroup) cardsWeb.getParent()).removeView(cardsWeb);
        }
        box.addView(cardsWeb, new FrameLayout.LayoutParams(-1, -1));
        cardsWeb.onResume();
        if (pageLoaded) pushTables();
        return box;
    }

    // The activity is going away: free the WebView from its window so it can be reused (and the activity can be collected).
    static void release(Activity a) {
        if (cardsWeb != null && cardsCtx != null && cardsCtx.getBaseContext() == a) {
            if (cardsWeb.getParent() instanceof ViewGroup) ((ViewGroup) cardsWeb.getParent()).removeView(cardsWeb);
            cardsCtx.setBaseContext(a.getApplicationContext());
        }
        if (sceneActivity == a) sceneActivity = null;
    }
}
