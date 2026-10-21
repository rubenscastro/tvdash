package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

// A lamp icon of the home, drawn natively from the live state (so a toggle shows at once, without waiting for the server to
// draw the house again): housedash's BulbIcon (on = warm bulb with rays, off = pale and faded) plus the white focus ring.
final class LampView extends View {
    // lucide-like bulb on a 24 grid: the SVG path "M12 2a7 7 0 0 1 4 12.74V17a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1v-2.26A7 7 0 0 1 12 2z" built by hand
    private static final Path BULB = new Path();
    static {
        RectF globe = new RectF(5f, 2f, 19f, 16f);
        BULB.moveTo(12f, 2f);
        BULB.arcTo(globe, -90f, 145f);
        BULB.lineTo(16f, 17f);
        BULB.quadTo(16f, 18f, 15f, 18f);
        BULB.lineTo(9f, 18f);
        BULB.quadTo(8f, 18f, 8f, 17f);
        BULB.lineTo(8f, 14.74f);
        BULB.arcTo(globe, 125f, 145f);
        BULB.close();
    }
    private final String name;
    private final float iconSize;             // design px
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    LampView(Context c, String name, float iconSize) {
        super(c);
        this.name = name;
        this.iconSize = iconSize;
        setFocusable(true);
        setClickable(true);
    }

    @Override protected void onDraw(Canvas cv) {
        boolean on = HouseCards.lampOn(name);
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float size = w / 1.9f;                 // the box is 1.9x the icon
        if (isFocused()) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x14FFFFFF);
            cv.drawCircle(cx, cy, w / 2f - 3f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3f, w * 0.06f));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy, w / 2f - 3f, p);
        }
        if (on) { // soft halo
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy, size * 0.9f, new int[] {0x66FFD060, 0x00FFD060}, null, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, size * 0.9f, p);
            p.setShader(null);
        }
        cv.save();
        cv.translate(cx - size / 2f, cy - size / 2f);
        cv.scale(size / 24f, size / 24f);
        int alpha = on ? 255 : 102; // 0.4 when off
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);                      // dark outline: same contrast on bright and dark floors
        p.setStrokeWidth(1.6f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(on ? 0x8C000000 : 0x8CFFFFFF); cv.drawPath(BULB, p); // same outline as when lit, white when off
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? 0xFFFFE4A0 : 0xFFFFFFFF);
        p.setAlpha(on ? 255 : alpha);
        cv.drawPath(BULB, p);
        p.setColor(on ? 0xFFFFD060 : 0xFFFFFFFF);
        p.setAlpha(on ? 255 : (int) (alpha * 0.6f));
        cv.drawRoundRect(new RectF(9f, 18f, 15f, 19.5f), 0.5f, 0.5f, p);
        p.setColor(on ? 0xFFFFC030 : 0xFFFFFFFF);
        p.setAlpha(on ? 255 : (int) (alpha * 0.4f));
        cv.drawRoundRect(new RectF(10f, 19.5f, 14f, 21f), 0.5f, 0.5f, p);
        if (on) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFFFFE4A0);
            float[][] rays = {{12, 0.5f, 12, 1.5f}, {4.2f, 3.2f, 5, 4}, {19.8f, 3.2f, 19, 4}, {2, 10, 3, 10}, {22, 10, 21, 10}};
            for (float[] r : rays) cv.drawLine(r[0], r[1], r[2], r[3], p);
        }
        cv.restore();
    }

    @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        invalidate();
    }
}
