package com.ansub.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

// The 3D house behind the home is a picture drawn on the SERVER (the `housedash-renderer` container: the housedash scene in
// headless Chromium, served by the tv-home function). The TV only downloads the finished JPEG when its version changes, keeps
// the last one on disk, and shows it as a plain image: nothing 3D runs on the TV.
final class HouseView {
    private static final String ORIGIN = "https://appassets.local/";
    static final String ORIGIN_URL = ORIGIN;

    private static Bitmap bitmap;
    private static String version = "";
    private static boolean downloading;
    private static final List<WeakReference<ImageView>> views = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    // Layouts that need the lamp icon positions (focusable overlays) and the sky colour at the bottom of the picture.
    interface Listener { void onHouse(JSONArray icons, int bottomColor); }
    private static final List<WeakReference<Listener>> listeners = new ArrayList<>();
    static JSONArray icons;
    static String period = "night";
    static int bottomColor = 0xFF060B1E;
    static void listen(Listener l) { listeners.add(new WeakReference<>(l)); if (icons != null) l.onHouse(icons, bottomColor); }

    static ImageView create(Activity a) {
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        iv.setBackgroundColor(bottomColor);
        iv.setFocusable(false);
        if (bitmap == null) loadDisk(a.getApplicationContext());
        if (bitmap != null) iv.setImageBitmap(bitmap);
        views.add(new WeakReference<>(iv));
        return iv;
    }

    private static void loadDisk(Context c) {
        File f = new File(c.getFilesDir(), "house.jpg"), m = new File(c.getFilesDir(), "house.meta");
        if (!f.exists() || !m.exists()) return;
        try (InputStream in = new java.io.FileInputStream(m)) {
            JSONObject meta = new JSONObject(new String(readAll(in), "UTF-8"));
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return;
            bitmap = b;
            version = meta.optString("version", "");
            period = meta.optString("period", period);
            icons = meta.optJSONArray("icons");
            bottomColor = b.getPixel(b.getWidth() / 2, b.getHeight() - 2);
        } catch (Exception ignored) {}
    }

    // The server's picture version for the TV to be on (from the sync `house` section). Downloads only when it differs.
    static void onHouse(final Context c, final JSONObject meta, final String imageUrl) {
        final String v = meta.optString("version", "");
        if (v.isEmpty() || v.equals(version) || downloading) return;
        downloading = true;
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(imageUrl).openConnection();
                h.setConnectTimeout(5000);
                h.setReadTimeout(20000);
                byte[] jpg;
                try (InputStream in = h.getInputStream()) { jpg = readAll(in); }
                final Bitmap bm = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
                if (bm == null) return;
                try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), "house.jpg"))) { o.write(jpg); }
                try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), "house.meta"))) { o.write(meta.toString().getBytes("UTF-8")); }
                final JSONArray ic = meta.optJSONArray("icons");
                main.post(() -> {
                    bitmap = bm;
                    version = v;
                    period = meta.optString("period", period);
                    icons = ic;
                    bottomColor = bm.getPixel(bm.getWidth() / 2, bm.getHeight() - 2);
                    for (WeakReference<ImageView> r : views) { ImageView iv = r.get(); if (iv != null) iv.setImageBitmap(bm); }
                    for (WeakReference<Listener> r : listeners) { Listener l = r.get(); if (l != null) l.onHouse(icons, bottomColor); }
                });
            } catch (Exception ignored) {
            } finally { downloading = false; }
        }, "house-download").start();
    }

    static String version() { return version; }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        return o.toByteArray();
    }

    // Serves the APK's assets to the in-app pages (the cards) through the fake https origin; everything else is blocked.
    static WebResourceResponse serve(Context c, WebResourceRequest r) {
        String url = r.getUrl().toString();
        if (!url.startsWith(ORIGIN)) return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null, null);
        String path = r.getUrl().getPath();
        if (path == null || path.endsWith("/")) path = "/cards/index.html";
        try {
            InputStream in = c.getAssets().open(path.substring(1));
            String mime = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".png") ? "image/png"
                    : path.endsWith(".ttf") ? "font/ttf" : path.endsWith(".webp") ? "image/webp" : path.endsWith(".svg") ? "image/svg+xml" : "text/html";
            return new WebResourceResponse(mime, "utf-8", in);
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null);
        }
    }

    static void release(Activity a) { /* nothing is hosted in the activity any more */ }
}
