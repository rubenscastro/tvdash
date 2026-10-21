package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

// The page background of the home screen:
//   linear-gradient(0deg, rgba(0,0,0,0.2), rgba(0,0,0,0.2)),   (blend: plus-darker)
//   radial-gradient(46.67% 55.58% at 32.08% 89.81%, rgba(5,116,241,0.5) 0%, rgba(5,116,241,0) 100%),
//   radial-gradient(59.14% 80% at 80.42% 13.15%, #0574F1 0%, rgba(20,36,72,0.2) 100%),
//   linear-gradient(108.16deg, #0574F1 0%, #1B315B 97.33%)
// Painted once at screen size.
final class Look {
    // A radial gradient shaped as an ellipse of (rx x ry) screen fractions, centred at (x, y) screen fractions,
    // from `from` at the centre to `to` at the rim, and `to` beyond.
    private static void ellipse(Canvas cv, Paint p, int w, int h, float rx, float ry, float x, float y, int from, int to) {
        p.setShader(new RadialGradient(0, 0, 1, from, to, Shader.TileMode.CLAMP));
        cv.save();
        cv.translate(w * x, h * y);
        cv.scale(w * rx, h * ry);
        cv.drawRect(-8, -8, 8, 8, p); // clamped beyond the rim, so this covers the screen
        cv.restore();
    }

    static Drawable background(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.DITHER_FLAG);

        // base: linear 108.16deg
        double a = Math.toRadians(108.16), dx = Math.sin(a), dy = -Math.cos(a);
        double len = Math.abs(w * dx) + Math.abs(h * dy);
        float cx = w / 2f, cy = h / 2f;
        p.setShader(new LinearGradient(cx - (float) (dx * len / 2), cy - (float) (dy * len / 2),
                cx + (float) (dx * len / 2), cy + (float) (dy * len / 2),
                new int[] {0xFF0574F1, 0xFF0574F1, 0xFF1B315B, 0xFF1B315B}, new float[] {0f, 0f, 0.9733f, 1f},
                Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);

        // then two ellipses on top (CSS "w% h% at x% y%"): the first listed in the CSS is the topmost
        ellipse(cv, p, w, h, 0.5914f, 0.80f, 0.8042f, 0.1315f, 0xFF0574F1, 0x33142448);
        ellipse(cv, p, w, h, 0.4667f, 0.5558f, 0.3208f, 0.8981f, 0x800574F1, 0x000574F1);
        // topmost: 20% black over everything (the design blends it as "plus-darker"; drawn here as a plain 20% black)
        p.setShader(null);
        p.setColor(0x33000000);
        cv.drawRect(0, 0, w, h, p);
        return new BitmapDrawable(c.getResources(), bmp);
    }
}
