package com.ansub.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

// The row of five shortcuts (dock over an app, and on the home screen), as designed in Figma:
// a glass panel with five tiles, a blue ring on the selected one, no captions.
final class Dock {
    // design px on a 1920 x 1080 screen (the Figma frame is 1940 x 1100)
    static final int W = 1820, H = 230, PAD_X = 40, PAD_Y = 40, TILE_W = 250, TILE_H = 150, TILE_GAP = 48; // 6 tiles line up with the grid's columns
    static final int X = (1920 - W) / 2;

    static FrameLayout build(final Activity a, boolean unusedDark, final Runnable afterLaunch) {
        FrameLayout box = new FrameLayout(a);
        box.setBackground(Ui.dockPanel(a, Ui.px(a, 70)));
        for (int i = 0; i < Items.DOCK.length; i++) {
            final String key = Items.DOCK[i];
            final FrameLayout[] self = new FrameLayout[1];
            FrameLayout t = Ui.tile(a, 0x33000000, () -> {
                if (key.equals("source")) { SourcePicker.show(a, self[0]); return; } // opens next to this tile
                Items.launch(a, key);
                if (afterLaunch != null) afterLaunch.run();
            });
            self[0] = t;
            fill(a, t, key);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(a, TILE_W), Ui.px(a, TILE_H));
            lp.leftMargin = Ui.px(a, PAD_X + i * (TILE_W + TILE_GAP));
            lp.topMargin = Ui.px(a, PAD_Y);
            box.addView(t, lp);
        }
        return box;
    }

    private static void fill(Activity a, FrameLayout t, String key) {
        int w = Ui.px(a, TILE_W), h = Ui.px(a, TILE_H);
        if (key.equals("tvaberta")) { // the TV tile: colour bands with the TV mark
            t.addView(Ui.picture(a, Ui.tvPicture(a, w, h)), new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        if (key.equals("source")) {
            t.addView(Ui.picture(a, Ui.sourcePicture(a, w, h)), new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        if (key.equals("cameras")) { // the Cerberus mark on its gradient
            t.addView(Ui.picture(a, Ui.camerasPicture(a, w, h)), new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        String[] grad = Items.gradient(key);
        if (grad != null) { // TV aberta, Cameras, Source: gradient with a white icon, no caption
            boolean vertical = key.equals("cameras") || key.equals("source"); // 180deg, 0% to 100.04%
            t.addView(Ui.picture(a, vertical ? Ui.gradientPicture(a, grad[0], grad[1], 180, 0, 100.04, w, h)
                    : Ui.gradientPicture(a, grad[0], grad[1], 118.16, w, h)), new FrameLayout.LayoutParams(-1, -1));
            ImageView iv = new ImageView(a);
            iv.setImageDrawable(Items.styledIcon(key));
            t.addView(iv, new FrameLayout.LayoutParams(Ui.px(a, 64), Ui.px(a, 64), Gravity.CENTER));
            return;
        }
        if (key.equals("ps5")) { // the console: its gradient with the logo
            t.addView(Ui.picture(a, Ui.ps5Picture(a, w, h)), new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        if (key.startsWith("ch:")) { // a channel: its gradient with the logo
            for (String[] c : Channels.HOME)
                if (key.equals("ch:" + c[1])) {
                    t.addView(Ui.picture(a, Ui.channelPicture(a, c, w, h)), new FrameLayout.LayoutParams(-1, -1));
                    return;
                }
        }
        String pkg = key.startsWith("app:") ? key.substring(4) : null;
        Drawable picture = pkg == null ? null : Items.banner(a, pkg, w, h);
        if (picture != null) {
            t.addView(Ui.picture(a, picture), new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView n = Ui.text(a, Math.round(Ui.TILE_TITLE), Color.WHITE, Ui.regular());
            n.setText(pkg == null ? key : Items.appLabel(a, pkg));
            n.setGravity(Gravity.CENTER);
            n.setSingleLine(false);
            n.setMaxLines(2);
            n.setPadding(Ui.px(a, Math.round(Ui.TILE_PAD)), 0, Ui.px(a, Math.round(Ui.TILE_PAD)), 0);
            t.addView(n, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        }
    }

    // What the dock shows right now, so the home screen can tell when it must redraw it.
    static String signature(Context c) { return "fixed"; } // the dock no longer changes

    static android.view.View first(FrameLayout box) { return box.getChildAt(0); }
}
