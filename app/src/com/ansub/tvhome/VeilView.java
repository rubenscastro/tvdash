package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;

// housedash's contrast veil between the 3D scene and the cards: black from 0% (left) to 55% (right edge), with the top and bottom
// faded out by a mask (transparent 0 -> opaque 30% -> opaque 70% -> transparent 100%). Not used at dawn, when the cards turn dark-on-light.
final class VeilView extends View {
    private Bitmap cache;

    VeilView(Context c) {
        super(c);
        setFocusable(false);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cache = null;
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (cache == null) {
            cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(cache);
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, w, 0, 0x00000000, 0x8C000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            Paint m = new Paint();
            m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            m.setShader(new LinearGradient(0, 0, 0, h, new int[] {0x00000000, 0xFF000000, 0xFF000000, 0x00000000}, new float[] {0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, m);
        }
        cv.drawBitmap(cache, 0, 0, null);
    }
}
