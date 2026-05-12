package com.puzzle.fun.free.offlinegame;

/**
 * Shared preferences keys for dual-WebView debug (main + debug activity).
 */
public final class DebugDualWebViewPrefs {

    private DebugDualWebViewPrefs() {
    }

    public static final String PREFS_NAME = "dual_webview_debug";
    public static final String KEY_BG_WEB_URL = "bg_web_url";
    /** 0–100, default 100 (opaque). */
    public static final String KEY_TOP_ALPHA_PERCENT = "top_alpha_percent";

    public static final String DEFAULT_BG_WEB_URL =
            "https://rabigame.fun/r_game/__game_center_back__/index.html";
}
