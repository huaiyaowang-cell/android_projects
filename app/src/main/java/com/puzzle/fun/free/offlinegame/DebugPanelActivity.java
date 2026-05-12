package com.puzzle.fun.free.offlinegame;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen debug controls for the stacked WebViews in {@link MainActivity}.
 */
public class DebugPanelActivity extends AppCompatActivity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private LinearLayout bgAlphaContainer;

    private SharedPreferences prefs() {
        return getSharedPreferences(DebugDualWebViewPrefs.PREFS_NAME, MODE_PRIVATE);
    }

    private int readTopAlphaPercent() {
        return prefs().getInt(DebugDualWebViewPrefs.KEY_TOP_ALPHA_PERCENT, 100);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFF222222);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setText("Dual WebView DEBUG");
        root.addView(title);

        Button btnBgInterstitial = new Button(this);
        btnBgInterstitial.setText("BG Interstitial (adBreak)");
        btnBgInterstitial.setAllCaps(false);
        btnBgInterstitial.setOnClickListener(v -> EventBus.getDefault().post(new DebugWebViewEvents.BgInterstitial()));

        TextView alphaLabel = new TextView(this);
        alphaLabel.setTextColor(Color.WHITE);
        int startAlpha = readTopAlphaPercent();
        alphaLabel.setText("Top WebView alpha: " + startAlpha + "%");

        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(100);
        alphaSeek.setProgress(startAlpha);
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alphaLabel.setText("Top WebView alpha: " + progress + "%");
                prefs().edit().putInt(DebugDualWebViewPrefs.KEY_TOP_ALPHA_PERCENT, progress).apply();
                EventBus.getDefault().post(new DebugWebViewEvents.TopAlphaPercent(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        Button btnBack = new Button(this);
        btnBack.setText("Back");
        btnBack.setAllCaps(false);
        btnBack.setOnClickListener(v -> finish());

        TextView bgAlphaTitle = new TextView(this);
        bgAlphaTitle.setTextColor(Color.WHITE);
        bgAlphaTitle.setText("Background layers alpha:");

        bgAlphaContainer = new LinearLayout(this);
        bgAlphaContainer.setOrientation(LinearLayout.VERTICAL);

        root.addView(btnBgInterstitial);
        root.addView(alphaLabel);
        root.addView(alphaSeek);
        root.addView(bgAlphaTitle);
        root.addView(bgAlphaContainer);
        root.addView(btnBack);

        fetchConfigAndRenderLayerAlphas();
    }

    private void fetchConfigAndRenderLayerAlphas() {
        networkExecutor.execute(() -> {
            int count = 1;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(DebugDualWebViewPrefs.GAME_CONFIG_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.connect();
                InputStream stream = conn.getResponseCode() >= 200 && conn.getResponseCode() < 300
                        ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                if (stream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                }
                JSONObject root = new JSONObject(sb.toString());
                JSONObject data = root.optJSONObject("data");
                JSONObject config = data == null ? null : data.optJSONObject("config");
                JSONArray urls = config == null ? null : config.optJSONArray("passthroughUrls");
                if (urls != null && urls.length() > 0) {
                    count = urls.length();
                }
            } catch (Exception ignored) {
                count = 1;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            int finalCount = count;
            mainHandler.post(() -> renderBgLayerAlphaControls(finalCount));
        });
    }

    private void renderBgLayerAlphaControls(int layerCount) {
        bgAlphaContainer.removeAllViews();
        for (int i = 0; i < layerCount; i++) {
            final int layerIndex = i;
            int initial = prefs().getInt(DebugDualWebViewPrefs.bgLayerAlphaKey(layerIndex), 100);
            TextView label = new TextView(this);
            label.setTextColor(Color.WHITE);
            label.setText("Layer " + layerIndex + " alpha: " + initial + "%");

            SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(100);
            seekBar.setProgress(DebugDualWebViewPrefs.clampPercent(initial));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int clamped = DebugDualWebViewPrefs.clampPercent(progress);
                    label.setText("Layer " + layerIndex + " alpha: " + clamped + "%");
                    prefs().edit().putInt(DebugDualWebViewPrefs.bgLayerAlphaKey(layerIndex), clamped).apply();
                    EventBus.getDefault().post(new DebugWebViewEvents.BgLayerAlphaPercent(layerIndex, clamped));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            bgAlphaContainer.addView(label);
            bgAlphaContainer.addView(seekBar);
        }
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
