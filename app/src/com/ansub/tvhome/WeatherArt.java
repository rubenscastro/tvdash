package com.ansub.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

// Small weather illustrations for the weather card (sun, moon, clouds, rain, storm, snow, fog), drawn in a
// 100 x 100 box. Same vocabulary as housedash's WeatherIcon: a warm sun, a pale moon, soft grey-blue clouds.
final class WeatherArt extends Drawable {
    static final int CLEAR = 0, CLOUDY = 1, FOG = 2, RAIN = 3, SNOW = 4, STORM = 5;
    private final int type;
    private final boolean night;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    WeatherArt(int type, boolean night) { this.type = type; this.night = night; }

    static int typeOf(int wmo) {
        if (wmo == 0) return CLEAR;
        if (wmo <= 3) return CLOUDY;
        if (wmo == 45 || wmo == 48) return FOG;
        if (wmo >= 51 && wmo <= 67 || wmo >= 80 && wmo <= 82) return RAIN;
        if (wmo >= 71 && wmo <= 77 || wmo == 85 || wmo == 86) return SNOW;
        if (wmo >= 95) return STORM;
        return CLOUDY;
    }

    @Override public void draw(Canvas c) {
        android.graphics.Rect b = getBounds();
        c.save();
        c.translate(b.left, b.top);
        c.scale(b.width() / 100f, b.height() / 100f);
        if (type == CLEAR) { if (night) moon(c, 50, 50, 26); else sun(c, 50, 50, 22); }
        else if (type == CLOUDY) {
            if (night) moon(c, 62, 34, 17); else sun(c, 64, 34, 15);
            cloud(c, 44, 60, 1f, 0xFFE4EAF7);
        } else {
            cloud(c, 50, 42, 1f, type == STORM ? 0xFF9AA6C2 : 0xFFD5DDF0);
            if (type == RAIN) for (int i = 0; i < 3; i++) drop(c, 34 + i * 16, 70 + (i % 2) * 6);
            if (type == SNOW) for (int i = 0; i < 3; i++) flake(c, 34 + i * 16, 74 + (i % 2) * 6);
            if (type == STORM) bolt(c);
            if (type == FOG) { p.setColor(0xCCDDE5F5); p.setStrokeWidth(5); p.setStrokeCap(Paint.Cap.ROUND); for (int i = 0; i < 3; i++) c.drawLine(24 + i * 4, 70 + i * 9, 76 - i * 4, 70 + i * 9, p); }
        }
        c.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, r * 2.2f, new int[] {0x66FFB020, 0x00FFB020}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 2.2f, p);
        p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f, new int[] {0xFFFFE27A, 0xFFFFA928}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        p.setColor(0xFFFFC14A);
        p.setStrokeWidth(r * 0.2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            c.drawLine(cx + (float) Math.cos(a) * r * 1.35f, cy + (float) Math.sin(a) * r * 1.35f, cx + (float) Math.cos(a) * r * 1.75f, cy + (float) Math.sin(a) * r * 1.75f, p);
        }
    }

    private void moon(Canvas c, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, r * 2f, new int[] {0x33DCE6FF, 0x00DCE6FF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 2f, p);
        p.setShader(null);
        p.setColor(0xFFE9EEFA);
        Path moon = new Path();
        moon.addCircle(cx, cy, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx + r * 0.55f, cy - r * 0.25f, r * 0.9f, Path.Direction.CW);
        moon.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(moon, p);
    }

    private void cloud(Canvas c, float cx, float cy, float s, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(color);
        c.drawCircle(cx - 10 * s, cy + 2 * s, 14 * s, p);
        c.drawCircle(cx + 8 * s, cy - 6 * s, 18 * s, p);
        c.drawCircle(cx + 24 * s, cy + 4 * s, 12 * s, p);
        c.drawRoundRect(cx - 24 * s, cy + 2 * s, cx + 36 * s, cy + 18 * s, 8 * s, 8 * s, p);
    }

    private void drop(Canvas c, float x, float y) {
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFF6EB7FF);
        p.setStrokeWidth(5);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x, y, x - 4, y + 11, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void flake(Canvas c, float x, float y) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 3.4f, p);
    }

    private void bolt(Canvas c) {
        Path b = new Path();
        b.moveTo(52, 60); b.lineTo(40, 78); b.lineTo(49, 78); b.lineTo(44, 94); b.lineTo(62, 72); b.lineTo(52, 72); b.lineTo(58, 60); b.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFD54A);
        c.drawPath(b, p);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(android.graphics.ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
