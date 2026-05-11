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

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
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
        LinearLayout topToggleRow = createToggleRow(alphaSeek);

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
        root.addView(topToggleRow);
        root.addView(bgAlphaTitle);
        root.addView(bgAlphaContainer);
        root.addView(btnBack);

        fetchConfigAndRenderLayerAlphas();
    }

    private void fetchConfigAndRenderLayerAlphas() {
        networkExecutor.execute(() -> {
            int apiCount = 0;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(DebugDualWebViewPrefs.GAME_CONFIG_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.connect();
                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                if (code >= 200 && code < 300) {
                    InputStream stream = conn.getInputStream();
                    if (stream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                    }
                }
                List<String> urls = DebugDualWebViewPrefs.parsePassthroughUrlsFromGameConfigJson(sb.toString());
                apiCount = urls.size();
            } catch (Exception ignored) {
                apiCount = 0;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            final int layerCount = apiCount;
            mainHandler.post(() -> renderBgLayerAlphaControls(Math.max(0, layerCount)));
        });
    }

    private void renderBgLayerAlphaControls(int layerCount) {
        bgAlphaContainer.removeAllViews();
        for (int i = 0; i < layerCount; i++) {
            final int layerIndex = i;
            int initial = prefs().getInt(
                    DebugDualWebViewPrefs.bgLayerAlphaKey(layerIndex),
                    DebugDualWebViewPrefs.defaultBgLayerAlphaPercent(layerIndex)
            );
            TextView label = new TextView(this);
            label.setTextColor(Color.WHITE);
            String layerName = "Overlay #" + (layerIndex + 1) + " (index " + layerIndex + ")";
            label.setText(layerName + " alpha: " + initial + "%");

            SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(100);
            seekBar.setProgress(DebugDualWebViewPrefs.clampPercent(initial));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int clamped = DebugDualWebViewPrefs.clampPercent(progress);
                    label.setText(layerName + " alpha: " + clamped + "%");
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
            LinearLayout toggleRow = createToggleRow(seekBar);
            bgAlphaContainer.addView(label);
            bgAlphaContainer.addView(seekBar);
            bgAlphaContainer.addView(toggleRow);
        }
    }

    private LinearLayout createToggleRow(SeekBar targetSeekBar) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button btnZero = new Button(this);
        btnZero.setAllCaps(false);
        btnZero.setText("0%");
        btnZero.setOnClickListener(v -> targetSeekBar.setProgress(0));

        Button btnFull = new Button(this);
        btnFull.setAllCaps(false);
        btnFull.setText("100%");
        btnFull.setOnClickListener(v -> targetSeekBar.setProgress(100));

        LinearLayout.LayoutParams childLp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        row.addView(btnZero, childLp);
        row.addView(btnFull, childLp);
        return row;
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
