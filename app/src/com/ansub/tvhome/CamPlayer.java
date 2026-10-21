package com.ansub.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

// One camera full screen. Left/Right switch camera; Back returns to the grid.
public class CamPlayer extends Activity implements SurfaceHolder.Callback {
    private RtspPlayer player;
    private SurfaceView sv;
    private TextView status, name;
    private int index;
    private boolean surfaceReady;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideName = () -> name.setVisibility(View.GONE);
    private float scale;

    private int px(int v) { return Math.round(v * scale); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        index = getIntent().getIntExtra("index", 0);
        if (getIntent().hasExtra("cam")) { // debug: by stream name
            for (int i = 0; i < Cameras.LIST.length; i++) if (Cameras.LIST[i][0].equals(getIntent().getStringExtra("cam"))) index = i;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(30));
        status.setTextColor(0xB0FFFFFF);
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        name = new TextView(this);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(30));
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(px(24), px(10), px(24), px(10));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x99101012);
        chip.setCornerRadius(px(30));
        name.setBackground(chip);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        nl.leftMargin = px(48); nl.bottomMargin = px(48);
        root.addView(name, nl);
        setContentView(root);
    }

    private void play() {
        if (!surfaceReady) return;
        if (player != null) player.stop();
        final String cam = Cameras.LIST[index][0];
        name.setText(Cameras.LIST[index][1]);
        name.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideName);
        handler.postDelayed(hideName, 3500);
        status.setText("conectando…");
        status.setVisibility(View.VISIBLE);
        player = new RtspPlayer(Cameras.HOST, Cameras.PORT, cam, sv.getHolder().getSurface(), new RtspPlayer.Listener() {
            @Override public void onState(String s) {
                runOnUiThread(() -> {
                    status.setText(s.equals("carregando") ? "aguardando imagem…" : s);
                    status.setVisibility(s.equals("ao vivo") ? View.GONE : View.VISIBLE);
                });
            }
            @Override public void onSize(int w, int h) {}
        });
        player.start();
    }

    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_CHANNEL_UP) {
            index = (index + 1) % Cameras.LIST.length; play(); return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            index = (index + Cameras.LIST.length - 1) % Cameras.LIST.length; play(); return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_INFO) {
            name.setVisibility(View.VISIBLE); handler.removeCallbacks(hideName); handler.postDelayed(hideName, 3500); return true;
        }
        return super.onKeyDown(code, ev);
    }

    @Override public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; play(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        if (player != null) player.stop();
        player = null;
    }
}
