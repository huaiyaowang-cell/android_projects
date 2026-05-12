package com.puzzle.fun.free.offlinegame;

/**
 * Shared preferences keys for dual-WebView debug (main + debug activity).
 */
public final class DebugDualWebViewPrefs {

    private DebugDualWebViewPrefs() {
    }

    public static final String PREFS_NAME = "dual_webview_debug";
    /** 0–100, default 100 (opaque). */
    public static final String KEY_TOP_ALPHA_PERCENT = "top_alpha_percent";
    public static final String KEY_BG_LAYER_ALPHA_PREFIX = "bg_layer_alpha_";
    public static final String GAME_CONFIG_URL =
            "https://api.rabigame.fun/api/v1/passthrough/game-config?channel=happy-glass";

    public static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static String bgLayerAlphaKey(int index) {
        return KEY_BG_LAYER_ALPHA_PREFIX + index;
    }
}
