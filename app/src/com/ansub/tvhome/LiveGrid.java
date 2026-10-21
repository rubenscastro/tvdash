package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// The Live tab of the Cerberus screen: every camera as a picture (a keyframe read every few seconds), OK opens it full screen.
// Up from the first row hands the focus back to the tabs.
final class LiveGrid extends FrameLayout {
    private static final int COLS = 3, MARGIN = 96, GAP = 36, BORDER = 6;

    private static class Tile { FrameLayout box; GradientDrawable ring; ImageView img; TextView status; }
    private final List<Tile> tiles = new ArrayList<>();
    private volatile boolean running;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Activity a;
    private final float scale;
    private final java.util.function.IntConsumer onUpFromTop;

    private int px(int v) { return Math.round(v * scale); }

    LiveGrid(Activity activity, java.util.function.IntConsumer onUpFromTop) {
        super(activity);
        this.a = activity;
        this.onUpFromTop = onUpFromTop;
        final android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        scale = dm.widthPixels / 1920f;
        // Two big cameras on the first row, the other three share the second; the block fills the height under the header
        // and is centred on the screen.
        final int sideM = px(60), g = px(20);
        final int avail = dm.heightPixels - px(200) - px(24);
        final int rowW = dm.widthPixels - 2 * sideM;
        final int h1 = Math.round((avail - g) * 0.56f), h2 = avail - g - h1;
        final int w1 = (rowW - g) / 2, w2 = (rowW - 2 * g) / 3;
        for (int i = 0; i < Cameras.LIST.length; i++) {
            final int idx = i;
            final boolean big = i < 2;
            final Tile t = new Tile();
            t.box = new FrameLayout(activity);
            t.ring = new GradientDrawable();
            t.ring.setCornerRadius(px(16));
            t.ring.setColor(0x14FFFFFF);
            t.ring.setStroke(px(BORDER), Color.TRANSPARENT);
            t.box.setBackground(t.ring);
            t.box.setPadding(px(BORDER), px(BORDER), px(BORDER), px(BORDER));
            t.box.setFocusable(true);
            t.img = new ImageView(activity);
            t.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.box.addView(t.img, new FrameLayout.LayoutParams(-1, -1));
            t.status = new TextView(activity);
            t.status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(22));
            t.status.setTextColor(0xB0FFFFFF);
            t.status.setText("conectando…");
            t.box.addView(t.status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            t.box.setOnFocusChangeListener((v, has) -> t.ring.setStroke(px(BORDER), has ? Color.WHITE : Color.TRANSPARENT));
            t.box.setOnClickListener(v -> a.startActivity(new Intent(a, CamPlayer.class).putExtra("index", idx)));
            t.box.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_BACK) && idx < 2) {
                    LiveGrid.this.onUpFromTop.accept(idx);
                    return true;
                }
                return false;
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(big ? w1 : w2, big ? h1 : h2);
            lp.leftMargin = sideM + (big ? i * (w1 + g) : (i - 2) * (w2 + g));
            lp.topMargin = big ? 0 : h1 + g;
            addView(t.box, lp);
            tiles.add(t);
        }
        // nothing selected while the focus is in the header
        park = new View(activity);
        park.setFocusable(true);
        addView(park, new FrameLayout.LayoutParams(1, 1));
    }

    private final View park;

    // takes the focus off the cameras (no white ring) without handing it to anything visible
    void blur() { park.requestFocus(); }

    void focusTile(int i) { tiles.get(Math.max(0, Math.min(tiles.size() - 1, i))).box.requestFocus(); }

    void start() {
        if (running) return;
        running = true;
        refreshLoop();
    }

    void stop() { running = false; }

    // Previews: every camera is opened at once only to read its first keyframe, then closed; the keyframes are decoded one at a
    // time. Repeats while the tab is visible.
    private void refreshLoop() {
        new Thread(() -> {
            while (running) {
                final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(tiles.size());
                for (int i = 0; i < tiles.size(); i++) {
                    final Tile t = tiles.get(i);
                    RtspPlayer g = new RtspPlayer(Cameras.HOST, Cameras.PORT, Cameras.LIST[i][0], null, new RtspPlayer.Listener() {
                        @Override public void onState(String st) {
                            if (st.startsWith("erro")) { ui.post(() -> { if (t.img.getDrawable() == null) t.status.setText("sem sinal"); }); done.countDown(); }
                        }
                        @Override public void onSize(int w, int h) {}
                        @Override public void onGrab(String codec, byte[] kf) {
                            new Thread(() -> {
                                Bitmap bmp = running ? StillDecoder.decode(codec, kf, 2) : null;
                                ui.post(() -> {
                                    if (bmp != null) { t.img.setImageBitmap(bmp); t.status.setText(""); }
                                    else if (t.img.getDrawable() == null) t.status.setText("sem imagem");
                                });
                                done.countDown();
                            }).start();
                        }
                    });
                    g.setGrabOnly(true);
                    g.start();
                    final long deadline = System.currentTimeMillis() + 30000;
                    new Thread(() -> { // give up on a camera that never sends a keyframe
                        while (running && System.currentTimeMillis() < deadline && g.alive()) {
                            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                        }
                        g.stop();
                    }).start();
                }
                try { done.await(40, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { return; }
                for (int k = 0; k < 20 && running; k++) { try { Thread.sleep(500); } catch (InterruptedException e) { return; } }
            }
        }, "cam-previews").start();
    }
}
