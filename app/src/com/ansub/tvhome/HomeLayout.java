package com.ansub.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// The whole home screen in three layers, so the dock can be the same screen pushed down:
//   bg      the page background
//   header  clock and the three round buttons
//   content the dock row and the grid of apps / channels (at their final, home-screen positions)
// The dock state is just `content` translated down by DOCK_SHIFT, with bg and header invisible.
final class HomeLayout {
    // How far the content sits below its home position when it is the dock (design px at 1920).
    // Home: dock row at y=155. Dock: the Figma places it at y=769, with the first grid row peeking below.
    static final int GRID_TOP_ = 1108;                  // dock 810 + 230 + 68
    static final int DOCK_TOP = 810; // 177 + 585 (3 cards of 163, 48 apart) + 48
    // Dock state (Figma 12:814, over an app): the dock panel at y=788 and the first grid row 32 below it (y=1050).
    // `content` moves by GRID_SHIFT (the grid), and the dock panel by a further DOCK_EXTRA, since the gap between
    // them is 32 there but 68 on the home screen.
    static final int DOCK_SHIFT = 1050 - GRID_TOP_;      // = GRID_SHIFT
    static final int DOCK_EXTRA = (788 - DOCK_TOP) - DOCK_SHIFT;

    final Activity a;
    final FrameLayout root, header, content;
    final android.widget.ScrollView scroller;
    final View bg, shade;
    FrameLayout cards; // the three house cards at the right; faded out in the dock state
    View veil; // contrast gradient between the 3D house and the cards; hidden at dawn and in the dock state
    android.widget.ImageView house; // the 3D house picture behind everything; faded out in the dock state
    FrameLayout dockView;
    private ImageView netIcon;
    private String dockSig = "";
    final List<View> grid = new ArrayList<>();

    private HomeLayout(Activity a) {
        this.a = a;
        root = new FrameLayout(a);
        bg = new View(a);
        bg.setBackground(Look.background(a));
        header = new FrameLayout(a);
        header.setClipChildren(false); // the selected header button grows 10% past its box
        content = new FrameLayout(a);
        // The house picture scrolls with the page (like the cards): it is the first child of the scrolling content.
        house = HouseView.create(a);
        content.addView(house, new FrameLayout.LayoutParams(a.getResources().getDisplayMetrics().widthPixels, a.getResources().getDisplayMetrics().heightPixels));
        // the contrast veil behind the cards (housedash: 648 px wide at the right, 108% tall), 920 px at TV scale
        veil = new VeilView(a);
        int sw = a.getResources().getDisplayMetrics().widthPixels, sh = a.getResources().getDisplayMetrics().heightPixels;
        content.addView(veil, Ui.at(sw - px(1040), -Math.round(sh * 0.16f), px(1040), Math.round(sh * 1.08f))); // up toward the top right, a bit wider
        content.setClipChildren(false);
        root.setClipChildren(false);
        // The page scrolls: header, dock and grid live in one tall `content`, inside a scroll view. The
        // scroll view follows the D-pad focus by itself, so selecting a tile in the last row scrolls it into view.
        // Own the scroll position: the stock scroll view would also nudge itself to the focused tile and undo ours.
        scroller = new android.widget.ScrollView(a) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
            @Override public boolean arrowScroll(int direction) { return false; } // only the focus moves the page
        };
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        scroller.setFocusable(false);
        int rows = Math.max(ROWS, (gridKeys().size() + COLS - 1) / COLS);
        int contentH = Ui.px(a, GRID_TOP + rows * (TILE_H + V_GAP) + 260);
        content.addView(header, 2, new FrameLayout.LayoutParams(-1, Ui.px(a, 160))); // tall enough for the buttons and their growth
        scroller.addView(content, new FrameLayout.LayoutParams(-1, contentH));
        // the page's bottom edge: the last row's own padding (the scroll view sized the content to its children otherwise)
        content.addView(new View(a), Ui.at(0, contentH - 1, 1, 1));
        cards = HouseCards.build(a);
        content.addView(cards, 2, Ui.at(px(1830 - HouseCards.W), px(190), px(HouseCards.W), px(HouseCards.H)));
        // A dark layer under the dock when it floats over an app: clear at the top to 80% black at the bottom,
        // over the lower 690 px of the screen. Only the dock state shows it.
        shade = new View(a);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0xCC000000}));
        shade.setAlpha(0f);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        root.addView(shade, new FrameLayout.LayoutParams(-1, Ui.px(a, 690), Gravity.BOTTOM));
        root.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
    }

    static HomeLayout build(Activity a) {
        HomeLayout h = new HomeLayout(a);
        h.buildHeader();
        h.buildContent();
        h.buildLamps();
        return h;
    }

    // ── lamp controls: the icons are drawn natively from the live state (LampView), over the house picture
    private final java.util.Map<String, LampView> lamps = new java.util.HashMap<>();
    private HouseView.Listener houseListener;   // held here: HouseView keeps only a weak reference
    private Runnable lampListener;

    private void buildLamps() {
        houseListener = (icons, bottom) -> a.runOnUiThread(() -> placeLamps(icons, bottom));
        HouseView.listen(houseListener);
        lampListener = () -> a.runOnUiThread(() -> { for (LampView v : lamps.values()) v.invalidate(); });
        HouseCards.listenLamps(lampListener);
    }

    // ── remote navigation around the lamps (the map the user drew): each key names its target, everything else keeps the default
    // Row ends wrap like text: RIGHT on the last app of a row goes to the first of the next row, LEFT on the first goes to the
    // last of the row above.
    private void wireGrid() {
        for (int i = 0; i < grid.size(); i++) {
            if (i % COLS == COLS - 1 && i + 1 < grid.size()) link(grid.get(i), View.FOCUS_RIGHT, grid.get(i + 1));
            if (i % COLS == 0 && i > 0) link(grid.get(i), View.FOCUS_LEFT, grid.get(i - 1));
        }
    }

    private void link(View from, int dir, View to) {
        if (from == null || to == null) return;
        if (to.getId() == View.NO_ID) to.setId(View.generateViewId());
        switch (dir) {
            case View.FOCUS_UP: from.setNextFocusUpId(to.getId()); break;
            case View.FOCUS_DOWN: from.setNextFocusDownId(to.getId()); break;
            case View.FOCUS_LEFT: from.setNextFocusLeftId(to.getId()); break;
            default: from.setNextFocusRightId(to.getId());
        }
    }

    private void wireNav() {
        java.util.List<View> menu = new ArrayList<>(); // header children in the order they were added: settings, wifi, source
        for (int i = 0; i < header.getChildCount(); i++) if (header.getChildAt(i).isFocusable()) menu.add(header.getChildAt(i));
        if (menu.size() < 3 || dockView == null || dockView.getChildCount() < 6) return;
        View source = menu.get(2), wifi = menu.get(1), settings = menu.get(0);
        View jardim = lamps.get("Jardim"), quarto = lamps.get("Quarto"), corredor = lamps.get("Corredor"), fundo = lamps.get("Fundo");
        View[] d = new View[6];
        for (int i = 0; i < 6; i++) d[i] = dockView.getChildAt(i);
        // menu buttons
        link(source, View.FOCUS_LEFT, fundo);
        link(source, View.FOCUS_DOWN, d[4]);
        link(wifi, View.FOCUS_DOWN, d[5]);
        link(settings, View.FOCUS_DOWN, d[5]);
        // lamps
        link(fundo, View.FOCUS_RIGHT, source);
        link(fundo, View.FOCUS_LEFT, quarto);
        link(fundo, View.FOCUS_DOWN, corredor);
        link(quarto, View.FOCUS_UP, fundo);
        link(quarto, View.FOCUS_RIGHT, fundo);
        link(quarto, View.FOCUS_LEFT, jardim);
        link(quarto, View.FOCUS_DOWN, corredor);
        link(corredor, View.FOCUS_UP, quarto);
        link(corredor, View.FOCUS_LEFT, jardim);
        link(corredor, View.FOCUS_RIGHT, fundo);
        link(corredor, View.FOCUS_DOWN, d[3]);
        link(jardim, View.FOCUS_RIGHT, corredor);
        link(jardim, View.FOCUS_DOWN, d[0]);
        // dock: only UP changes
        link(d[0], View.FOCUS_UP, jardim);
        link(d[1], View.FOCUS_UP, jardim);
        link(d[2], View.FOCUS_UP, corredor);
        link(d[3], View.FOCUS_UP, corredor);
        link(d[4], View.FOCUS_UP, wifi);
        link(d[5], View.FOCUS_UP, settings);
        // coming back up from the dock returns to the item the focus left (when that item's DOWN leads to this tile)
        View[] tops = {source, wifi, settings, jardim, quarto, corredor, fundo};
        for (View t : tops) {
            if (t == null) continue;
            t.setOnKeyListener((v, code, ev) -> {
                if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN && ev.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    View back = lastDockTile.get(v);   // DOWN goes back to the dock tile we last came up from (same group)
                    if (back != null && back.isAttachedToWindow() && back.isFocusable()) { origin = v; back.requestFocus(); return true; }
                    origin = v;
                }
                return false;
            });
        }
        for (View tile : d) {
            tile.setOnKeyListener((v, code, ev) -> {
                if (code == android.view.KeyEvent.KEYCODE_DPAD_UP && ev.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && origin != null && origin.isAttachedToWindow() && origin.isFocusable()
                        && origin.getNextFocusDownId() == v.getId() && v.getId() != View.NO_ID) {
                    lastDockTile.put(origin, v);
                    origin.requestFocus();
                    return true;
                }
                if (code == android.view.KeyEvent.KEYCODE_DPAD_UP && ev.getAction() == android.view.KeyEvent.ACTION_DOWN && v.getNextFocusUpId() != View.NO_ID) {
                    View up = v.getRootView().findViewById(v.getNextFocusUpId());
                    if (up != null) lastDockTile.put(up, v);   // remember which tile left for this top item
                }
                return false;
            });
        }
    }
    private View origin;
    private final java.util.Map<View, View> lastDockTile = new java.util.HashMap<>();

    // The lamp icons belong to the home only: hidden (and not focusable) while the dock floats over an app.
    void setLampsAlpha(float alpha) {
        lampsAlpha = alpha;
        for (LampView v : lamps.values()) { v.setAlpha(alpha); v.setFocusable(alpha > 0.5f); }
    }
    private float lampsAlpha = 1f;

    private void placeLamps(org.json.JSONArray icons, int bottom) {
        veil.setVisibility("dawn".equals(HouseView.period) ? View.INVISIBLE : View.VISIBLE);
        applyTone();
        bg.setBackgroundColor(bottom); // below the picture the page continues in the sky's bottom colour
        if (icons == null) return;
        try {
            for (int i = 0; i < icons.length(); i++) {
                org.json.JSONObject o = icons.getJSONObject(i);
                final String name = o.getString("name");
                int box = px(Math.round((float) o.getDouble("size") * 1.9f));
                int x = Math.round((float) o.getDouble("x") * a.getResources().getDisplayMetrics().widthPixels / 1920f) - box / 2;
                int y = Math.round((float) o.getDouble("y") * a.getResources().getDisplayMetrics().heightPixels / 1080f) - box / 2;
                LampView v = lamps.get(name);
                if (v == null) {
                    v = new LampView(a, name, (float) o.getDouble("size"));
                    v.setOnClickListener(view -> HouseCards.toggleLamp(a, name, !HouseCards.lampOn(name)));
                    hookScroll(v, 0);
                    v.setAlpha(lampsAlpha);
                    v.setFocusable(lampsAlpha > 0.5f);
                    lamps.put(name, v);
                    content.addView(v, Ui.at(x, y, box, box));
                } else {
                    v.setLayoutParams(Ui.at(x, y, box, box));
                }
            }
            wireNav();
        } catch (Exception ignored) {}
    }

    private int px(int v) { return Ui.px(a, v); }

    private void buildHeader() {
        // 90 px page padding, 32 from the top: clock 62 px Black on the left, three 88 px glass buttons (32 apart) on the right
        clock = new TextClock(a);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(120));
        clock.setLetterSpacing(-0.06f);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Ui.black());
        clock.setIncludeFontPadding(false);
        clock.setFontFeatureSettings("tnum");
        clock.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(clock, Ui.at(px(90), px(56), -2, px(120)));

        int d = px(88), gap = px(32), x = a.getResources().getDisplayMetrics().widthPixels - px(90) - d;
        addRound(new LineIcon(LineIcon.SETTINGS), x, d, v -> Items.start(a, new Intent(Settings.ACTION_SETTINGS)));
        x -= d + gap;
        netIcon = addRound(new LineIcon(LineIcon.WIFI), x, d, v -> Items.start(a, new Intent(Settings.ACTION_WIFI_SETTINGS)));
        x -= d + gap;
        addRound(Icons.drawable(Icons.SOURCE), x, d, v -> SourcePicker.show(a, v));
        refreshNetwork();
        for (int i = 0; i < header.getChildCount(); i++) if (header.getChildAt(i).isFocusable()) hookScroll(header.getChildAt(i), 0);
    }

    private TextClock clock;
    private final List<FrameLayout> roundFrames = new ArrayList<>();
    private final List<ImageView> roundIcons = new ArrayList<>();

    // Dawn: the housedash tone (light glass, dark ink) on the clock, the three round buttons and the dock
    void applyTone() {
        boolean dawn = "dawn".equals(HouseView.period);
        int ink = 0xFF334155;
        if (clock != null) clock.setTextColor(dawn ? ink : Color.WHITE);
        for (FrameLayout f : roundFrames) f.setBackground(dawn ? Ui.glassPanel(a, px(48), 98.9, 0x00000000, false, 0x6BFFFFFF, 0x4DFFFFFF, 0) : Ui.glassPanel(a, px(48), 98.9, 0x1FFFFFFF, false));
        for (ImageView iv : roundIcons) { if (dawn) iv.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN); else iv.clearColorFilter(); }
        if (dockView != null) dockView.setBackground(Ui.dockPanel(a, px(70), dawn));
    }

    private ImageView addRound(Drawable icon, int x, int d, View.OnClickListener click) {
        FrameLayout f = Ui.card(a, 0, d / 2, Color.WHITE, (Runnable) null);
        f.setClipToOutline(false);
        f.setBackground(Ui.glassPanel(a, px(48), 98.9, 0x1FFFFFFF, false)); // radius 48 on an 88 box: a circle
        f.setOnClickListener(click);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(icon);
        f.addView(iv, new FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER));
        header.addView(f, Ui.at(x, px(32), d, d));
        roundFrames.add(f); roundIcons.add(iv);
        return iv;
    }

    // The home grid: the curated streaming apps, the HDMI input, the TV channels, then EVERY other installed app
    // (alphabetical), and the Play Store always last.
    private List<String> gridKeys() {
        List<String> keys = new ArrayList<>();
        PackageManager pm = a.getPackageManager();
        for (String pkg : Items.APPS) if (Items.launchIntent(pm, pkg) != null) keys.add("app:" + pkg);
        TvInputInfo hdmi = Items.mainHdmi(a);
        if (hdmi != null) keys.add("hdmi:" + hdmi.getId());
        for (String[] ch : Channels.HOME) keys.add("ch:" + ch[1]);
        java.util.Set<String> seen = new java.util.HashSet<>(java.util.Arrays.asList(Items.APPS));
        seen.add(a.getPackageName()); seen.add("com.android.vending");
        final String[] settingsPkgs = {"com.android.tv.settings", "com.tcl.settings", "com.android.settings"};
        for (String sp : settingsPkgs) seen.add(sp); // the Settings app goes after the Play Store, at the very end
        final java.util.Map<String, String> label = new java.util.HashMap<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (seen.add(pkg) && Items.launchIntent(pm, pkg) != null) label.put(pkg, Items.appLabel(a, pkg));
            }
        }
        List<String> extra = new ArrayList<>(label.keySet());
        java.util.Collections.sort(extra, (x, y) -> label.get(x).compareToIgnoreCase(label.get(y)));
        for (String pkg : extra) keys.add("app:" + pkg);
        if (Items.launchIntent(pm, "com.android.vending") != null) keys.add("store");
        for (String sp : settingsPkgs) if (Items.launchIntent(pm, sp) != null) { keys.add("app:" + sp); break; }
        // what the dock already has (Globo, Netflix, PS5...) is not repeated in the grid
        java.util.Set<String> docked = new java.util.HashSet<>(java.util.Arrays.asList(Items.DOCK));
        TvInputInfo ps5 = Items.ps5Input(a);
        if (ps5 != null) docked.add("hdmi:" + ps5.getId());
        keys.removeAll(docked);
        return keys;
    }

    private void buildContent() {
        addDock();
        int tw = px(TILE_W), th = px(TILE_H);
        List<String> keys = gridKeys();
        // 6 columns of 250 x 150 tiles, 48 apart, 90 from the sides; rows 108 apart, with the name under each tile
        for (int i = 0; i < keys.size(); i++) {
            View t = tileFor(keys.get(i), tw, th);
            int x = px(GRID_X) + (i % COLS) * (tw + px(H_GAP)), y = px(GRID_TOP) + (i / COLS) * (th + px(V_GAP));
            content.addView(t, Ui.at(x, y, tw, th));
            grid.add(t);
            // scroll so the tile and its name are fully on screen
            hookScroll(t, Math.max(0, y + th + px(150) - a.getResources().getDisplayMetrics().heightPixels), y);
            final TextView cap = Ui.text(a, 32, Color.WHITE, Ui.regular());
            cap.setText(captionFor(keys.get(i)));
            cap.setGravity(Gravity.CENTER_HORIZONTAL);
            cap.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cap.setIncludeFontPadding(false);
            cap.setVisibility(View.INVISIBLE); // only the selected tile shows its name, 19 below it
            // the tile grows 10% about its centre, so its bottom edge drops by 7.5 px
            final int selTop = y + th + Math.round(th * (Ui.SCALE - 1f) / 2f) + px(Math.round(Ui.LABEL_GAP_SELECTED));
            content.addView(cap, Ui.at(x, selTop, tw, px(40)));
            final View.OnFocusChangeListener prev = t.getOnFocusChangeListener();
            t.setOnFocusChangeListener((v, has) -> {
                if (prev != null) prev.onFocusChange(v, has);
                cap.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            });
        }
        wireGrid();
    }

    static final int TILE_W = (int) Ui.TILE_W, TILE_H = (int) Ui.TILE_H, COLS = 6, H_GAP = 48, V_GAP = 112, GRID_X = 90, GRID_TOP = GRID_TOP_, ROWS = 3; // grid starts 68 below the dock; rows 112 apart

    // The grid tile (first row) closest to the middle of a dock tile: the app "under" it.
    int gridIndexUnder(int dockIndex) {
        int dockCenter = Dock.X + Dock.PAD_X + dockIndex * (Dock.TILE_W + Dock.TILE_GAP) + Dock.TILE_W / 2;
        int best = 0, bestDist = Integer.MAX_VALUE;
        for (int c = 0; c < COLS && c < grid.size(); c++) {
            int center = GRID_X + c * (TILE_W + H_GAP) + TILE_W / 2;
            int dist = Math.abs(center - dockCenter);
            if (dist < bestDist) { bestDist = dist; best = c; }
        }
        return best;
    }

    // Selecting a tile scrolls the page to scrollYpx (a fixed position per row, so it is the same coming from any direction).
    private void hookScroll(View v, final int scrollYpx) { hookScroll(v, scrollYpx, -1); }

    // `tileTop` (grid tiles): going UP, the page only moves when the tile is not already fully on screen
    private void hookScroll(View v, final int scrollYpx, final int tileTop) {
        final View.OnFocusChangeListener old = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((view, has) -> {
            if (old != null) old.onFocusChange(view, has);
            if (!has) return;
            int target = Math.min(scrollYpx, maxScroll()), cur = scroller.getScrollY();
            if (tileTop >= 0 && target < cur && tileTop - cur >= px(24)) return; // room for it above: stay
            scrollSmooth(target);
        });
    }

    private Object scrollToken;
    private void scrollSmooth(final int target) {
        final int from = scroller.getScrollY();
        final Object token = new Object();
        scrollToken = token;
        if (from == target) return;
        Anim.run(260, new android.view.animation.DecelerateInterpolator(2f), t -> {
            if (scrollToken == token) scroller.scrollTo(0, Math.round(from + (target - from) * t));
        }, null);
    }

    private int maxScroll() {
        return Math.max(0, content.getLayoutParams().height - a.getResources().getDisplayMetrics().heightPixels);
    }

    private String captionFor(String key) {
        if (key.startsWith("app:")) return Items.appLabel(a, key.substring(4));
        if (key.startsWith("ch:")) for (String[] c : Channels.HOME) if (key.equals("ch:" + c[1])) return c[0];
        if (key.startsWith("hdmi:")) for (TvInputInfo in : Items.hdmiInputs(a)) if (key.equals("hdmi:" + in.getId())) return Items.inputLabel(a, in);
        return "Play Store";
    }

    private void addDock() {
        if (dockView != null) content.removeView(dockView);
        dockView = Dock.build(a, false, null);
        dockSig = Dock.signature(a);
        content.addView(dockView, 2, Ui.at(px(Dock.X), px(DOCK_TOP), px(Dock.W), px(Dock.H)));
        for (int i = 0; i < dockView.getChildCount(); i++) hookScroll(dockView.getChildAt(i), 0); // the dock is at the top
        applyTone();
        wireNav();
    }

    // "último app" / "última HDMI" may have changed while an app was open; true when the dock was redrawn
    boolean refreshDock() {
        if (dockSig.equals(Dock.signature(a))) return false;
        addDock();
        return true;
    }

    void focusDock(int index) {
        if (dockView != null && index >= 0 && index < dockView.getChildCount()) dockView.getChildAt(index).requestFocus();
    }

    void focusGrid(int index) {
        if (index >= 0 && index < grid.size()) grid.get(index).requestFocus();
    }

    int focusedDockIndex() {
        View f = a.getCurrentFocus();
        if (f != null && dockView != null) for (int i = 0; i < dockView.getChildCount(); i++) if (dockView.getChildAt(i) == f) return i;
        return 0;
    }

    // In the dock state the peeking grid must not take focus.
    void setGridFocusable(boolean on) {
        for (View t : grid) t.setFocusable(on);
    }

    private View tileFor(final String key, int w, int h) {
        Runnable go = () -> Items.launch(a, key);
        if (key.startsWith("ch:")) {
            String[] ch = null;
            for (String[] c : Channels.HOME) if (key.equals("ch:" + c[1])) ch = c;
            FrameLayout t = Ui.tile(a, 0xFF1E1A1C, go);
            t.addView(Ui.picture(a, Ui.channelPicture(a, ch, w, h)), new FrameLayout.LayoutParams(-1, -1));
            return t;
        }
        if (key.startsWith("app:")) {
            String pkg = key.substring(4);
            Drawable banner = Items.banner(a, pkg, w, h);
            if (banner != null) {
                FrameLayout t = Ui.tile(a, 0xFF1E1A1C, go);
                t.addView(Ui.picture(a, banner), new FrameLayout.LayoutParams(-1, -1));
                return t;
            }
            FrameLayout t = Ui.tile(a, 0x8014203A, go);
            ImageView iv = new ImageView(a);
            try { iv.setImageDrawable(a.getPackageManager().getApplicationIcon(pkg)); } catch (Exception ignored) {}
            t.addView(iv, new FrameLayout.LayoutParams(px(72), px(72), Gravity.CENTER));
            return t;
        }
        FrameLayout t = Ui.tile(a, 0x8014203A, go);
        if (key.startsWith("hdmi:")) {
            String label = "HDMI";
            for (TvInputInfo in : Items.hdmiInputs(a)) if (key.equals("hdmi:" + in.getId())) label = Items.inputLabel(a, in);
            if (Items.isPlayStation(label)) { // the console gets its own look: gradient and logo
                t.addView(Ui.picture(a, Ui.ps5Picture(a, w, h)), new FrameLayout.LayoutParams(-1, -1));
                return t;
            }
            t.addView(Ui.iconLabel(a, LineIcon.HDMI, label, null), new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            t.addView(Ui.picture(a, Ui.playStorePicture(a, w, h)), new FrameLayout.LayoutParams(-1, -1));
        }
        return t;
    }

    void refreshNetwork() {
        if (netIcon == null) return;
        ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
        int[] icon = LineIcon.WIFI_1;
        float alpha = 0.4f;
        try {
            NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (c != null) {
                alpha = 1f;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) icon = LineIcon.ETHERNET;
                else {
                    int rssi = android.os.Build.VERSION.SDK_INT >= 29 ? c.getSignalStrength() : Integer.MIN_VALUE;
                    if (rssi == Integer.MIN_VALUE) {
                        WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
                    }
                    int bars = 1 + WifiManager.calculateSignalLevel(rssi, 3);
                    icon = bars >= 3 ? LineIcon.WIFI : bars == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI_1;
                }
            }
        } catch (Exception ignored) {}
        netIcon.setImageDrawable(new LineIcon(icon));
        netIcon.setAlpha(alpha);
    }
}
