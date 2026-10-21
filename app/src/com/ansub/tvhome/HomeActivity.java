package com.ansub.tvhome;

import android.app.Activity;
import android.os.Bundle;

// The home screen. Opened by the dock's expand animation (which hands over with the focused dock tile)
// or straight from the Home button when no app is in front.
public class HomeActivity extends Activity {
    static HomeActivity instance;
    static boolean everShown;
    static long pausedAt;

    private HomeLayout layout;

    @Override protected void onStart() { super.onStart(); HouseCards.visible(this, 1); }
    @Override protected void onStop() { super.onStop(); HouseCards.visible(this, -1); }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        if (RemoteKeys.handle(this, ev)) return true;
        if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN) HouseCards.calm();
        return super.dispatchKeyEvent(ev);
    }


    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        everShown = true;
        instance = this;
        getWindow().setBackgroundDrawable(Look.background(this));
        layout = HomeLayout.build(this);
        setContentView(layout.root);
        layout.root.postDelayed(HouseCards::enter, 350);
        applyFocus(getIntent());
    }

    // From the dock: the app tile right under the dock tile that was selected (grid_focus), or the dock tile itself.
    private void applyFocus(android.content.Intent i) {
        int g = i.getIntExtra("grid_focus", -1);
        if (g >= 0) layout.focusGrid(g); else layout.focusDock(i.getIntExtra("dock_focus", 0));
    }

    @Override protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        applyFocus(i);
    }

    @Override protected void onResume() {
        super.onResume();
        Items.appInFront = false;
        layout.refreshNetwork();
        if (layout.refreshDock()) layout.focusDock(0);
        if (getCurrentFocus() == null) layout.focusDock(0);
    }

    @Override protected void onPause() { super.onPause(); pausedAt = System.currentTimeMillis(); }

    @Override protected void onDestroy() { super.onDestroy(); HouseView.release(this); HouseCards.release(this); if (instance == this) instance = null; }

    @Override public void onBackPressed() { /* home screen: nothing to go back to */ }
}
