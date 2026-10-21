package com.ansub.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// Our own antenna screen: a full-screen TvView on the TV's tuner input, with a small channel
// banner and a channel list. If the channel list can't be read, it hands over to the TV's own app.
public class TvActivity extends Activity {
    private static final String PREFS = "tvhome", LAST = "last_channel";
    private static final int LIST_ROWS = 6; // rows visible in the list (the header with the segmented control takes the rest of the frame)
    private static final int ROW_H = 136, PANEL_W = 833, LOGO = 72, SEG_H = 112, CHIPS_H = 96;

    private static class Channel {
        long id; String number; String name; String logo; double sort; String input; boolean main; String category;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Channel> channels = new ArrayList<>();
    private String inputId;
    private int current = -1;
    private TvView tv;
    private long tuneAt; private String tuneName = "";
    private StaticView snow; // analog-TV static while the tuner has no picture
    private LinearLayout banner;
    private ImageView bannerLogo;
    private ImageView bannerSource;
    private TextView bannerName, bannerProgram, status, digits;
    private boolean preview; // screenshots only: no video, just the overlays
    private FrameLayout listPanel;
    private LinearLayout listCol;
    private View scrollThumb;
    private final List<View> rows = new ArrayList<>();
    private boolean listOpen;
    private View listVeil;     // dark gradient behind the channel list
    private int listSel;
    private int listTab = 0, listCat = 0, listZone = 2; // tab: 0 Principais / 1 Todos; category chip (0 = Todas); focus: 0 tabs, 1 chips, 2 rows
    private final List<Channel> view = new ArrayList<>();   // what the list shows now
    private final List<String> cats = new ArrayList<>();
    private LinearLayout segBox, chipsRow;
    private TextView[] segTabs = new TextView[2];
    private android.widget.HorizontalScrollView chipsScroll;
    private final StringBuilder typed = new StringBuilder();
    private final Runnable hideBanner = () -> banner.setVisibility(View.GONE);
    private final Runnable applyTyped = this::tuneTyped;

    // The tuner input built into the TV (ids look like .../HW2); other apps also list tuner-type inputs.
    static String tunerInputId(Context c) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return null;
        String fallback = null;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() != TvInputInfo.TYPE_TUNER || in.getParentId() != null) continue;
            if (in.getId().contains("/HW")) return in.getId();
            if (fallback == null) fallback = in.getId();
        }
        return fallback;
    }

    // The TV's own antenna app, opened on the tuner's channels. Used when ours can't work.
    static void openStockTv(Context c, String inputId) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(inputId));
        List<Intent> tries = new ArrayList<>();
        tries.add(new Intent(view).setClassName("com.tcl.tv", "com.tcl.player.TVActivity"));
        tries.add(new Intent(view).setClassName("com.android.tv", "com.android.tv.MainActivity"));
        tries.add(view);
        for (Intent i : tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { c.startActivity(i); return; } catch (Exception ignored) {}
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        inputId = tunerInputId(this);
        if (inputId != null) loadChannels();
        if (inputId == null || channels.isEmpty()) {
            if (inputId != null) openStockTv(this, inputId);
            finish();
            return;
        }
        preview = getIntent().getBooleanExtra("preview", false);
        buildUi();
        tune(startIndex(getIntent()));
        if (preview) { showBanner(); handler.removeCallbacks(hideBanner); openList(getIntent().getBooleanExtra("list", false)); }
    }

    // The channel asked for by a home tile, else the last one watched, else the first.
    private int startIndex(Intent in) {
        long want = in.getLongExtra("channel_id", -1);
        if (want < 0) want = getSharedPreferences(PREFS, 0).getLong(LAST, -1);
        for (int i = 0; i < channels.size(); i++) if (channels.get(i).id == want) return i;
        return 0;
    }

    @Override protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        setIntent(in);
        if (!channels.isEmpty() && in.hasExtra("channel_id")) tune(startIndex(in));
        if (preview) openList(in.getBooleanExtra("list", false));
    }

    private void loadChannels() {
        for (String[] row : Channels.LIST) channels.add(channelOf(row, true, Channels.categoryOfMain(row[2])));
        for (String[] row : Channels.EXTRA) channels.add(channelOf(row, false, row[5]));
        channels.sort((x, y) -> Double.compare(x.sort, y.sort)); // ascending by channel number
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        String[] order = {"Abertos", "Notícias", "Religiosos", "Legislativos", "Educativos", "Filmes e séries", "Infantil", "Esportes", "Música", "Anime", "Variedades"};
        for (String c : order) for (Channel ch : channels) if (ch.category.equals(c)) { set.add(c); break; }
        cats.add("Todas");
        cats.addAll(set);
    }

    private Channel channelOf(String[] row, boolean main, String category) {
        Channel ch = new Channel();
        ch.number = row[0];
        ch.name = row[1];
        ch.id = Long.parseLong(row[2]);
        ch.logo = row[3];
        ch.input = row.length > 4 && row[4].equals("ip") ? Channels.IP_INPUT : null; // null: the antenna
        ch.sort = parseNumber(ch.number);
        ch.main = main;
        ch.category = category;
        return ch;
    }

    // The list's content: Principais = the main channels; Todos = everything, narrowed by the category chip.
    private void buildView() {
        view.clear();
        for (Channel ch : channels) {
            if (listTab == 0 ? ch.main : (listCat == 0 || ch.category.equals(cats.get(listCat)))) view.add(ch);
        }
    }

    // CH + / -: the next MAIN channel in that direction (the other ones are reached through the list)
    private int stepMain(int dir) {
        int n = channels.size();
        for (int k = 1; k <= n; k++) {
            int i = ((current + dir * k) % n + n) % n;
            if (channels.get(i).main) return i;
        }
        return current;
    }

    // "7" -> 7, "7.2" or "7-2" -> 7.2, anything else sorts last.
    private static double parseNumber(String n) {
        try { return Double.parseDouble(n.replace('-', '.').trim()); } catch (Exception e) { return 1e9; }
    }

    // Figma design px (frame 1940 wide) -> screen px
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().widthPixels / 1940f); }

    private TextView label(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(sp));
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setSingleLine(true);
        return t;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        snow = new StaticView(this);
        root.addView(snow, new FrameLayout.LayoutParams(-1, -1));
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String id) { status.setVisibility(View.GONE); snow.stop(); android.util.Log.d("tunetime", "video after " + (android.os.SystemClock.elapsedRealtime() - tuneAt) + " ms (" + tuneName + ")"); }
            @Override public void onVideoUnavailable(String id, int reason) {
                snow.start();
                status.setText(reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ? "Sintonizando..." : "Sem sinal");
                status.setVisibility(status.length() == 0 ? View.GONE : View.VISIBLE);
            }
            @Override public void onConnectionFailed(String id) { openStockTv(TvActivity.this, id); finish(); }
        });

        status = label(26, 0xFFFFFFFF, false);
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // Channel banner (Figma 7:370): logo, name (bold) with the current programme under it (when known), and the
        // channel number on the right, in a wide glass pill at the bottom left
        banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(32), dp(32), dp(32), dp(32));
        banner.setBackground(Ui.menuPanel(this, dp(56), 140.88, 0.40f, 0.48f));
        bannerLogo = new ImageView(this);
        bannerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        banner.addView(bannerLogo, new LinearLayout.LayoutParams(dp(83), dp(82)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setGravity(Gravity.CENTER_VERTICAL);
        names.setPadding(dp(32), 0, dp(32), 0);
        bannerName = label(40, Color.WHITE, true);
        names.addView(bannerName);
        bannerProgram = label(30, 0x99FFFFFF, false);
        bannerProgram.setVisibility(View.GONE); // the programme guide is not readable by this app
        names.addView(bannerProgram);
        banner.addView(names, new LinearLayout.LayoutParams(0, dp(82), 1f));
        bannerSource = new ImageView(this); // where the channel comes from: antenna or internet
        bannerSource.setAlpha(0.5f);
        banner.addView(bannerSource, new LinearLayout.LayoutParams(dp(44), dp(44)));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(PANEL_W), -2, Gravity.BOTTOM | Gravity.START); // same width as the channel list
        bl.leftMargin = dp(82);
        bl.bottomMargin = dp(90);
        root.addView(banner, bl);

        digits = label(60, Color.WHITE, true);
        digits.setVisibility(View.GONE);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        dl.setMargins(0, dp(40), dp(56), 0);
        root.addView(digits, dl);

        // Expanded list (Figma 7:408): 7 rows of 136, the selected one in a blue pill, a scroll bar on the right
        listPanel = new FrameLayout(this);
        listPanel.setBackground(Ui.menuPanel(this, dp(56), 97.26, 0.30f, 0.38f));
        listPanel.setVisibility(View.GONE);
        listCol = new LinearLayout(this);
        final LinearLayout col = listCol;
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(1, 1, 1, 1);
        // segmented control: Principais / Todos
        segBox = new LinearLayout(this);
        segBox.setOrientation(LinearLayout.HORIZONTAL);
        segBox.setGravity(Gravity.CENTER_VERTICAL);
        segBox.setPadding(dp(24), dp(14), dp(24), dp(14));
        String[] segNames = {"Principais", "Todos"};
        for (int i = 0; i < 2; i++) {
            TextView t = label(34, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            segTabs[i] = t;
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i == 1) sp.leftMargin = dp(12);
            t.setText(segNames[i]);
            segBox.addView(t, sp);
        }
        col.addView(segBox, new LinearLayout.LayoutParams(-1, dp(SEG_H)));
        // category chips (Todos only)
        chipsScroll = new android.widget.HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipsScroll.setFocusable(false);
        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        chipsRow.setPadding(dp(24), dp(10), dp(24), dp(10));
        for (int i = 0; i < cats.size(); i++) {
            TextView c = label(30, Color.WHITE, false);
            c.setPadding(dp(30), dp(10), dp(30), dp(10));
            c.setText(cats.get(i));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) cp.leftMargin = dp(14);
            chipsRow.addView(c, cp);
        }
        chipsScroll.addView(chipsRow);
        chipsScroll.setVisibility(View.GONE);
        col.addView(chipsScroll, new LinearLayout.LayoutParams(-1, dp(CHIPS_H)));
        for (int i = 0; i < LIST_ROWS; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(32), 0, dp(32), 0);
            ImageView check = new ImageView(this); // marks the channel being watched
            check.setImageBitmap(Channels.logo(this, "check")); // Figma check, 56 px box
            r.addView(check, new LinearLayout.LayoutParams(dp(56), dp(56)));
            ImageView logo = new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(LOGO), dp(LOGO));
            ilp.leftMargin = dp(24);
            r.addView(logo, ilp);
            TextView name = label(40, Color.WHITE, false);
            name.setPadding(dp(24), 0, dp(24), 0);
            r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView src = new ImageView(this); // antenna or internet
            src.setAlpha(0.5f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(44), dp(44));
            slp.rightMargin = dp(32);
            r.addView(src, slp);
            rows.add(r);
            col.addView(r, new LinearLayout.LayoutParams(-1, dp(ROW_H)));
        }
        listPanel.addView(col, new FrameLayout.LayoutParams(-1, -2));
        scrollThumb = new View(this);
        scrollThumb.setBackground(Ui.rounded(0x80FFFFFF, dp(48)));
        listPanel.addView(scrollThumb, new FrameLayout.LayoutParams(dp(16), dp(100)));
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(dp(PANEL_W), dp(LIST_ROWS * ROW_H + 2), Gravity.TOP | Gravity.START);
        ll.leftMargin = dp(82);
        ll.topMargin = dp(40);
        // Figma "Rectangle 31": 1920 x 1092, linear-gradient(270deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.5) 100%) behind the channel list; it fades with the list
        listVeil = new View(this);
        listVeil.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[] {0x00FF0000, 0xC0FF0000})); // TEMP: red test
        listVeil.setVisibility(View.GONE);
        root.addView(listVeil, new FrameLayout.LayoutParams(dp(1920), dp(1092)));
        root.addView(listPanel, ll);
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));
    }

    private GradientDrawable glass(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private void tune(int index) { tune(index, 0); }

    // dir: +1 = the new channel is below the current one in the list, -1 = above (the banner moves that way), 0 = no movement
    private void tune(int index, int dir) {
        if (channels.isEmpty()) return;
        final int old = current;
        current = (index % channels.size() + channels.size()) % channels.size();
        Channel ch = channels.get(current);
        tuneAt = android.os.SystemClock.elapsedRealtime(); tuneName = ch.number + " " + ch.name + " via " + (ch.input != null ? ch.input : inputId);
        if (!preview) { snow.start(); status.setText("Sintonizando..."); status.setVisibility(View.VISIBLE); tv.tune(ch.input != null ? ch.input : inputId, TvContract.buildChannelUri(ch.id)); }
        getSharedPreferences(PREFS, 0).edit().putLong(LAST, ch.id).apply();
        showBanner(dir, old);
    }

    private void showBanner() { showBanner(0, current); }

    private void fillBanner() { fillBanner(channels.get(current)); }

    private void fillBanner(Channel ch) {
        bannerSource.setImageDrawable(new LineIcon(ch.input != null ? LineIcon.GLOBE : LineIcon.ANTENNA));
        bannerName.setText(ch.name);
        setLogo(bannerLogo, ch);
    }

    private Object bannerAnim;

    // With a direction the banner of the old channel moves out (up when the new channel is further down the list, down otherwise)
    // and fades, then the banner of the new channel moves in from the opposite side and fades in.
    private void showBanner(final int dir, final int old) {
        if (listOpen) return;
        handler.removeCallbacks(hideBanner);
        final Object token = new Object();
        bannerAnim = token;
        if (dir != 0 && !preview && banner.getVisibility() != View.VISIBLE) {   // hidden: show the OLD channel's banner first so it can move out
            fillBanner(channels.get(old));
            banner.setTranslationY(0); banner.setAlpha(1f);
            banner.setVisibility(View.VISIBLE);
        }
        if (dir == 0 || preview) {
            fillBanner();
            banner.setTranslationY(0); banner.setAlpha(1f);
            banner.setVisibility(View.VISIBLE);
            if (!preview) handler.postDelayed(hideBanner, 3500);
            return;
        }
        final float dist = dp(64) * -dir;
        final float from0 = banner.getTranslationY(), alpha0 = banner.getAlpha();
        Anim.run(400, new android.view.animation.AccelerateDecelerateInterpolator(), t -> {
            if (bannerAnim != token) return;
            banner.setTranslationY(from0 + (dist - from0) * t);
            banner.setAlpha(alpha0 * (1f - t));
        }, () -> {
            if (bannerAnim != token) return;
            fillBanner();
            Anim.run(320, new android.view.animation.DecelerateInterpolator(1.6f), t -> {
                if (bannerAnim != token) return;
                banner.setTranslationY(-dist * (1f - t));
                banner.setAlpha(t);
            }, () -> {
                if (bannerAnim != token) return;
                banner.setTranslationY(0); banner.setAlpha(1f);
                handler.postDelayed(hideBanner, 3500);
            });
        });
    }

    private void setLogo(ImageView iv, Channel ch) {
        android.graphics.Bitmap b = Channels.listLogo(this, ch.logo, dp(LOGO));
        iv.setImageBitmap(b);
        iv.setVisibility(b == null ? View.INVISIBLE : View.VISIBLE);
    }

    private void tuneTyped() {
        String want = typed.toString();
        typed.setLength(0);
        digits.setVisibility(View.GONE);
        for (int i = 0; i < channels.size(); i++)
            if (channels.get(i).number.equals(want) || channels.get(i).number.startsWith(want + ".")) {
                tune(i);
                return;
            }
    }

    private void openList(boolean open) {
        final boolean wasShown = listPanel.getVisibility() == View.VISIBLE;
        listOpen = open;
        if (open) listPanel.setVisibility(View.VISIBLE);
        final float from = listVeil.getVisibility() == View.VISIBLE ? listVeil.getAlpha() : 0f;
        if (open) {
            listVeil.setVisibility(View.VISIBLE);
            Anim.run(240, new android.view.animation.DecelerateInterpolator(1.5f), p -> { if (listOpen) listVeil.setAlpha(from + (1f - from) * p); }, null);
        } else if (listVeil.getVisibility() == View.VISIBLE) {
            Anim.run(180, new android.view.animation.AccelerateInterpolator(), p -> { if (!listOpen) listVeil.setAlpha(from * (1f - p)); },
                    () -> { if (!listOpen) listVeil.setVisibility(View.GONE); });
        }
        if (open) {
            handler.removeCallbacks(hideBanner);
            Channel cur = channels.get(current);
            listTab = cur.main ? 0 : 1;                      // a channel that is not a main one opens the list on Todos...
            listCat = cats.indexOf(cur.category);            // ...in the category it belongs to
            if (listTab == 0 || listCat < 0) listCat = 0;
            listZone = 2;
            buildView();
            listSel = Math.max(0, view.indexOf(channels.get(current)));
            listFirst = 0;
            drawList();
            expandFromBanner();
            handler.postDelayed(() -> android.util.Log.d("veil", "vis=" + listVeil.getVisibility() + " alpha=" + listVeil.getAlpha() + " size=" + listVeil.getWidth() + "x" + listVeil.getHeight() + " at " + listVeil.getLeft() + "," + listVeil.getTop() + " idx=" + ((ViewGroup) listVeil.getParent()).indexOfChild(listVeil) + "/" + ((ViewGroup) listVeil.getParent()).getChildCount() + " panelIdx=" + ((ViewGroup) listVeil.getParent()).indexOfChild(listPanel) + " tvIdx=" + ((ViewGroup) listVeil.getParent()).indexOfChild(tv)), 900);
        } else {
            showBanner();
            if (wasShown) collapseToBanner(); else listPanel.setVisibility(View.GONE);
        }
    }

    // The list grows out of the channel banner (and shrinks back into it): the selected row starts exactly where the banner is and
    // the panel opens up and down around it. The panel is clipped to a rounded window, so the corner radius stays on the whole time.
    private float winP = 1f, winSelTop, winSelBottom, winTy0;
    private int winFull, winW;
    private boolean winBanner;

    private void prepareWindow() {
        final int rowH = dp(ROW_H);
        winSelTop = dp(1) + headerH() + (listSel - listFirst) * rowH; winSelBottom = winSelTop + rowH;
        winFull = listPanel.getLayoutParams().height; winW = dp(PANEL_W);
        float bannerCenter = getResources().getDisplayMetrics().heightPixels - dp(90) - dp(146) / 2f;
        winTy0 = bannerCenter - (dp(40) + (winSelTop + winSelBottom) / 2f);
        listPanel.setClipToOutline(true);
        listPanel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, Math.round(winSelTop * (1f - winP)), winW, Math.round(winSelBottom + (winFull - winSelBottom) * winP), dp(56));
            }
        });
    }

    private void applyWindow(float p) {
        winP = p;
        listPanel.setTranslationY((1f - p) * winTy0);
        listPanel.invalidateOutline();
        listPanel.setAlpha(Math.min(1f, 0.3f + p * 1.4f));
        if (winBanner) banner.setAlpha(1f - Math.min(1f, p * 2.5f));
    }

    private void expandFromBanner() {
        prepareWindow();
        winBanner = banner.getVisibility() == View.VISIBLE;
        applyWindow(0f);
        Anim.run(340, new android.view.animation.DecelerateInterpolator(2f), p -> { if (listOpen) applyWindow(p); }, () -> {
            if (!listOpen) return;
            applyWindow(1f);
            listPanel.setClipToOutline(false);
            banner.setAlpha(1f);
            banner.setVisibility(View.GONE);
        });
    }

    private void collapseToBanner() {
        prepareWindow();
        winBanner = true;
        banner.setAlpha(0f);
        Anim.run(260, new android.view.animation.AccelerateInterpolator(1.5f), p -> { if (!listOpen) applyWindow(1f - p); }, () -> {
            if (listOpen) return;
            listPanel.setVisibility(View.GONE);
            listPanel.setClipToOutline(false);
            listPanel.setTranslationY(0);
            listPanel.setAlpha(1f);
            banner.setAlpha(1f);
        });
    }

    private int listFirst = 0;

    private int headerH() { return dp(SEG_H) + (listTab == 1 ? dp(CHIPS_H) : 0); }

    private void drawHeader() {
        for (int i = 0; i < 2; i++) {
            TextView t = segTabs[i];
            boolean active = i == listTab, focused = listZone == 0 && active;
            GradientDrawable g = Ui.rounded(active ? 0x33FFFFFF : 0x00000000, dp(100));
            if (focused) g.setStroke(dp(6), Color.WHITE);
            t.setBackground(g);
            t.setAlpha(active ? 1f : 0.6f);
        }
        chipsScroll.setVisibility(listTab == 1 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < chipsRow.getChildCount(); i++) {
            TextView c = (TextView) chipsRow.getChildAt(i);
            boolean active = i == listCat, focused = listZone == 1 && active;
            GradientDrawable g = Ui.rounded(active ? 0x33FFFFFF : 0x14FFFFFF, dp(100));
            if (focused) g.setStroke(dp(6), Color.WHITE);
            c.setBackground(g);
            c.setAlpha(active ? 1f : 0.7f);
        }
        if (listTab == 1 && listCat < chipsRow.getChildCount()) {
            final View chip = chipsRow.getChildAt(listCat);
            chipsScroll.post(() -> chipsScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(120)), 0));
        }
    }

    private void drawList() {
        drawHeader();
        // keep the selection inside the visible window
        if (listSel < listFirst) listFirst = listSel;
        if (listSel >= listFirst + LIST_ROWS) listFirst = listSel - LIST_ROWS + 1;
        listFirst = Math.max(0, Math.min(listFirst, Math.max(0, view.size() - LIST_ROWS)));
        Channel playing = channels.get(current);
        for (int i = 0; i < LIST_ROWS; i++) {
            LinearLayout r = (LinearLayout) rows.get(i);
            int idx = listFirst + i;
            if (idx >= view.size()) { r.setVisibility(View.GONE); r.setBackground(null); continue; }
            r.setVisibility(View.VISIBLE);
            Channel ch = view.get(idx);
            r.getChildAt(0).setVisibility(ch == playing ? View.VISIBLE : View.INVISIBLE);
            setLogo((ImageView) r.getChildAt(1), ch);
            ((TextView) r.getChildAt(2)).setText(ch.name);
            ((ImageView) r.getChildAt(3)).setImageDrawable(new LineIcon(ch.input != null ? LineIcon.GLOBE : LineIcon.ANTENNA));
            if (idx == listSel && listZone == 2) {
                GradientDrawable sel = Ui.rounded(0x1AFFFFFF, dp(100));
                sel.setStroke(dp(6), Color.WHITE);
                r.setBackground(sel);
            } else {
                r.setBackground(null);
            }
        }
        // rows always span the whole panel (like the source menu) and there is no scroll bar: the list scrolls by selection
        FrameLayout.LayoutParams cl = (FrameLayout.LayoutParams) listCol.getLayoutParams();
        cl.rightMargin = 0;
        listCol.setLayoutParams(cl);
        FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) listPanel.getLayoutParams();
        pl.height = headerH() + dp(Math.min(view.size(), LIST_ROWS) * ROW_H + 2);
        listPanel.setLayoutParams(pl);
        scrollThumb.setVisibility(View.GONE);
    }

    // Keys while the list is open: the segmented control (zone 0), the category chips (1, Todos only) and the rows (2).
    private boolean onListKey(int code, boolean up, boolean down) {
        boolean enter = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (code == KeyEvent.KEYCODE_BACK) { openList(false); return true; }
        // From the categories or the rows, left / right walk one line of tabs: Principais, then Todos' categories (Todas, Notícias,
        // ...); left on the first category goes back to Principais.
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && listTab == 0) { openList(false); return true; }   // left on Principais closes the list
        if (listZone == 0 && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // on the Principais / Todos control the sideways keys only switch between those two
            int t = code == KeyEvent.KEYCODE_DPAD_LEFT ? 0 : 1;
            if (t != listTab) { listTab = t; listCat = 0; buildView(); listSel = 0; listFirst = 0; drawList(); }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int pos = listTab == 0 ? 0 : 1 + listCat;
            int to = Math.max(0, Math.min(cats.size(), pos + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
            if (to != pos) {
                listTab = to == 0 ? 0 : 1;
                listCat = to == 0 ? 0 : to - 1;
                buildView(); listSel = 0; listFirst = 0;
                if (listZone == 1 && listTab == 0) listZone = 0;
                drawList();
            }
            return true;
        }
        if (listZone == 0) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || enter) listZone = listTab == 1 ? 1 : 2;
            else return false;
        } else if (listZone == 1) {
            if (code == KeyEvent.KEYCODE_DPAD_UP) listZone = 0;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN || enter) { if (!view.isEmpty()) listZone = 2; }
            else return false;
        } else {
            if (code == KeyEvent.KEYCODE_DPAD_UP && listSel == 0) listZone = listTab == 1 ? 1 : 0;
            else if (code == KeyEvent.KEYCODE_DPAD_UP || up) listSel = Math.max(0, listSel - (up ? LIST_ROWS : 1));
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN || down) listSel = Math.min(view.size() - 1, listSel + (down ? LIST_ROWS : 1));
            else if (enter) {
                Channel pick = view.get(listSel);
                int to = channels.indexOf(pick);
                openList(false);
                if (to != current) tune(to);        // OK on the channel already playing only closes the list
                return true;
            } else return false;
        }
        drawList();
        return true;
    }

    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        if (channels.isEmpty()) return super.onKeyDown(code, ev);
        // channel + / - are inverted on purpose: + goes to the previous channel of the list, - to the next
        boolean up = code == KeyEvent.KEYCODE_CHANNEL_DOWN || code == KeyEvent.KEYCODE_PAGE_DOWN;
        boolean down = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_PAGE_UP;
        if (listOpen) return onListKey(code, up, down) || super.onKeyDown(code, ev);
        if (code == KeyEvent.KEYCODE_DPAD_UP) { openList(true); return true; }                   // up opens the channel list
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) { showBanner(); return true; } // sideways shows the channel banner
        if (up) { tune(stepMain(1), 1); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {                                                  // down opens the dock over the channel
            Items.appInFront = true;
            // never leave a home screen under the dock, and a dock that became the (opaque) home is spent: a fresh, translucent
            // one goes over the channel (the same rule as Home pressed over an app)
            if (HomeActivity.instance != null) HomeActivity.instance.finish();
            if (DockActivity.current != null && DockActivity.current.isOpaque()) DockActivity.current.finish();
            startActivity(new Intent(this, DockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            overridePendingTransition(0, 0);
            return true;
        }
        if (down) { tune(stepMain(-1), -1); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_TV_INPUT_HDMI_1 && false) {
            openList(true);
            return true;
        }
        if (code == KeyEvent.KEYCODE_INFO || code == KeyEvent.KEYCODE_MENU) { showBanner(); return true; }
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 && typed.length() < 4) {
            typed.append((char) ('0' + code - KeyEvent.KEYCODE_0));
            digits.setText(typed.toString());
            digits.setVisibility(View.VISIBLE);
            handler.removeCallbacks(applyTyped);
            handler.postDelayed(applyTyped, 1600);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(code, ev);
    }

    // The TV view hands number keys to the TV's input service first, so catch them before it does.
    @Override public boolean dispatchKeyEvent(KeyEvent ev) {
        if (RemoteKeys.handle(this, ev)) return true;
        int code = ev.getKeyCode();
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) onKeyDown(code, ev);
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (tv != null) tv.reset(); } catch (Exception ignored) {}
    }

    @Override protected void onStop() {
        super.onStop();
        handler.removeCallbacksAndMessages(null);
        try { tv.reset(); } catch (Exception ignored) {}   // stop the channel's audio/video session right away
        finish(); // leaving (Home, another app) releases the tuner
    }
}
