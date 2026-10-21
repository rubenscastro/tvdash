package com.ansub.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// What the home screen and the dock can open, and how. Keys: tvaberta, laststream, lasthdmi, cameras,
// source, store, app:<package>, ch:<channel id>, hdmi:<input id>.
final class Items {
    static final String PREFS = "tvhome";
    // True from the moment we open an app or the TV until the home screen or dock is shown again: the Home
    // button opens the dock while it is true, the full home screen otherwise.
    static volatile boolean appInFront;
    // Fixed dock: TV aberta, Globo, Netflix, PS5, Câmeras, Source.
    static final String[] DOCK = {"tvaberta", "ch:246", "app:com.netflix.ninja", "ps5", "cameras", "source"};
    static final String DEFAULT_STREAM = "com.netflix.ninja";

    // The streaming apps of the home grid, in order (the apps.txt list is only used by the old home).
    static final String[] APPS = {
        "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus", "com.wbd.stream",
        "com.apple.atve.androidtv.appletv", "com.spotify.tv.android", "com.globo.globotv",
        "com.formulaone.production",
    };

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    static String appLabel(Context c, String pkg) {
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // The app's TV banner (its home screen tile picture), cropped to w x h, or null if it has none.
    static android.graphics.drawable.Drawable banner(Context c, String pkg, int w, int h) {
        try {
            PackageManager pm = c.getPackageManager();
            android.graphics.drawable.Drawable b = pm.getActivityBanner(launchIntent(pm, pkg));
            if (b == null) b = pm.getApplicationBanner(pkg);
            return b == null ? null : Ui.cover(c, b, w, h);
        } catch (Exception e) { return null; }
    }

    static boolean isPlayStation(String label) {
        String l = label.toLowerCase();
        return l.contains("playstation") || l.contains("ps5");
    }

    static String lastStream(Context c) {
        String p = prefs(c).getString("last_stream", DEFAULT_STREAM);
        return launchIntent(c.getPackageManager(), p) != null ? p : DEFAULT_STREAM;
    }

    static String lastHdmi(Context c) {
        String id = prefs(c).getString("last_hdmi", null);
        if (id != null) return id;
        TvInputInfo main = mainHdmi(c);
        return main == null ? null : main.getId();
    }

    static String title(Context c, String key) {
        switch (key) {
            case "tvaberta": return "TV aberta";
            case "lastchannel": return "Último canal";
            case "laststream": return "Último app de streaming";
            case "lasthdmi": return "Último input HDMI";
            case "cameras": return "Cerberus";
            case "source": return "Source";
            default: return key;
        }
    }

    // The second line on a dock tile: which app / input "último" points at right now.
    static String subtitle(Context c, String key) {
        if (key.equals("laststream")) return appLabel(c, lastStream(c));
        if (key.equals("lasthdmi")) {
            String id = lastHdmi(c);
            for (TvInputInfo in : hdmiInputs(c)) if (in.getId().equals(id)) return inputLabel(c, in);
            return "";
        }
        return "";
    }

    // Dock tiles with their own look: {gradient start, gradient end} and the icon (white) drawn on top.
    static String[] gradient(String key) {
        switch (key) {
            case "tvaberta": return new String[] {"#5270FD", "#2C48D5"};
            case "cameras": return new String[] {"#CD0D20", "#7F0022"};
            case "source": return new String[] {"#0089FC", "#0B4ABD"};
            default: return null;
        }
    }

    static android.graphics.drawable.Drawable styledIcon(String key) {
        switch (key) {
            case "tvaberta": return Icons.drawable(Icons.TV_ABERTA);
            case "cameras": return Icons.drawable(Icons.CAMERAS);
            case "source": return Icons.drawable(Icons.SOURCE);
            default: return null;
        }
    }

    static int[] icon(String key) {
        switch (key) {
            case "tvaberta": case "lastchannel": return LineIcon.ANTENNA;
            case "laststream": return LineIcon.PLAY;
            case "lasthdmi": return LineIcon.HDMI;
            case "cameras": return LineIcon.CAMERA;
            default: return LineIcon.SOURCE;
        }
    }

    // The HDMI input the user is looking at right now through this app (null: something else). The Source menu shows its check
    // only while that input is on screen behind the dock; on the home screen no source has the check.
    static volatile String viewingHdmi;

    static void launch(Activity a, String key) {
        if (key.startsWith("hdmi:")) viewingHdmi = key.substring(5);
        else if (!key.equals("source") && !key.equals("ps5") && !key.equals("lasthdmi")) viewingHdmi = null;
        switch (key) {
            case "tvaberta": start(a, new Intent(a, TvActivity.class)); return;
            case "cameras": start(a, new Intent(a, CerberusActivity.class)); return;
            case "ps5": { TvInputInfo in = ps5Input(a); if (in != null) launch(a, "hdmi:" + in.getId()); return; }
            case "lastchannel": start(a, new Intent(a, TvActivity.class).putExtra("channel_id", Long.parseLong(Channels.lastWatched(a)[2]))); return;
            case "source": SourcePicker.show(a, null); return;
            case "laststream": launch(a, "app:" + lastStream(a)); return;
            case "lasthdmi": { String id = lastHdmi(a); if (id != null) launch(a, "hdmi:" + id); return; }
            case "store": launch(a, "app:com.android.vending"); return;
        }
        if (key.startsWith("app:")) {
            String pkg = key.substring(4);
            Intent i = launchIntent(a.getPackageManager(), pkg);
            if (i == null) return;
            if (!pkg.equals("com.android.vending")) prefs(a).edit().putString("last_stream", pkg).apply();
            start(a, i);
        } else if (key.startsWith("ch:")) {
            start(a, new Intent(a, TvActivity.class).putExtra("channel_id", Long.parseLong(key.substring(3))));
        } else if (key.startsWith("hdmi:")) {
            String id = key.substring(5);
            prefs(a).edit().putString("last_hdmi", id).apply();
            openInput(a, id);
        }
    }

    static void start(Context c, Intent i) {
        appInFront = true;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) {}
    }

    // The TV maker's own antenna app, on the tuner's channels (also reachable from the Source list).
    static void openStockTv(Context c) {
        String id = TvActivity.tunerInputId(c);
        if (id != null) TvActivity.openStockTv(c, id);
    }

    // Opens an HDMI input in the TV maker's Live TV app, with generic fallbacks.
    static void openInput(Context c, String id) {
        appInFront = true;
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        tries.add(new Intent(view).setClassName("com.tcl.tv", "com.tcl.player.TVActivity"));
        tries.add(new Intent(view).setClassName("com.android.tv", "com.android.tv.MainActivity"));
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { if (t.resolveActivity(c.getPackageManager()) != null) { c.startActivity(t); return; } } catch (Exception ignored) {}
        }
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    static List<TvInputInfo> hdmiInputs(Context c) {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList())
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(c)) out.add(in);
        Collections.sort(out, (x, y) -> x.getId().compareTo(y.getId()));
        return out;
    }

    // The TV has this port renamed "Cameras"; the app shows it as the port it is.
    static String fixPortName(String name) {
        return name != null && name.trim().equalsIgnoreCase("cameras") ? "HDMI 4" : name;
    }

    // The name set in the TV's Inputs settings, else the name of the device plugged in (HDMI-CEC), else "HDMI N".
    static String inputLabel(Context c, TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(c);
        if (custom != null && custom.length() > 0) return fixPortName(custom.toString());
        String device = connectedDeviceName(c, in);
        if (device != null) return device;
        CharSequence label = in.loadLabel(c);
        return label != null && label.length() > 0 ? label.toString() : "HDMI";
    }

    // "HDMI 1": the name set in the TV's settings if any, else the port name (not the device's name).
    static String portLabel(Context c, TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(c);
        if (custom != null && custom.length() > 0) return fixPortName(custom.toString());
        CharSequence l = in.loadLabel(c);
        return l != null && l.length() > 0 ? l.toString() : "HDMI";
    }

    static String connectedDeviceName(Context c, TvInputInfo port) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return null;
        for (TvInputInfo child : tim.getTvInputList()) {
            if (!port.getId().equals(child.getParentId())) continue;
            CharSequence n = child.loadCustomLabel(c);
            if (n == null || n.length() == 0) n = child.loadLabel(c);
            if (n != null && n.toString().trim().length() > 0) return n.toString().trim();
        }
        return null;
    }

    // The HDMI input with the PlayStation on it; else the first one that has any device; else the first.
    static TvInputInfo ps5Input(Context c) {
        for (TvInputInfo i : hdmiInputs(c)) {
            String d = connectedDeviceName(c, i);
            if ((d != null && isPlayStation(d)) || isPlayStation(portLabel(c, i))) return i;
        }
        return mainHdmi(c);
    }

    // The HDMI input that has a device on it (the console), or the first one.
    static TvInputInfo mainHdmi(Context c) {
        List<TvInputInfo> in = hdmiInputs(c);
        for (TvInputInfo i : in) if (connectedDeviceName(c, i) != null) return i;
        return in.isEmpty() ? null : in.get(0);
    }
}
