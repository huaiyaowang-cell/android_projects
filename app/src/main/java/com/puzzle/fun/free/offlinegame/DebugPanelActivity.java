package com.puzzle.fun.free.offlinegame;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.greenrobot.eventbus.EventBus;

/**
 * Full-screen debug controls for the stacked WebViews in {@link MainActivity}.
 */
public class DebugPanelActivity extends AppCompatActivity {

    private SharedPreferences prefs() {
        return getSharedPreferences(DebugDualWebViewPrefs.PREFS_NAME, MODE_PRIVATE);
    }

    private String readBgUrl() {
        return prefs().getString(DebugDualWebViewPrefs.KEY_BG_WEB_URL, DebugDualWebViewPrefs.DEFAULT_BG_WEB_URL);
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

        Button btnShowBg = new Button(this);
        btnShowBg.setText("BG layer ON");
        btnShowBg.setAllCaps(false);
        btnShowBg.setOnClickListener(v -> EventBus.getDefault().post(new DebugWebViewEvents.BgLayerShow()));

        Button btnHideBg = new Button(this);
        btnHideBg.setText("BG layer OFF");
        btnHideBg.setAllCaps(false);
        btnHideBg.setOnClickListener(v -> EventBus.getDefault().post(new DebugWebViewEvents.BgLayerHide()));

        Button btnUrl = new Button(this);
        btnUrl.setText("BG URL…");
        btnUrl.setAllCaps(false);
        btnUrl.setOnClickListener(v -> promptBgUrl());

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

        root.addView(btnShowBg);
        root.addView(btnHideBg);
        root.addView(btnUrl);
        root.addView(btnBgInterstitial);
        root.addView(alphaLabel);
        root.addView(alphaSeek);
        root.addView(btnBack);
    }

    private void promptBgUrl() {
        final EditText input = new EditText(this);
        input.setText(readBgUrl());
        input.setHint("https://...");
        new AlertDialog.Builder(this)
                .setTitle("Background WebView URL")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String url = input.getText() != null ? input.getText().toString().trim() : "";
                    if (url.isEmpty()) {
                        url = DebugDualWebViewPrefs.DEFAULT_BG_WEB_URL;
                    }
                    prefs().edit().putString(DebugDualWebViewPrefs.KEY_BG_WEB_URL, url).apply();
                    EventBus.getDefault().post(new DebugWebViewEvents.BgUrlChanged());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
