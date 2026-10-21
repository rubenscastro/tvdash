package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

// What the Home button opens. It is the invisible root of the home task (singleTask), so every press of
// Home reaches it, and it picks the screen:
//   an app is in front   -> the dock, floating over it
//   anything else        -> the full home screen (also pressing Home while the dock is up)
public class HomeEntry extends Activity {
    static HomeEntry current;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = this;
        decide();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        decide();
    }

    @Override protected void onDestroy() { super.onDestroy(); if (current == this) current = null; }

    private void decide() {
        if (getIntent().hasExtra("sim")) { HouseCards.setSim(getIntent().getStringExtra("sim")); getIntent().removeExtra("sim"); }
        if (DockActivity.current != null && !DockActivity.current.isExpanded()) { // dock already up: Home again opens the full home screen
            startActivity(new Intent(this, DockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); // back to front, for focus
            DockActivity.current.expand(false);
            return;
        }
        if (!Items.appInFront && DockActivity.current != null && DockActivity.current.isExpanded()) {
            // already on the home screen (the expanded dock + HomeOverApp): make sure that task is in front
            ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).moveTaskToFront(DockActivity.current.getTaskId(), 0);
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        if (Items.appInFront) {
            // never leave a home screen under the dock: what shows through must be the app
            if (HomeActivity.instance != null) HomeActivity.instance.finish();
            // a dock that became the (opaque) home is spent: a fresh, translucent one goes over the app
            if (DockActivity.current != null && DockActivity.current.isOpaque()) DockActivity.current.finish();
            startActivity(new Intent(this, DockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        else startActivity(new Intent(this, HomeActivity.class));
        overridePendingTransition(0, 0);
    }
}
