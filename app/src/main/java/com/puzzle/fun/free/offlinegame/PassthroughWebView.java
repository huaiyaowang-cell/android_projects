package com.puzzle.fun.free.offlinegame;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Forwards touch events to lower {@link WebView}s (same coordinates), then handles normally.
 */
public class PassthroughWebView extends WebView {

    private final List<WebView> passthroughTargets = new ArrayList<>();
    private boolean passthroughTouchesEnabled = true;

    public PassthroughWebView(Context context) {
        super(context);
    }

    public PassthroughWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PassthroughWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setPassthroughTarget(WebView target) {
        passthroughTargets.clear();
        if (target != null) {
            passthroughTargets.add(target);
        }
    }

    public void setPassthroughTargets(Collection<WebView> targets) {
        passthroughTargets.clear();
        if (targets == null) {
            return;
        }
        for (WebView target : targets) {
            if (target != null) {
                passthroughTargets.add(target);
            }
        }
    }

    public void setPassthroughTouchesEnabled(boolean enabled) {
        this.passthroughTouchesEnabled = enabled;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (passthroughTouchesEnabled && !passthroughTargets.isEmpty()) {
            for (WebView target : passthroughTargets) {
                if (target == null || target.getVisibility() != View.VISIBLE) {
                    continue;
                }
                MotionEvent copy = MotionEvent.obtain(ev);
                try {
                    target.dispatchTouchEvent(copy);
                } finally {
                    copy.recycle();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
