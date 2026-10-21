package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

// The dock: the home screen pushed down so only the dock row shows (with the first row of tiles
// peeking under it), floating over whatever app is in front. It slides up when Home is pressed.
// Down (or Home again) slides everything up into the full home screen, with the app right under the
// selected dock tile selected; Up and Back slide the dock away.
public class DockActivity extends Activity {
    static DockActivity current;

    private HomeLayout layout;
    private boolean expanding, closing, expanded;

    // True once it has turned into the home screen: it then just waits, hidden under the home screen, until
    // the dock is needed again (closing it would flash the app behind for one frame).
    boolean isExpanded() { return expanded; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = this;
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        show();
    }

    // Starts (or restarts) the dock: slides in from below the screen to the dock position, in 0.55 s.
    private void show() {
        expanding = closing = expanded = false;
        layout = HomeLayout.build(this);
        layout.bg.setAlpha(0f);
        layout.header.setAlpha(0f);
        layout.cards.setAlpha(0f);
        layout.setLampsAlpha(0f);
        layout.house.setAlpha(0f);
        layout.veil.setAlpha(0f);
        layout.shade.setAlpha(0f);
        layout.dockView.setTranslationY(Ui.px(this, HomeLayout.DOCK_EXTRA));
        layout.setGridFocusable(false);
        setContentView(layout.root);
        layout.focusDock(0);
        final float from = getResources().getDisplayMetrics().heightPixels, to = shift();
        layout.content.setTranslationY(from);
        Anim.run(550, new DecelerateInterpolator(2.2f), t -> {
            layout.content.setTranslationY(from + (to - from) * t);
            layout.shade.setAlpha(Math.min(1f, t * 1.6f));
        }, null);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (expanded && !opaque && !i.getBooleanExtra("keep", false)) show(); // it was parked under the home screen: bring the dock up again
    }

    private float shift() { return Ui.px(this, HomeLayout.DOCK_SHIFT); }

    @Override protected void onStart() { super.onStart(); HouseCards.visible(this, 1); }

    @Override protected void onResume() { super.onResume(); Items.appInFront = false; }

    // Up / Down / Back are the dock's own: take them before the scroll view inside it can swallow them.
    @Override public boolean dispatchKeyEvent(KeyEvent ev) {
        if (RemoteKeys.handle(this, ev)) return true;
        int code = ev.getKeyCode();
        if (expanded) {                                  // the home screen takes the keys
            if (ev.getAction() == KeyEvent.ACTION_DOWN) HouseCards.calm();
            return super.dispatchKeyEvent(ev);
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0 && !expanding && !closing && !expanded) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) expand(false); else close(); // Down opens the home but keeps the dock tile selected
            }
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    // Slide the whole thing up into the home screen: content up, background and header fade in. With
    // `toGrid` the app tile under the selected dock tile is selected at once. Then the real home screen
    // (which looks exactly the same) opens on top.
    void expand(boolean toGrid) {
        if (expanding || closing || expanded) return;
        expanding = true;
        HouseCards.enter();
        final int dockIndex = layout.focusedDockIndex();
        layout.setGridFocusable(true);
        final int gridIndex = layout.gridIndexUnder(dockIndex); // the grid tile closest under the selected dock tile
        if (toGrid) layout.focusGrid(gridIndex);
        final float from = layout.content.getTranslationY(), headerDy = -Ui.px(this, 24), extra = layout.dockView.getTranslationY();
        Anim.run(460, new DecelerateInterpolator(2f), t -> {
            layout.content.setTranslationY(from * (1f - t));
            layout.dockView.setTranslationY(extra * (1f - t)); // the dock and the grid come to their 68 px home spacing
            layout.shade.setAlpha(1f - t);
            layout.bg.setAlpha(t);
            layout.header.setAlpha(t);
            layout.cards.setAlpha(t);
            layout.setLampsAlpha(t);
            layout.house.setAlpha(t);
            layout.veil.setAlpha(t);
            layout.header.setTranslationY(headerDy * (1f - t));
        }, () -> {
            Items.appInFront = false;
            expanded = true;
            // the opaque home screen opens in this task, on top of the dock (which stays visible until it has
            // drawn, so the app underneath never shows through), and stops the app below
            Intent home = new Intent(this, HomeOverApp.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (toGrid) home.putExtra("grid_focus", gridIndex); else home.putExtra("dock_focus", dockIndex);
            // Best: the dock window itself becomes the opaque home (no second window, nothing to hand over: no blink, and the
            // app below is stopped because this window is no longer translucent).
            if (convertOpaque()) {
                opaque = true;
                layout.setGridFocusable(true);
                if (!toGrid) layout.focusDock(dockIndex);
                return;
            }
            // Fallback (the platform refused the conversion): open the home as a second window, as before.
            startActivity(home);
            overridePendingTransition(0, 0);
        });
    }

    private boolean opaque;
    boolean isOpaque() { return opaque; }

    // Activity.convertFromTranslucent() is hidden API (on the unsupported/greylist list: reachable on this Android).
    private boolean convertOpaque() {
        try {
            java.lang.reflect.Method m = Activity.class.getDeclaredMethod("convertFromTranslucent");
            m.setAccessible(true);
            m.invoke(this);
            return true;
        } catch (Throwable t) {
            android.util.Log.w("Dock", "convertFromTranslucent failed: " + t);
            return false;
        }
    }

    // Back from the home that lives in the dock window: close it and let the app that was open show again.
    @Override public void onBackPressed() {
        if (opaque) {
            Items.appInFront = true;
            finishAffinity();
            overridePendingTransition(0, 0);
            if (HomeEntry.current != null) HomeEntry.current.moveTaskToBack(true);
        } else super.onBackPressed();
    }


    // Up or Back: slide the dock away and give the screen back to the app.
    private void close() {
        closing = true;
        Items.appInFront = true;
        final float from = layout.content.getTranslationY(), to = getResources().getDisplayMetrics().heightPixels;
        Anim.run(230, new AccelerateInterpolator(), t -> {
            layout.content.setTranslationY(from + (to - from) * t);
            layout.shade.setAlpha(1f - t);
        }, () -> {
            finish();
            overridePendingTransition(0, 0);
            // the invisible home root must not keep the screen: send the home task back too
            if (HomeEntry.current != null) HomeEntry.current.moveTaskToBack(true);
        });
    }

    // Another app took the screen (a shortcut was chosen) while the dock was up: it is done.
    @Override protected void onStop() { super.onStop(); HouseCards.visible(this, -1); if (!expanding && !expanded) finish(); }

    @Override protected void onDestroy() { super.onDestroy(); HouseView.release(this); HouseCards.release(this); if (current == this) current = null; }
}
