package com.ansub.tvhome;

import android.view.Choreographer;
import android.view.animation.Interpolator;

// A tiny animation driver on the display clock. The system's animation scale is switched off on this
// TV (to make it snappy), which also freezes ViewPropertyAnimator; these animations must still play.
final class Anim {
    interface Step { void apply(float t); } // t goes 0 -> 1, already eased

    static void run(final long durationMs, final Interpolator ease, final Step step, final Runnable end) {
        final long[] start = {0};
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override public void doFrame(long frameTimeNanos) {
                if (start[0] == 0) start[0] = frameTimeNanos;
                float raw = (frameTimeNanos - start[0]) / (durationMs * 1_000_000f);
                if (raw >= 1f) {
                    step.apply(1f);
                    if (end != null) end.run();
                } else {
                    step.apply(ease.getInterpolation(Math.max(0f, raw)));
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        });
    }
}
