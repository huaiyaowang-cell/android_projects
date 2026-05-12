package com.puzzle.fun.free.offlinegame;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

/**
 * Forwards touch events to another {@link WebView} below (same coordinates), then handles normally.
 */
public class PassthroughWebView extends WebView {

    private WebView passthroughTarget;
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
        this.passthroughTarget = target;
    }

    public void setPassthroughTouchesEnabled(boolean enabled) {
        this.passthroughTouchesEnabled = enabled;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (passthroughTouchesEnabled && passthroughTarget != null
                && passthroughTarget.getVisibility() == View.VISIBLE) {
            MotionEvent copy = MotionEvent.obtain(ev);
            try {
                passthroughTarget.dispatchTouchEvent(copy);
            } finally {
                copy.recycle();
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
