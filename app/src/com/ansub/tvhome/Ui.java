package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

// Shared view builders for the home screen, the dock and the pickers. All sizes are px on a 1920-wide
// screen and scale to the real width.
final class Ui {
    static final int MARGIN = 53, GAP = 43;

    // Figma design px (the frames are 1940 wide) -> screen px
    static int fig(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().widthPixels / 1940f); }

    static int px(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().widthPixels / 1920f); }
    // housedash's font: Inter (variable, assets/fonts/Inter.ttf) at the weights the dash uses; falls back to the system font.
    private static final java.util.Map<Integer, Typeface> INTER = new java.util.HashMap<>();
    private static Typeface inter(int weight, Typeface fallback) {
        Typeface t = INTER.get(weight);
        if (t != null) return t;
        try {
            android.app.Application app = (android.app.Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            t = new Typeface.Builder(app.getAssets(), "fonts/Inter.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (Throwable e) { t = null; }
        if (t == null) t = fallback;
        INTER.put(weight, t);
        return t;
    }
    static Typeface regular() { return inter(400, Typeface.create("sans-serif", Typeface.NORMAL)); }
    static Typeface bold() { return inter(700, Typeface.create("sans-serif", Typeface.BOLD)); }
    static Typeface medium() { return inter(500, Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
    static Typeface black() { return inter(900, Typeface.create("sans-serif-black", Typeface.NORMAL)); }

    static TextView text(Context c, int sizePx, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, sizePx));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setSingleLine(true);
        return t;
    }

    static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    // A focusable card: rounded, clipped, with a white ring and a slight grow while focused.
    // ---- Tile design tokens (Figma 12:1029 normal / 12:1031 selected), in px at 1920 wide ----
    // The selected tile is the normal one scaled by SCALE, so every selected size below is the base size x SCALE.
    static final float TILE_W = 250, TILE_H = 150, TILE_RADIUS = 32;          // normal: 250 x 150, radius 32
    // Grow / shrink on selection over 160 ms (own driver: the system animation scale is 0). A newer call supersedes an older one.
    static void scaleTo(final View v, final float target) {
        final float from = v.getScaleX();
        final Object token = new Object();
        v.setTag(0x7f0f0001, token);
        if (from == target) return;
        Anim.run(160, new android.view.animation.DecelerateInterpolator(1.6f), t -> {
            if (v.getTag(0x7f0f0001) != token) return;
            float sc = from + (target - from) * t;
            v.setScaleX(sc);
            v.setScaleY(sc);
        }, null);
    }
    static final float SCALE = 1.10f;                                          // selected: 275 x 165, radius 35.2
    static final float RING = 5f / SCALE;                                      // white border: 5 at selected size
    static final float EDGE = 1f / SCALE;                                      // 10% black line just inside the ring: 1 at selected size
    static final int EDGE_COLOR = 0x1A000000;                                  // rgba(0,0,0,0.10)
    static final float TILE_PAD = 16, TILE_TITLE = 28;                         // text inside a tile: 28, padded 16
    static final float LABEL = 32f / SCALE, LABEL_GAP = 19f / SCALE, LABEL_GAP_SELECTED = 19f; // name under the selected tile: 32, 19 below it (grid design 12:814)
    static final float SHADOW_DY = 10, SHADOW_BLUR = 15; static final int SHADOW_COLOR = 0x26000000; // 0 10 15 at 15%

    // Grid / dock tiles: the tokens above.
    static FrameLayout card(Context c, int fill, int radius, Runnable click) {
        return tile(c, fill, click);
    }

    static FrameLayout card(Context c, int fill, int radius, final int ringColor, final int ringPx, final int darkPx,
                            final float focusScale, Runnable click) {
        return tile(c, fill, click); // kept for older callers: every tile uses the same tokens
    }

    static FrameLayout tile(final Context c, int fill, Runnable click) {
        final int radius = px(c, Math.round(TILE_RADIUS));
        FrameLayout f = new FrameLayout(c);
        f.setBackground(rounded(fill, radius));
        f.setClipToOutline(true);
        final boolean[] selected = {false};
        final float ring = RING * c.getResources().getDisplayMetrics().widthPixels / 1920f;
        final float edge = EDGE * c.getResources().getDisplayMetrics().widthPixels / 1920f;
        final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG), dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setStyle(Paint.Style.STROKE); white.setColor(Color.WHITE); white.setStrokeWidth(ring);
        dark.setStyle(Paint.Style.STROKE); dark.setColor(EDGE_COLOR); dark.setStrokeWidth(edge);
        f.setForeground(new Drawable() {
            @Override public void draw(Canvas cv) {
                if (!selected[0]) return;
                android.graphics.Rect b = getBounds();
                // the white ring on the outer edge, then the darker line just inside it
                cv.drawRoundRect(new android.graphics.RectF(b.left + ring / 2, b.top + ring / 2, b.right - ring / 2, b.bottom - ring / 2),
                        radius - ring / 2, radius - ring / 2, white);
                float o = ring + edge / 2;
                cv.drawRoundRect(new android.graphics.RectF(b.left + o, b.top + o, b.right - o, b.bottom - o),
                        Math.max(0, radius - o), Math.max(0, radius - o), dark);
            }
            @Override public void setAlpha(int a) {}
            @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        f.setFocusable(true);
        f.setClickable(true);
        if (click != null) f.setOnClickListener(v -> click.run());
        if (android.os.Build.VERSION.SDK_INT >= 28) { // drop shadow of the selected tile
            f.setOutlineAmbientShadowColor(SHADOW_COLOR);
            f.setOutlineSpotShadowColor(SHADOW_COLOR);
        }
        final float lift = px(c, Math.round(SHADOW_BLUR));
        f.setOnFocusChangeListener((v, has) -> {
            selected[0] = has;
            scaleTo(v, has ? SCALE : 1f);
            v.setTranslationZ(has ? lift : 0f);
            v.getForeground().invalidateSelf();
            v.invalidate();
        });
        return f;
    }

    // Icon buttons (Figma 12:1182 / 12:1186): 88 x 88, radius 48, glass; selected = x1.10 with a 5 px white ring.
    static FrameLayout card(Context c, int fill, int radius, final int ringColor, Runnable click) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(rounded(fill, radius));
        f.setClipToOutline(true);
        final GradientDrawable outer = new GradientDrawable();
        outer.setCornerRadius(radius);
        final int ringPx = px(c, 5);
        outer.setStroke(ringPx, Color.TRANSPARENT);
        f.setForeground(outer);
        f.setFocusable(true);
        f.setClickable(true);
        if (click != null) f.setOnClickListener(v -> click.run());
        f.setOnFocusChangeListener((v, has) -> {
            outer.setStroke(ringPx, has ? ringColor : Color.TRANSPARENT);
            scaleTo(v, has ? SCALE : 1f); // 88 -> 96.8, icon 40 -> 44, ring 5 -> 5.5, radius 48 -> 52.8
        });
        return f;
    }

    static final int BLUE = 0xFF0095FF;

    // The home cards (Figma 14:2478): white 12% -> 8% at 95.8deg.
    static Drawable cardPanel(final Context c, final int radius) {
        return new Drawable() {
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas cv) {
                android.graphics.Rect b = getBounds();
                int w = b.width(), h = b.height();
                double a = Math.toRadians(95.83), dx = Math.sin(a), dy = -Math.cos(a);
                double len = Math.abs(w * dx) + Math.abs(h * dy);
                float cx = b.left + w / 2f, cy = b.top + h / 2f;
                fill.setShader(new android.graphics.LinearGradient(cx - (float) (dx * len / 2), cy - (float) (dy * len / 2),
                        cx + (float) (dx * len / 2), cy + (float) (dy * len / 2), new int[] {0x1FFFFFFF, 0x1FFFFFFF, 0x14FFFFFF, 0x14FFFFFF},
                        new float[] {0f, 0.094f, 0.913f, 1f}, android.graphics.Shader.TileMode.CLAMP));
                cv.drawRoundRect(new android.graphics.RectF(b), radius, radius, fill);
                // housedash's `.card`: a 1 px white 10% border (keeps the glass visible on a light sky)
                Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
                line.setStyle(Paint.Style.STROKE);
                line.setColor(0x1AFFFFFF);
                line.setStrokeWidth(Math.max(1f, px(c, 1)));
                float h2 = line.getStrokeWidth() / 2f;
                cv.drawRoundRect(new android.graphics.RectF(b.left + h2, b.top + h2, b.right - h2, b.bottom - h2), radius, radius, line);
            }
            @Override public void setAlpha(int al) {}
            @Override public void setColorFilter(android.graphics.ColorFilter f) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    // The dock panel (grid design 12:814): white 12% -> 8% at 127.2deg, no border, radius 70.
    static Drawable dockPanel(final Context c, final int radius) { return dockPanel(c, radius, false); }

    // `dawn`: the housedash dawn glass (white 42% -> 30%), more opaque so it holds up over the bright morning sky
    static Drawable dockPanel(final Context c, final int radius, final boolean dawn) {
        final int[] cols = dawn ? new int[] {0x6BFFFFFF, 0x6BFFFFFF, 0x4DFFFFFF, 0x4DFFFFFF} : new int[] {0x1FFFFFFF, 0x1FFFFFFF, 0x14FFFFFF, 0x14FFFFFF};
        return new Drawable() {
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas cv) {
                android.graphics.Rect b = getBounds();
                int w = b.width(), h = b.height();
                double a = Math.toRadians(127.2), dx = Math.sin(a), dy = -Math.cos(a);
                double len = Math.abs(w * dx) + Math.abs(h * dy);
                float cx = b.left + w / 2f, cy = b.top + h / 2f;
                fill.setShader(new android.graphics.LinearGradient(cx - (float) (dx * len / 2), cy - (float) (dy * len / 2),
                        cx + (float) (dx * len / 2), cy + (float) (dy * len / 2), cols,
                        new float[] {0f, 0.094f, 0.913f, 1f}, android.graphics.Shader.TileMode.CLAMP));
                cv.drawRoundRect(new android.graphics.RectF(b), radius, radius, fill);
            }
            @Override public void setAlpha(int al) {}
            @Override public void setColorFilter(android.graphics.ColorFilter f) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    // The dock / menu panel: a white-to-grey glass gradient with a hairline border, at a CSS angle.
    static Drawable glassPanel(final Context c, final int radius, final double angleDeg) {
        return glassPanel(c, radius, angleDeg, 0x1FFFFFFF, true);
    }

    // The menu / list / banner panel (Figma 12:1449, 12:1651, 12:1661): white a -> grey b, no border.
    // `a`/`b` are the gradient's alphas (0.30/0.38 menus, 0.40/0.48 banner). The system's own blur is off on this
    // TV, so a dark base (about 40%) stays under it to keep the text readable over bright video.
    static Drawable menuPanel(Context c, int radius, double angleDeg, float a, float b) {
        return glassPanel(c, radius, angleDeg, 0x00000000, true, Math.round(a * 255) << 24 | 0xFFFFFF, Math.round(b * 255) << 24 | 0x999999, 0x66000000);
    }

    static Drawable glassPanel(final Context c, final int radius, final double angleDeg, final int borderColor, final boolean darkBase) {
        return glassPanel(c, radius, angleDeg, borderColor, darkBase, 0x33FFFFFF, 0x47999999, 0xD9000000);
    }

    static Drawable glassPanel(final Context c, final int radius, final double angleDeg, final int borderColor, final boolean darkBase,
                               final int from, final int to, final int baseColor) {
        return new Drawable() {
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
            { line.setStyle(Paint.Style.STROKE); line.setColor(borderColor); line.setStrokeWidth(Math.max(1, px(c, 1))); }
            @Override public void draw(Canvas cv) {
                android.graphics.Rect b = getBounds();
                int w = b.width(), h = b.height();
                double a = Math.toRadians(angleDeg), dx = Math.sin(a), dy = -Math.cos(a);
                double len = Math.abs(w * dx) + Math.abs(h * dy);
                float cx = b.left + w / 2f, cy = b.top + h / 2f;
                fill.setShader(new android.graphics.LinearGradient(cx - (float) (dx * len / 2), cy - (float) (dy * len / 2),
                        cx + (float) (dx * len / 2), cy + (float) (dy * len / 2), from, to,
                        android.graphics.Shader.TileMode.CLAMP));
                android.graphics.RectF r = new android.graphics.RectF(b.left + 0.5f, b.top + 0.5f, b.right - 0.5f, b.bottom - 0.5f);
                // a dark base first: the design sits on black, and the app behind must not show through the text
                base.setColor(baseColor);
                if (darkBase) cv.drawRoundRect(r, radius, radius, base);
                cv.drawRoundRect(r, radius, radius, fill);
                if ((borderColor >>> 24) != 0) cv.drawRoundRect(r, radius, radius, line);
            }
            @Override public void setAlpha(int al) {}
            @Override public void setColorFilter(android.graphics.ColorFilter f) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private static Bitmap asset(Context c, String name) {
        try (java.io.InputStream in = c.getAssets().open("logos/" + name + ".png")) { return android.graphics.BitmapFactory.decodeStream(in); }
        catch (Exception e) { return null; }
    }

    private static void centred(Canvas cv, Bitmap b, float w, float h, float bw, float bh) { // bw x bh design px at TILE_W wide
        if (b == null) return;
        float k = w / TILE_W;
        cv.drawBitmap(b, null, new android.graphics.RectF(w / 2f - bw * k / 2, h / 2f - bh * k / 2, w / 2f + bw * k / 2, h / 2f + bh * k / 2),
                new Paint(Paint.FILTER_BITMAP_FLAG));
    }

    // The TV tile (Figma 12:1941): five colour bands under a soft dark fade, the "TV" mark in the middle,
    // on a dark gradient.
    static Drawable tvPicture(Context c, int w, int h) {
        Bitmap bmp = ((BitmapDrawable) gradientPicture(c, "#434343", "#121212", 176.71, 6.732, 98.024, w, h)).getBitmap();
        Canvas cv = new Canvas(bmp);
        int[] bands = {0xFFFE255F, 0xFFFFE059, 0xFF29E088, 0xFF5CCEFF, 0xFF229BFF};
        Paint p = new Paint();
        for (int i = 0; i < bands.length; i++) { p.setColor(bands[i]); cv.drawRect(w * i / 5f, 0, w * (i + 1) / 5f, h, p); }
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, h, 0x00000000, 0x4D000000, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        centred(cv, asset(c, "tvguide"), w, h + 1f * w / TILE_W, 118, 65);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    // The Cameras tile: 174.53deg #B90334 -> #7C0021 with the Cerberus mark (about 120 wide).
    static Drawable camerasPicture(Context c, int w, int h) {
        Bitmap bmp = ((BitmapDrawable) gradientPicture(c, "#B90334", "#7C0021", 174.53, 6.73, 98.02, w, h)).getBitmap();
        Bitmap logo = asset(c, "cerberus");
        if (logo != null) centred(new Canvas(bmp), logo, w, h, 120, 120f * logo.getHeight() / logo.getWidth());
        return new BitmapDrawable(c.getResources(), bmp);
    }

    // The Source tile: 180deg #343434 -> #181818 with the connectors picture at the bottom centre, as large as fits
    // 120 design px tall (the size that looked right).
    static Drawable sourcePicture(Context c, int w, int h) {
        Bitmap bmp = ((BitmapDrawable) gradientPicture(c, "#343434", "#181818", 180, 0, 100.04, w, h)).getBitmap();
        Bitmap art = asset(c, "source"); // the whole tile illustration (Figma 21:817): the port, its frame and the two faded neighbours
        if (art != null) new Canvas(bmp).drawBitmap(art, null, new android.graphics.RectF(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        return new BitmapDrawable(c.getResources(), bmp);
    }

    // The Play Store tile (Figma 12:1965): white -> #DBDBDB, with the Google Play mark (98.1 x 109.4).
    static Drawable playStorePicture(Context c, int w, int h) {
        Bitmap bmp = ((BitmapDrawable) gradientPicture(c, "#FFFFFF", "#DBDBDB", 180, 0, 100.04, w, h)).getBitmap();
        centred(new Canvas(bmp), asset(c, "playstore"), w, h, 98.133f, 109.364f);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    // The PlayStation tile picture: its gradient with the logo in the middle.
    static Drawable ps5Picture(Context c, int w, int h) {
        Bitmap bmp = ((BitmapDrawable) gradientPicture(c, "#0173F6", "#023A93", 180, 0, 100.04, w, h)).getBitmap();
        Canvas cv = new Canvas(bmp);
        Drawable logo = Icons.drawable(Icons.PS5, 202, 44, true);
        int lw = w * 3 / 5, lh = h / 3;
        logo.setBounds((w - lw) / 2, (h - lh) / 2, (w + lw) / 2, (h + lh) / 2);
        logo.draw(cv);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    // Icon over a title, with an optional smaller line under it.
    static View iconLabel(Context c, int[] icon, String title, String sub) {
        return iconLabel(c, icon == null ? null : new LineIcon(icon), 46, title, sub);
    }

    static View iconLabel(Context c, Drawable icon, int iconPx, String title, String sub) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            box.addView(iv, new LinearLayout.LayoutParams(px(c, iconPx), px(c, iconPx)));
        }
        TextView t = text(c, Math.round(TILE_TITLE), Color.WHITE, regular());
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setText(title);
        t.setGravity(Gravity.CENTER);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(px(c, Math.round(TILE_PAD)), 0, px(c, Math.round(TILE_PAD)), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = px(c, 10);
        box.addView(t, lp);
        if (sub != null && sub.length() > 0) {
            TextView s = text(c, 19, 0x99FFFFFF, regular());
            s.setText(sub);
            s.setGravity(Gravity.CENTER);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPadding(px(c, 12), 0, px(c, 12), 0);
            box.addView(s, new LinearLayout.LayoutParams(-2, -2));
        }
        return box;
    }

    // Scales a drawable to cover w x h (cropping the overflow), as a ready bitmap.
    static Drawable cover(Context c, Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(c.getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    // linear-gradient(angle, from p0%, to p1%) at w x h
    static Drawable gradientPicture(Context c, String from, String to, double angleDeg, double p0, double p1, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        double a = Math.toRadians(angleDeg), dx = Math.sin(a), dy = -Math.cos(a);
        double len = Math.abs(w * dx) + Math.abs(h * dy);
        float cx = w / 2f, cy = h / 2f;
        int f0 = Color.parseColor(from), f1 = Color.parseColor(to);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(cx - (float) (dx * len / 2), cy - (float) (dy * len / 2),
                cx + (float) (dx * len / 2), cy + (float) (dy * len / 2), new int[] {f0, f0, f1, f1},
                new float[] {0f, (float) (p0 / 100), (float) (p1 / 100), 1f}, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    // A CSS-style linear gradient (angle in degrees, like linear-gradient(106.76deg, a, b)) as a w x h picture.
    static Drawable gradientPicture(Context c, String from, String to, double angleDeg, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        double a = Math.toRadians(angleDeg), dx = Math.sin(a), dy = -Math.cos(a);
        double len = Math.abs(w * dx) + Math.abs(h * dy);
        float cx = w / 2f, cy = h / 2f;
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(cx - (float) (dx * len / 2), cy - (float) (dy * len / 2),
                cx + (float) (dx * len / 2), cy + (float) (dy * len / 2),
                Color.parseColor(from), Color.parseColor(to), android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        return new BitmapDrawable(c.getResources(), bmp);
    }

    // The channel tile picture (Figma 12:1855..12:1880): its gradient, with the 103 x 102 logo centred in the 250 x 150 tile.
    static Drawable channelPicture(Context c, String[] ch, int w, int h) {
        Bitmap bmp = ((BitmapDrawable) gradientPicture(c, ch[3], ch[4], Double.parseDouble(ch[5]),
                Double.parseDouble(ch[6]), Double.parseDouble(ch[7]), w, h)).getBitmap();
        Canvas cv = new Canvas(bmp);
        Bitmap logo = Channels.logo(c, ch[2]);
        if (logo != null) {
            float k = w / TILE_W;
            float lw = 103.125f * k, lh = 102.438f * k;
            float s = Math.min(lw / logo.getWidth(), lh / logo.getHeight());
            float dw = logo.getWidth() * s, dh = logo.getHeight() * s;
            cv.drawBitmap(logo, null, new android.graphics.RectF(w / 2f - dw / 2, h / 2f - dh / 2, w / 2f + dw / 2, h / 2f + dh / 2),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        return new BitmapDrawable(c.getResources(), bmp);
    }

    static ImageView picture(Context c, Drawable d) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        iv.setImageDrawable(d);
        return iv;
    }

    static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
