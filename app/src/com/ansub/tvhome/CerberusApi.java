package com.ansub.tvhome;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

// Cerberus on the LAN. The read-only key (events, thumbnails, clips) comes from the home server (tv-home ?ck=1) once and stays in memory;
// the Cerberus page's own /api/... requests are answered here with the key added, so the page never sees it.
final class CerberusApi {
    static final String BASE = "http://10.0.20.10:8000";
    private static volatile String key;

    private CerberusApi() {}

    static synchronized String key(Context c) {
        if (key != null) return key;
        try {
            String json = HouseCards.get(HouseCards.endpoint(c) + "&ck=1", 8000);
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (!o.isNull("key")) key = o.getString("key");
        } catch (Exception ignored) {}
        return key;
    }

    static void warmUp(final Context c) { new Thread(() -> key(c), "cerberus-key").start(); }

    // the URL the native player uses: the key travels as ?token= (the player can't set headers on every request)
    static String mediaUrl(Context c, long eventId, String kind) {
        return BASE + "/api/events/" + eventId + "/media/" + kind + "?token=" + key(c);
    }

    static Map<String, String> headers(Context c) {
        Map<String, String> h = new HashMap<>();
        h.put("x-api-key", String.valueOf(key(c)));
        return h;
    }

    // proxies one GET of the page to Cerberus
    static WebResourceResponse proxy(Context c, String pathAndQuery, Map<String, String> reqHeaders) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(BASE + pathAndQuery).openConnection();
            h.setConnectTimeout(5000);
            h.setReadTimeout(20000);
            h.setRequestProperty("x-api-key", String.valueOf(key(c)));
            String range = reqHeaders == null ? null : reqHeaders.get("Range");
            if (range != null) h.setRequestProperty("Range", range);
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            String type = h.getContentType() == null ? "application/octet-stream" : h.getContentType().split(";")[0].trim();
            Map<String, String> out = new HashMap<>();
            out.put("Access-Control-Allow-Origin", "*");
            out.put("Cache-Control", pathAndQuery.contains("/media/") ? "max-age=900" : "no-store");
            return new WebResourceResponse(type, "utf-8", code, code == 200 ? "OK" : "Error", out, in);
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 502, "Bad gateway", new HashMap<>(), null);
        }
    }
}
