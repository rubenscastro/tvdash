package com.ansub.tvhome;

// The home screen reached from the dock (dock -> Down). It is an opaque screen in the dock's own task, so the
// app that was open underneath is stopped while it is up. Back closes it and the dock below it, and the app
// that was open before shows again.
public class HomeOverApp extends HomeActivity {
    @Override public void onBackPressed() {
        Items.appInFront = true;
        finishAffinity();
        overridePendingTransition(0, 0);
        // the invisible home root must not keep the screen
        if (HomeEntry.current != null) HomeEntry.current.moveTaskToBack(true);
    }
}
