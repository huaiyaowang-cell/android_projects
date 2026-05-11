package com.puzzle.fun.free.offlinegame;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared preferences keys for dual-WebView debug (main + debug activity).
 */
public final class DebugDualWebViewPrefs {

    private DebugDualWebViewPrefs() {
    }

    public static final String PREFS_NAME = "dual_webview_debug";
    /** 0–100, default 100 (opaque). Key bumped so installs pick up 100% default again. */
    public static final String KEY_TOP_ALPHA_PERCENT = "game_webview_alpha_percent";
    public static final String KEY_BG_LAYER_ALPHA_PREFIX = "bg_layer_alpha_";
    public static final String GAME_CONFIG_URL =
            "https://api.rabigame.fun/api/v1/passthrough/game-config?channel=happy-glass";

    public static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static String bgLayerAlphaKey(int index) {
        return KEY_BG_LAYER_ALPHA_PREFIX + index;
    }

    /** First URL (top among passthrough) opaque; deeper URLs slightly transparent for debugging. */
    public static int defaultBgLayerAlphaPercent(int index) {
        return index == 0 ? 100 : 60;
    }

    /**
     * Parses {@code data.config.passthroughUrls} from game-config JSON.
     * If {@code passthroughUrls} is a JSON string of an array, that form is also accepted.
     */
    public static List<String> parsePassthroughUrlsFromGameConfigJson(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return result;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            JSONObject config = data == null ? null : data.optJSONObject("config");
            JSONArray urls = config == null ? null : config.optJSONArray("passthroughUrls");
            if (urls == null && config != null) {
                String raw = config.optString("passthroughUrls", "");
                if (!raw.isEmpty()) {
                    try {
                        urls = new JSONArray(raw);
                    } catch (JSONException ignored) {
                    }
                }
            }
            if (urls != null) {
                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i, "").trim();
                    if (!url.isEmpty()) {
                        result.add(url);
                    }
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }
}
