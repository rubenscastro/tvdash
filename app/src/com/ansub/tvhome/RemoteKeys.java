package com.ansub.tvhome;

import android.app.Activity;
import android.view.KeyEvent;

// The remote's app buttons (TCL scan codes, found with the remote: TV 4001, Netflix 4062, Prime Video 4099, YouTube BUTTON_3,
// Globoplay 4097, Disney+ 4141). Every activity of ours that can be in front hands its key events here.
final class RemoteKeys {
    private RemoteKeys() {}

    static String targetFor(int code) {
        switch (code) {
            case 4001: return "tvaberta";                         // the TV button: our own antenna app
            case 4062: return "app:com.netflix.ninja";
            case 4099: return "app:com.amazon.amazonvideo.livingroom";
            case KeyEvent.KEYCODE_BUTTON_3: return "app:com.google.android.youtube.tv";
            case 4097: return "app:com.globo.globotv";
            case 4141: return "app:com.disney.disneyplus";
            default: return null;
        }
    }

    // true when the key was one of the app buttons (and has been handled)
    static boolean handle(Activity a, KeyEvent ev) {
        String key = targetFor(ev.getKeyCode());
        if (key == null) return false;
        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) Items.launch(a, key);
        return true;
    }
}
