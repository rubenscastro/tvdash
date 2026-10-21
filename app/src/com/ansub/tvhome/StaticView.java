package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

// The "chiado" of an analog TV between channels: grey noise that flickers (a few pre-made frames played at ~24 fps, stretched
// to the screen), faint scanlines and a slow bright band rolling down. Shown while the tuner has no picture.
final class StaticView extends View {
    static final boolean BARS = true; // colour bars with a wobbling scanline distortion (false: grey noise)
    private static final int FW = 768, FH = 432, FRAMES = 6;
    private final Bitmap[] frames = new Bitmap[FRAMES];
    private Bitmap[] variants;
    private float t;
    private final Paint noise = new Paint(Paint.FILTER_BITMAP_FLAG), lines = new Paint(), band = new Paint();
    private final Rect dst = new Rect();
    private boolean running;
    private long last;
    private int frame;
    private float bandY, barY = -0.5f, barX = 0.3f;
    private final Paint bar = new Paint();
    private final Random rnd = new Random();

    StaticView(Context c) {
        super(c);
        Random r = new Random(7);
        for (int f = 0; f < FRAMES; f++) {
            int[] px = new int[FW * FH];
            for (int i = 0; i < px.length; i++) {
                int g = (int) Math.min(255, Math.max(0, 128 + r.nextGaussian() * 85)); // contrasty grey grain
                px[i] = 0xFF000000 | (g << 16) | (g << 8) | g;
            }
            frames[f] = Bitmap.createBitmap(px, FW, FH, Bitmap.Config.ARGB_8888);
        }
        Bitmap sl = Bitmap.createBitmap(1, 4, Bitmap.Config.ARGB_8888);
        sl.setPixel(0, 0, 0x00000000); sl.setPixel(0, 1, 0x00000000); sl.setPixel(0, 2, 0x99000000); sl.setPixel(0, 3, 0x55000000);
        lines.setShader(new BitmapShader(sl, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        setVisibility(GONE);
    }

    void start() {
        setAlpha(1f);
        setVisibility(VISIBLE);
        if (running) return;
        running = true;
        t = 4.0f;           // the distorted band starts about 100 px below the top edge (its centre sweeps from (t * 0.12) % 1.5 - 0.25)
        bandY = 100f / 1080f;
        postOnAnimation(tick);
    }

    // fade out and stop
    void stop() {
        if (!running && getVisibility() != VISIBLE) return;
        running = false;
        animate().alpha(0f).setDuration(220).withEndAction(() -> setVisibility(GONE)).start();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.nanoTime();
            if (now - last > 40_000_000L) { last = now; frame = (frame + 1 + (int) (now % 2)) % FRAMES; bandY += 0.011f; if (bandY > 1.2f) bandY = -0.3f; invalidate(); }
            postOnAnimation(this);
        }
    };

    // The TV logo's colours, as bars (twice the width, so the distorted rows can wrap around)
    private static final int[] BAR = {0xFFE91E5A, 0xFFF2C94C, 0xFF2ED47A, 0xFF4DB8F5, 0xFF1F7FE0, 0xFFA855F7};
    private static int mix(int a, int b, float k) {
        int r = (int) (Color.red(a) * (1 - k) + Color.red(b) * k), g = (int) (Color.green(a) * (1 - k) + Color.green(b) * k), bl = (int) (Color.blue(a) * (1 - k) + Color.blue(b) * k);
        return Color.rgb(r, g, bl);
    }

    private void drawBars(Canvas cv, int w, int h) {
        if (variants == null || variants[0].getWidth() != w * 2) {
            variants = new Bitmap[3];
            float bw = w / (float) BAR.length;
            for (int v = 0; v < 3; v++) {
                variants[v] = Bitmap.createBitmap(w * 2, 8, Bitmap.Config.ARGB_8888);
                Canvas bc = new Canvas(variants[v]);
                Paint p = new Paint();
                for (int rep = 0; rep < 2; rep++) for (int i = 0; i < BAR.length; i++) {
                    int col = BAR[v == 2 ? (i + 2) % BAR.length : i];                       // 2: the bars shifted by two places
                    if (v == 1) col = mix(col, 0xFF1F6670, 0.6f);                           // 1: dark teal, washed out
                    if (v == 2) col = mix(col, 0xFFFFFFFF, 0.12f);
                    p.setColor(col);
                    bc.drawRect(rep * w + i * bw, 0, rep * w + (i + 1) * bw, 8, p);
                }
            }
        }
        t += 0.045f;
        final int strip = Math.max(3, h / 180);
        Rect src = new Rect(), to = new Rect();
        for (int y = 0; y < h; y += strip) {
            float f = y / (float) h;
            // gentle slow waves over ~75% of the screen; a horizontal band (~25% tall) that drifts down gets the strong distortion
            float c = (t * 0.12f) % 1.5f - 0.25f, d = Math.abs(f - c);
            float m = 1f - Math.min(1f, Math.max(0f, (d - 0.09f) / 0.05f));   // 1 inside the band, soft edges
            m = m * m * (3f - 2f * m);
            float dx = (float) (Math.sin(f * 7 + t * 1.4) * 0.03 + Math.sin(f * 17 - t * 2.0) * 0.016 + Math.sin(f * 41 + t * 4.0) * 0.006) * w
                    + m * (float) (Math.sin(f * 45 + t * 8) * 0.05 + Math.sin(f * 110 - t * 13) * 0.025 + Math.sin(f * 20 + t * 3) * 0.04) * w;
            int off = Math.floorMod(Math.round(dx), w);
            src.set(off, 0, off + w, 8);
            to.set(0, y, w, Math.min(h, y + strip));
            int vi = m > 0.5f ? ((int) (f * 16 + t * 2.5f)) % 3 : 0;     // patchwork of tones inside the band
            cv.drawBitmap(variants[Math.abs(vi)], src, to, null);
        }
        // film of noise on top, and heavy scanlines
        noise.setAlpha(60);
        dst.set(0, 0, w, h);
        cv.drawBitmap(frames[frame], null, dst, noise);
        noise.setAlpha(255);
        cv.drawRect(0, 0, w, h, lines);
        cv.drawRect(0, 0, w, h, lines);
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (BARS) { drawBars(cv, w, h); return; }
        dst.set(0, 0, w, h);
        cv.drawBitmap(frames[frame], null, dst, noise);
        cv.drawRect(0, 0, w, h, lines);
        float y = bandY * h, bh = h * 0.26f;
        band.setColor(0x14FFFFFF); // solid, hard edges
        cv.drawRect(0, y, w, y + bh, band);
    }
}
