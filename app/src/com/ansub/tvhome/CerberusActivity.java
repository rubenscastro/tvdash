package com.ansub.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.InputStream;

// Cerberus on the TV (the old Cameras app): the Cerberus panel's own header, covers and colours in a WebView (Live / Eventos /
// Alertas), with Live as a native layer under the header, and the event clip in the native player.
public class CerberusActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private android.widget.VideoView clipView;
    private LiveGrid live;
    private boolean liveFocus, pageReady;
    private String pendingTab = "eventos";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF21222C));
        getWindow().getDecorView().setBackgroundColor(0xFF21222C);
        CerberusApi.warmUp(this);
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(0xFF21222C); // opaque: pixels the page does not cover (a stale layout size after re-entering) are the page colour, not the WebView default
        web.setFocusable(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Bridge(), "TvBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return serve(r); }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        live = new LiveGrid(this, i -> { liveFocus = false; live.blur(); web.evaluateJavascript("window.tvFocusTabs && tvFocusTabs(" + (i == 1 ? 4 : 0) + ")", null); });
        live.setVisibility(View.GONE);
        float sc = getResources().getDisplayMetrics().widthPixels / 1920f;
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-1, getResources().getDisplayMetrics().heightPixels - Math.round(200 * sc) - Math.round(24 * sc));
        ll.topMargin = Math.round(200 * sc);
        root.addView(live, ll);
        this.root = root;
        // After the app is re-entered from the home screen the WebView paints a light 2 px frame at the left, right and bottom edges
        // (below the header). It is drawn by the WebView itself, so thin strips in the page colour sit on top of it.
        final int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels, m = 4, top = Math.round(135 * sc);
        for (int i = 0; i < 3; i++) {
            View v = new View(this);
            v.setBackgroundColor(0xFF21222C);
            v.setFocusable(false);
            FrameLayout.LayoutParams lp = i == 0 ? new FrameLayout.LayoutParams(m, sh - top) : i == 1 ? new FrameLayout.LayoutParams(m, sh - top) : new FrameLayout.LayoutParams(sw, m);
            if (i == 0) { lp.leftMargin = 0; lp.topMargin = top; }
            if (i == 1) { lp.leftMargin = sw - m; lp.topMargin = top; }
            if (i == 2) { lp.leftMargin = 0; lp.topMargin = sh - m; }
            root.addView(v, lp);
        }
        setContentView(root);
        String tab = getIntent().getStringExtra("tab");
        web.loadUrl(HouseView.ORIGIN_URL + "cerberus/index.html?tab=" + (tab == null ? "live" : tab));
    }

    // the page's own files from the APK; /api/... goes to Cerberus with the key
    private WebResourceResponse serve(WebResourceRequest r) {
        if (!r.getUrl().toString().startsWith(HouseView.ORIGIN_URL)) return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null, null);
        String path = r.getUrl().getPath();
        if (path == null || path.equals("/")) path = "/cerberus/index.html";
        if (path.startsWith("/api/")) {
            String q = r.getUrl().getEncodedQuery();
            return CerberusApi.proxy(this, path + (q == null ? "" : "?" + q), r.getRequestHeaders());
        }
        if (!path.startsWith("/cerberus/")) path = "/cerberus" + path;
        try {
            InputStream in = getAssets().open(path.substring(1));
            String mime = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".svg") ? "image/svg+xml"
                    : path.endsWith(".woff2") ? "font/woff2" : path.endsWith(".png") ? "image/png" : "text/html";
            return new WebResourceResponse(mime, "utf-8", in);
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null);
        }
    }

    private final class Bridge {
        @JavascriptInterface public void setTab(final String tab) {
            runOnUiThread(() -> {
                boolean isLive = tab.equals("live");
                live.setVisibility(isLive ? View.VISIBLE : View.GONE);
                if (isLive) live.start(); else { live.stop(); liveFocus = false; }
            });
        }
        @JavascriptInterface public void focusLive(final int i) { runOnUiThread(() -> { liveFocus = true; live.focusTile(i); }); }
        // plays the event clip in a native video layer over the modal's main image (x, y, w, h in the page's CSS px)
        @JavascriptInterface public void playClip(final int id, final float x, final float y, final float w, final float h, final float innerWidth) {
            runOnUiThread(() -> startClip(id, x, y, w, h, innerWidth));
        }
        @JavascriptInterface public void toggleClip() {
            runOnUiThread(() -> {
                if (clipView == null) return;
                if (clipView.isPlaying()) { clipView.pause(); clipState("paused"); } else { clipView.start(); clipState("playing"); }
            });
        }
        @JavascriptInterface public void stopClip() { runOnUiThread(CerberusActivity.this::removeClip); }
        @JavascriptInterface public void exit() { runOnUiThread(CerberusActivity.this::finish); }
    }

    private void clipState(String st) { web.evaluateJavascript("window.tvClipState && tvClipState('" + st + "')", null); }

    private void removeClip() {
        if (clipView == null) return;
        try { clipView.stopPlayback(); } catch (Exception ignored) {}
        root.removeView(clipView);
        clipView = null;
    }

    private void startClip(final long id, float x, float y, float w, float h, float innerWidth) {
        removeClip();
        final float k = web.getWidth() / Math.max(1f, innerWidth);
        clipView = new android.widget.VideoView(this);
        clipView.setAlpha(0f); // shown when the first frame is ready; the page says "carregando" meanwhile
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(w * k), Math.round(h * k));
        lp.leftMargin = Math.round(x * k); lp.topMargin = Math.round(y * k);
        root.addView(clipView, lp);
        final android.widget.VideoView v = clipView;
        new Thread(() -> {
            final android.net.Uri uri = android.net.Uri.parse(CerberusApi.mediaUrl(this, id, "clip"));
            final java.util.Map<String, String> hd = CerberusApi.headers(this);
            runOnUiThread(() -> {
                if (clipView != v) return;
                v.setVideoURI(uri, hd);
                v.setOnPreparedListener(mp -> { if (clipView != v) return; v.setAlpha(1f); v.start(); clipState("playing"); });
                v.setOnErrorListener((mp, what, extra) -> { if (clipView == v) { removeClip(); clipState(""); } return true; });
                v.setOnCompletionListener(mp -> { if (clipView == v) { removeClip(); clipState(""); } });
            });
        }, "clip-url").start();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent ev) {
        if (RemoteKeys.handle(this, ev)) return true;
        if (liveFocus) return super.dispatchKeyEvent(ev); // the live grid's own focus
        String k = null;
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: k = "left"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: k = "right"; break;
            case KeyEvent.KEYCODE_DPAD_UP: k = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: k = "down"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: k = "ok"; break;
            case KeyEvent.KEYCODE_BACK: k = "back"; break;
        }
        if (k == null) return super.dispatchKeyEvent(ev);
        if (ev.getAction() == KeyEvent.ACTION_DOWN) web.evaluateJavascript("window.tvKey && tvKey('" + k + "')", null);
        return true;
    }

    @Override protected void onStop() { super.onStop(); live.stop(); removeClip(); }
    @Override protected void onStart() { super.onStart(); if (live.getVisibility() == View.VISIBLE) live.start(); }
    @Override protected void onDestroy() { super.onDestroy(); web.destroy(); }
}
