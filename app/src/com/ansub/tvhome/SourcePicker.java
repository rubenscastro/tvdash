package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.media.tv.TvInputInfo;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

// The Source menu, as designed in Figma (7:262): a glass panel listing the HDMI inputs, each with the
// name of the device on it (or a dash). The row in focus is the blue pill, and the input in use has a
// blue check. The last row opens the TV maker's own antenna app (handy for debugging).
final class SourcePicker {
    static final int W = 760, GAP = 24; // design px; GAP: distance from the Source button

    // Opens the menu aligned to the right edge of `anchor` (the Source button), 24 px above it or, when
    // that would be cut off, 24 px below it.
    static void show(final Activity a, View anchor) {
        final Dialog d = new Dialog(a);
        final View[] veil = new View[1];
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.menuPanel(a, Ui.fig(a, 56), 100.77, 0.30f, 0.38f));
        int pad = Math.max(1, Ui.fig(a, 1));
        box.setPadding(pad, pad, pad, pad);

        // the check marks the input being watched, only when this menu is opened over it (dock floating over the source)
        boolean overApp = DockActivity.current != null && !DockActivity.current.isExpanded();
        String active = overApp ? Items.viewingHdmi : null;
        View first = null;
        final java.util.List<View> rowViews = new java.util.ArrayList<>();
        int rows = 0;
        final View[] current = new View[1];
        for (final TvInputInfo in : Items.hdmiInputs(a)) {
            String device = Items.connectedDeviceName(a, in);
            // connected: name in full white, the device name and the plug icon in white; empty: name at 50% and the crossed-out plug at 20%
            View row = row(a, hdmiName(a, in), device, in.getId().equals(active), device != null ? 2 : 1,
                    () -> { Items.launch(a, "hdmi:" + in.getId()); fadeOut(d, veil[0]); });
            box.addView(row, new LinearLayout.LayoutParams(-1, Ui.fig(a, 111)));
            if (first == null) first = row;
            if (in.getId().equals(active)) current[0] = row;
            rowViews.add(row);
            rows++;
        }
        // only the TV's video inputs (the HDMI ports): no antenna / stock-TV row any more

        int width = Ui.fig(a, W);
        // measure the real height (rows are 32 + 47 + 32 design px) so the 24 px gap to the button is exact
        box.measure(View.MeasureSpec.makeMeasureSpec(Ui.fig(a, W), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = box.getMeasuredHeight();
        // The window covers the whole screen so a gradient can sit behind the panel (Figma: 1920 x 1092, black 0% -> 50% left to right).
        final android.widget.FrameLayout root = new android.widget.FrameLayout(a);
        final View veilView = new View(a);
        veil[0] = veilView;
        veilView.setBackground(veilDrawable());
        veilView.setAlpha(0f);
        root.addView(veilView, Ui.at(0, 0, Ui.px(a, 1920), Ui.px(a, 1092)));
        d.setContentView(root, new android.view.ViewGroup.LayoutParams(-1, -1));
        Window w = d.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        int screenW = a.getResources().getDisplayMetrics().widthPixels, screenH = a.getResources().getDisplayMetrics().heightPixels;
        int gap = Ui.fig(a, GAP);
        int right, top;
        if (anchor != null) {
            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);
            right = loc[0] + anchor.getWidth();
            if (loc[1] - gap - height >= 0) top = loc[1] - gap - height;        // above, if it fits
            else top = loc[1] + anchor.getHeight() + gap;                       // else below
            top = Math.max(0, Math.min(top, screenH - height));
        } else {
            right = screenW - Ui.fig(a, 42);
            top = screenH - height - Ui.fig(a, 40);
        }
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        w.setGravity(Gravity.TOP | Gravity.LEFT);
        w.setAttributes(lp);
        android.widget.FrameLayout.LayoutParams bp = new android.widget.FrameLayout.LayoutParams(width, -2, Gravity.TOP | Gravity.LEFT);
        bp.leftMargin = Math.max(0, Math.min(right - width, screenW - width));
        bp.topMargin = top;
        root.addView(box, bp);
        // Opens with NO row selected: the first Down selects the first row, the first Up the last one (then it navigates as usual).
        for (View rv : rowViews) rv.setFocusable(false);
        // left / right close the menu (up / down move through the rows)
        d.setOnKeyListener((dlg, code, ev) -> {
            if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN && (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || code == android.view.KeyEvent.KEYCODE_BACK)) { fadeOut(d, veil[0]); return true; }
            if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN && !rowViews.isEmpty() && dlg instanceof Dialog && ((Dialog) dlg).getCurrentFocus() == null
                    && (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN || code == android.view.KeyEvent.KEYCODE_DPAD_UP)) {
                for (View rv : rowViews) rv.setFocusable(true);
                (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? rowViews.get(0) : rowViews.get(rowViews.size() - 1)).requestFocus();
                return true;
            }
            return false;
        });
        d.show();
        if (current[0] != null) { for (View rv : rowViews) rv.setFocusable(true); current[0].requestFocus(); } // opened over a source: it starts selected
        Anim.run(240, new android.view.animation.DecelerateInterpolator(1.5f), t -> veilView.setAlpha(t), null); // fade in
    }

    private static boolean closing;

    // Fade the gradient and the panel out, then close the dialog.
    private static void fadeOut(final Dialog d, final View veil) {
        if (closing) return;
        closing = true;
        final View panel = ((android.view.ViewGroup) d.getWindow().getDecorView().findViewById(android.R.id.content)).getChildAt(0) instanceof android.view.ViewGroup
                ? ((android.view.ViewGroup) ((android.view.ViewGroup) d.getWindow().getDecorView().findViewById(android.R.id.content)).getChildAt(0)).getChildAt(1) : null;
        Anim.run(180, new android.view.animation.AccelerateInterpolator(), t -> {
            if (veil != null) veil.setAlpha(1f - t);
            if (panel != null) panel.setAlpha(1f - t);
        }, () -> { closing = false; try { d.dismiss(); } catch (Exception ignored) {} });
    }

    // Figma "Rectangle 31": 1920 x 1092, linear-gradient(90deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.5) 100%)
    private static android.graphics.drawable.Drawable veilDrawable() {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint p = new android.graphics.Paint();
            @Override public void draw(android.graphics.Canvas c) {
                android.graphics.Rect b = getBounds();
                p.setShader(new android.graphics.LinearGradient(b.left, 0, b.right, 0, new int[] {0x00000000, 0x80000000},
                        new float[] {0f, 1f}, android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(b, p);
            }
            @Override public void setAlpha(int a) {}
            @Override public void setColorFilter(android.graphics.ColorFilter f) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    // "HDMI 1": the name set in the TV's settings if any, else the port name.
    private static String hdmiName(Activity a, TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(a);
        if (custom != null && custom.length() > 0) return Items.fixPortName(custom.toString());
        CharSequence l = in.loadLabel(a);
        return l != null && l.length() > 0 ? l.toString() : "HDMI";
    }

    private static TextView text(Activity a, int figPx, int color) {
        TextView t = new TextView(a);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.fig(a, figPx));
        t.setTextColor(color);
        t.setTypeface(Ui.regular());
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    // icon: 0 none, 1 empty HDMI (crossed-out plug, 20%), 2 connected HDMI (plug next to the device name)
    private static View row(final Activity a, String left, String right, boolean active, final int icon, final Runnable click) {
        final boolean strong = icon == 2;
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.fig(a, 32), 0, Ui.fig(a, 32), 0);
        ImageView check = new ImageView(a);
        check.setImageBitmap(Channels.logo(a, "check")); // Figma check, 56 px box
        check.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        r.addView(check, new LinearLayout.LayoutParams(Ui.fig(a, 56), Ui.fig(a, 56)));
        final int idleName = strong ? Color.WHITE : 0x80FFFFFF; // 50% for an input with nothing connected
        final TextView l = text(a, 40, idleName);
        l.setText(left);
        l.setPadding(Ui.fig(a, 24), 0, Ui.fig(a, 16), 0);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView rt = text(a, 32, strong ? Color.WHITE : 0x80FFFFFF);
        rt.setGravity(Gravity.RIGHT);
        if (right != null) { rt.setText(right); r.addView(rt, new LinearLayout.LayoutParams(-2, -2)); }
        if (icon != 0) {
            ImageView plug = new ImageView(a);
            plug.setImageBitmap(Channels.logo(a, icon == 2 ? "hdmi_on" : "hdmi_off"));
            plug.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.fig(a, 48), Ui.fig(a, 48));
            if (icon == 2) pl.leftMargin = Ui.fig(a, 12);
            r.addView(plug, pl);
        }
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnClickListener(v -> click.run());
        final android.graphics.drawable.GradientDrawable idle = Ui.rounded(Color.TRANSPARENT, Ui.fig(a, 56));
        final android.graphics.drawable.GradientDrawable sel = Ui.rounded(0x1AFFFFFF, Ui.fig(a, 56));
        sel.setStroke(Ui.fig(a, 6), Color.WHITE);
        r.setBackground(idle);
        r.setOnFocusChangeListener((v, has) -> {
            r.setBackground(has ? sel : idle);
            l.setTextColor(has ? Color.WHITE : idleName);
            rt.setTextColor(has || strong ? Color.WHITE : 0x80FFFFFF);
        });
        return r;
    }
}
