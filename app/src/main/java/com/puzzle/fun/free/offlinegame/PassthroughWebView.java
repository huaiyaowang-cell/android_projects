package com.puzzle.fun.free.offlinegame;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Forwards touch events to lower {@link WebView}s (same coordinates), then handles normally.
 * Events are only forwarded when the target's current URL host is {@code rabigame.fun} (including subdomains).
 */
public class PassthroughWebView extends WebView {

    private static final String ALLOWED_PASSTHROUGH_ROOT_HOST = "rabigame.fun";

    private final List<WebView> passthroughTargets = new ArrayList<>();
    private boolean passthroughTouchesEnabled = false;

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

    /** Only {@code http(s)://*.rabigame.fun/...} URLs (including subdomains). */
    public static boolean isRabigameFunHttpUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase();
        return ALLOWED_PASSTHROUGH_ROOT_HOST.equals(host)
                || host.endsWith("." + ALLOWED_PASSTHROUGH_ROOT_HOST);
    }

    /** Only {@code http(s)://*.rabigame.fun/...} targets receive passthrough touches. */
    static boolean isPassthroughAllowedForTarget(WebView target) {
        if (target == null) {
            return false;
        }
        return isRabigameFunHttpUrl(target.getUrl());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (passthroughTouchesEnabled && !passthroughTargets.isEmpty()) {
            for (WebView target : passthroughTargets) {
                if (target == null || target.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (!isPassthroughAllowedForTarget(target)) {
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
