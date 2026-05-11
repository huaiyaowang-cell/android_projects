package com.puzzle.fun.free.offlinegame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import com.bidderdesk.ad.ADManager;
import com.bidderdesk.ad.AdHelper;
import com.bidderdesk.ad.IAdListener;
import com.bidderdesk.ad.event.AdSdkInitComplete;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SDK-AD";

    private static final String WEB_GAME_URL = "https://appassets.androidplatform.net/assets/webgame/index.html";
    private static final String ALLOWED_PREFIX = "https://appassets.androidplatform.net/assets/webgame/";
    
    /**
     * Placement ids preloaded after BidderDesk SDK init (aligned with {@code UnityHelper.LoadAD}).
     */
    private static final String[] BIDDER_DESK_PLACEMENTS = new String[]{
            "reward_01", "interstitial_01",
            "banner_01",
            "open"
    };
    private static final String PLACEMENT_REWARDED = "reward_01";
    private static final String PLACEMENT_INTERSTITIAL = "interstitial_01";
    private static final String PLACEMENT_BANNER = "banner_01";
    private static final String PLACEMENT_OPEN = "open";
    private static final int OPEN_AD_MAX_RETRIES = 3;
    private static final long OPEN_AD_RETRY_DELAY_MS = 2000L;
    private static final long OPEN_AD_FIRST_TRY_DELAY_MS = 2000L;
    /** Toggle auto open-ad flow on app launch. */
    private static final boolean ENABLE_OPEN_AD_AUTO_FLOW = true;
    /** Toggle a manual test button to trigger open ad. */
    private static final boolean ENABLE_OPEN_AD_TEST_BUTTON = false;
    /** DEBUG: separate screen via {@link DebugPanelActivity}. */
    private static final boolean ENABLE_DUAL_WEBVIEW_DEBUG = BuildConfig.DEBUG;
    private static final String PREFS_DEBUG = DebugDualWebViewPrefs.PREFS_NAME;
    private static final String KEY_TOP_ALPHA_PERCENT = DebugDualWebViewPrefs.KEY_TOP_ALPHA_PERCENT;
    private static final String GAME_CONFIG_URL = DebugDualWebViewPrefs.GAME_CONFIG_URL;
    private static final String FALLBACK_BG_URL = "https://rabigame.fun/r_game/__game_center_back__/index.html";

    /**
     * Injected into the background WebView to mute DOM audio/video and new elements.
     * WebView has no native global mute; cross-origin iframes cannot be forced from here.
     */
    private static final String BG_MUTE_INJECT_JS =
            "(function(){'use strict';"
                    + "function m(el){try{el.muted=true;el.volume=0;el.setAttribute('muted','muted');}catch(e){}}"
                    + "function scan(){document.querySelectorAll('video,audio').forEach(m);}"
                    + "scan();"
                    + "if(!window.__bgMuteObs){window.__bgMuteObs=new MutationObserver(scan);"
                    + "window.__bgMuteObs.observe(document.documentElement,{childList:true,subtree:true});}"
                    + "if(!window.__bgMutePlay){window.__bgMutePlay=true;"
                    + "var p=HTMLMediaElement.prototype.play;"
                    + "HTMLMediaElement.prototype.play=function(){m(this);return p.apply(this,arguments)};}"
                    + "})();";

    private static final String TEST_BANNER_ID = "ca-app-pub-2915030877224461/9728916209";
    private static final String TEST_INTERSTITIAL_ID = "ca-app-pub-2915030877224461/5570997584";
    private static final String TEST_REWARDED_ID = "ca-app-pub-2915030877224461/4285017834";
    /** Interstitial frequency cap in seconds (0 = no cap). */
    private static final int INTERSTITIAL_MIN_INTERVAL_SECONDS = 30;
    /** Toggle AdMob test ad fallback (TEST_* ids) in code. */
    private static final boolean ENABLE_ADMOB_TEST_FALLBACK = false;

    private FrameLayout rootLayout;
    /** Bottom layers: remote URLs configured from passthrough API. */
    private final List<WebView> backgroundWebViews = new ArrayList<>();
    private final List<String> backgroundLayerUrls = new ArrayList<>();
    private FrameLayout backgroundLayersContainer;
    /** Top layer: local asset game; touches duplicated to top-most lower layer when visible. */
    private PassthroughWebView gameWebView;
    private WebViewAssetLoader assetLoader;
    private Button openAdTestButton;

    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private AdView bannerView;
    private long lastInterstitialShownAtMs;

    /** When true, fullscreen ads from H5 use {@link AdHelper}; banner still uses Web-driven AdMob unless you rely on BidderDesk banner only. */
    private volatile boolean bidderDeskAdsReady;
    /** Guard to show native splash(open) ad only once on cold start. */
    private boolean hasTriedOpenAdOnLaunch;
    private int openAdRetryCount;
    private final Runnable openAdRetryTask = () -> maybeShowNativeOpenAd("retry");
    private final Runnable openAdFirstTryTask = () -> maybeShowNativeOpenAd("delayed_first_try");
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterFullscreen();
        setupRoot();
        setupWebView();
        setupDebugEntryButton();
        setupOpenAdTestButton();
        if (ENABLE_ADMOB_TEST_FALLBACK) {
            preloadInterstitial();
            preloadRewarded();
        } else {
            preloadOpenAd();
            preloadInterstitial();
            preloadRewarded();
        }
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ENABLE_OPEN_AD_AUTO_FLOW) {
            rootLayout.removeCallbacks(openAdFirstTryTask);
            rootLayout.postDelayed(openAdFirstTryTask, OPEN_AD_FIRST_TRY_DELAY_MS);
        }
    }

    private void enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), decorView);
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }

    private void setupRoot() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);
        setContentView(rootLayout);
    }

    private void setupWebView() {
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        backgroundLayersContainer = new FrameLayout(this);
        backgroundLayersContainer.setVisibility(View.GONE);
        rootLayout.addView(backgroundLayersContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        gameWebView = new PassthroughWebView(this);
        WebSettings settings = gameWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        gameWebView.setWebChromeClient(new WebChromeClient());
        gameWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return null;
                }
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });
        gameWebView.addJavascriptInterface(new JsBridge(), "AndroidBridge");

        rootLayout.addView(gameWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        gameWebView.loadUrl(WEB_GAME_URL);
        applyTopWebViewAlphaFromPrefs();
        fetchBackgroundConfigAndRebuild();
    }

    private WebView createBackgroundLayerWebView() {
        WebView webView = new WebView(this);
        WebSettings bgSettings = webView.getSettings();
        bgSettings.setJavaScriptEnabled(true);
        bgSettings.setDomStorageEnabled(true);
        bgSettings.setAllowFileAccess(true);
        bgSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        bgSettings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new BackgroundJsBridge(), "NativeDebugBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectBackgroundWebMuteScript(view);
            }
        });
        return webView;
    }

    private void injectBackgroundWebMuteScript(WebView webView) {
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript(BG_MUTE_INJECT_JS, null);
    }

    private void destroyBackgroundLayers() {
        for (WebView webView : backgroundWebViews) {
            webView.removeJavascriptInterface("NativeDebugBridge");
            webView.destroy();
        }
        backgroundWebViews.clear();
        if (backgroundLayersContainer != null) {
            backgroundLayersContainer.removeAllViews();
        }
    }

    private SharedPreferences debugPrefs() {
        return getSharedPreferences(PREFS_DEBUG, MODE_PRIVATE);
    }

    private List<String> readPassthroughUrls(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        JSONObject config = data == null ? null : data.optJSONObject("config");
        JSONArray urls = config == null ? null : config.optJSONArray("passthroughUrls");
        List<String> result = new ArrayList<>();
        if (urls != null) {
            for (int i = 0; i < urls.length(); i++) {
                String url = urls.optString(i, "").trim();
                if (!url.isEmpty()) {
                    result.add(url);
                }
            }
        }
        if (result.isEmpty()) {
            result.add(FALLBACK_BG_URL);
        }
        return result;
    }

    private void setupDebugEntryButton() {
        if (!ENABLE_DUAL_WEBVIEW_DEBUG) {
            return;
        }
        Button debugBtn = new Button(this);
        debugBtn.setText("DEBUG");
        debugBtn.setAllCaps(false);
        debugBtn.setAlpha(0.9f);
        debugBtn.setOnClickListener(v -> startActivity(new Intent(this, DebugPanelActivity.class)));
        float density = getResources().getDisplayMetrics().density;
        int margin = (int) (12 * density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        lp.topMargin = (int) (48 * density);
        lp.rightMargin = margin;
        rootLayout.addView(debugBtn, lp);
    }

    private void triggerBackgroundInterstitial() {
        WebView topLayer = getTopMostBackgroundWebView();
        if (topLayer == null) {
            return;
        }
        String js = "(function(){"
                + "if(typeof window.adBreak!=='function'){console.warn('adBreak not available');return;}"
                + "window.adBreak({"
                + "type:'browse',"
                + "name:'game-center-back-commercial',"
                + "beforeAd:function(){},"
                + "afterAd:function(){if(window.NativeDebugBridge&&window.NativeDebugBridge.onBackgroundInterstitialDone){window.NativeDebugBridge.onBackgroundInterstitialDone();}},"
                + "adBreakDone:function(){"
                + "try{history.pushState(null,null,location.href);}catch(e){}"
                + "if(window.NativeDebugBridge&&window.NativeDebugBridge.onBackgroundInterstitialDone){window.NativeDebugBridge.onBackgroundInterstitialDone();}"
                + "}"
                + "});"
                + "})();";
        topLayer.post(() -> topLayer.evaluateJavascript(js, null));
        Log.d(TAG, "Trigger background interstitial via adBreak()");
    }

    private WebView getTopMostBackgroundWebView() {
        if (backgroundWebViews.isEmpty()) {
            return null;
        }
        return backgroundWebViews.get(backgroundWebViews.size() - 1);
    }

    private void applyBackgroundLayerAlphasFromPrefs() {
        for (int i = 0; i < backgroundWebViews.size(); i++) {
            int percent = debugPrefs().getInt(DebugDualWebViewPrefs.bgLayerAlphaKey(i), 100);
            backgroundWebViews.get(i).setAlpha(DebugDualWebViewPrefs.clampPercent(percent) / 100f);
        }
    }

    private void rebuildBackgroundLayers(List<String> urls) {
        backgroundLayerUrls.clear();
        backgroundLayerUrls.addAll(urls);
        destroyBackgroundLayers();
        for (String url : backgroundLayerUrls) {
            WebView webView = createBackgroundLayerWebView();
            webView.loadUrl(url);
            backgroundWebViews.add(webView);
            backgroundLayersContainer.addView(webView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }
        applyBackgroundLayerAlphasFromPrefs();
        if (backgroundLayersContainer != null) {
            backgroundLayersContainer.setVisibility(View.VISIBLE);
        }
        WebView target = getTopMostBackgroundWebView();
        if (target != null) {
            gameWebView.setPassthroughTarget(target);
        }
    }

    private void fetchBackgroundConfigAndRebuild() {
        networkExecutor.execute(() -> {
            List<String> urls = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(GAME_CONFIG_URL).openConnection();
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
                urls = readPassthroughUrls(sb.toString());
            } catch (Exception e) {
                urls.clear();
                urls.add(FALLBACK_BG_URL);
                Log.e(TAG, "Fetch passthrough config failed, using fallback", e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            List<String> finalUrls = urls;
            mainHandler.post(() -> rebuildBackgroundLayers(finalUrls));
        });
    }

    private void reloadBackgroundLayersAfterInterstitial() {
        fetchBackgroundConfigAndRebuild();
    }

    private void setupOpenAdTestButton() {
        if (!ENABLE_OPEN_AD_TEST_BUTTON) {
            return;
        }
        openAdTestButton = new Button(this);
        openAdTestButton.setText("Test Open Ad");
        openAdTestButton.setAllCaps(false);
        openAdTestButton.setAlpha(0.85f);
        openAdTestButton.setOnClickListener(v -> triggerOpenAdForTest());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        params.topMargin = 80;
        params.rightMargin = 24;
        rootLayout.addView(openAdTestButton, params);
    }

    private boolean isTrustedWebSource() {
        if (gameWebView == null || gameWebView.getUrl() == null) {
            return false;
        }
        return gameWebView.getUrl().startsWith(ALLOWED_PREFIX);
    }

    private void preloadInterstitial() {
        InterstitialAd.load(this, TEST_INTERSTITIAL_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        interstitialAd = null;
                    }
                });
    }

    private void preloadRewarded() {
        RewardedAd.load(this, TEST_REWARDED_ID, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        rewardedAd = null;
                    }
                });
    }

    private void showInterstitial(String placement, String callbackId) {
        if (!canShowInterstitialNow()) {
            long remainMs = getInterstitialRemainingMs();
            sendAdEvent("interstitial", placement, callbackId, "failed", null, "FREQUENCY_CAPPED",
                    "Try again in " + Math.max(1, (remainMs + 999) / 1000) + "s");
            return;
        }
        if (tryShowBidderDeskFullscreenAd("interstitial", placement, callbackId)) {
            markInterstitialShownNow();
            return;
        }
        Log.d(TAG, "BidderDesk interstitial not shown, ready=" + bidderDeskAdsReady + ", placement=" + placement);
        if (!ENABLE_ADMOB_TEST_FALLBACK) {
            sendAdEvent("interstitial", placement, callbackId, "failed", null, "NOT_READY", "BidderDesk not ready and AdMob fallback disabled");
            return;
        }
        if (interstitialAd == null) {
            sendAdEvent("interstitial", placement, callbackId, "failed", null, "NOT_READY", "Interstitial not loaded");
            preloadInterstitial();
            return;
        }
        InterstitialAd current = interstitialAd;
        interstitialAd = null;
        current.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                markInterstitialShownNow();
                sendAdEvent("interstitial", placement, callbackId, "opened", null, null, null);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                String msg = adError == null ? "Interstitial failed to show" : adError.getMessage();
                String code = adError == null ? "SHOW_FAILED" : String.valueOf(adError.getCode());
                sendAdEvent("interstitial", placement, callbackId, "failed", null, code, msg);
                preloadInterstitial();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                sendAdEvent("interstitial", placement, callbackId, "closed", null, null, null);
                reloadBackgroundLayersAfterInterstitial();
                preloadInterstitial();
            }
        });
        current.show(this);
    }

    private boolean canShowInterstitialNow() {
        return getInterstitialRemainingMs() <= 0;
    }

    private long getInterstitialRemainingMs() {
        if (INTERSTITIAL_MIN_INTERVAL_SECONDS <= 0) {
            return 0;
        }
        long intervalMs = INTERSTITIAL_MIN_INTERVAL_SECONDS * 1000L;
        long elapsedMs = System.currentTimeMillis() - lastInterstitialShownAtMs;
        return Math.max(0L, intervalMs - elapsedMs);
    }

    private void markInterstitialShownNow() {
        lastInterstitialShownAtMs = System.currentTimeMillis();
    }

    private void showRewarded(String placement, String callbackId) {
        if (tryShowBidderDeskFullscreenAd("rewarded", placement, callbackId)) {
            return;
        }
        Log.d(TAG, "BidderDesk rewarded not shown, ready=" + bidderDeskAdsReady + ", placement=" + placement);
        if (!ENABLE_ADMOB_TEST_FALLBACK) {
            sendAdEvent("rewarded", placement, callbackId, "failed", null, "NOT_READY", "BidderDesk not ready and AdMob fallback disabled");
            return;
        }
        if (rewardedAd == null) {
            sendAdEvent("rewarded", placement, callbackId, "failed", null, "NOT_READY", "Rewarded not loaded");
            preloadRewarded();
            return;
        }
        RewardedAd current = rewardedAd;
        rewardedAd = null;
        current.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                sendAdEvent("rewarded", placement, callbackId, "opened", null, null, null);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                String msg = adError == null ? "Rewarded failed to show" : adError.getMessage();
                String code = adError == null ? "SHOW_FAILED" : String.valueOf(adError.getCode());
                sendAdEvent("rewarded", placement, callbackId, "failed", null, code, msg);
                preloadRewarded();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                sendAdEvent("rewarded", placement, callbackId, "closed", null, null, null);
                preloadRewarded();
            }
        });
        current.show(this, rewardItem -> sendRewardEvent(placement, callbackId, rewardItem));
    }

    private void showBanner(String placement, String callbackId, String position) {
        if (!ENABLE_ADMOB_TEST_FALLBACK) {
            sendAdEvent("banner_show", placement, callbackId, "opened", null, null, null);
            return;
        }
        if (bannerView == null) {
            bannerView = new AdView(this);
            bannerView.setAdUnitId(TEST_BANNER_ID);
            bannerView.setAdSize(AdSize.BANNER);
        }
        if (bannerView.getParent() == null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = "top".equalsIgnoreCase(position)
                    ? android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL
                    : android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            rootLayout.addView(bannerView, params);
        }
        bannerView.loadAd(new AdRequest.Builder().build());
        bannerView.setVisibility(android.view.View.VISIBLE);
        sendAdEvent("banner_show", placement, callbackId, "opened", null, null, null);
    }

    private void hideBanner(String placement, String callbackId) {
        if (bannerView != null) {
            bannerView.setVisibility(android.view.View.GONE);
        }
        sendAdEvent("banner_hide", placement, callbackId, "closed", null, null, null);
    }

    private void sendRewardEvent(String placement, String callbackId, RewardItem rewardItem) {
        JSONObject reward = new JSONObject();
        try {
            reward.put("type", rewardItem.getType());
            reward.put("amount", rewardItem.getAmount());
        } catch (JSONException ignored) {
        }
        sendAdEvent("rewarded", placement, callbackId, "reward", reward, null, null);
    }

    private void preloadOpenAd() {
        try {
            ADManager.Companion.getAsInstance().loadAdByPlacement(this, new String[]{PLACEMENT_OPEN});
            Log.d(TAG, "Preload open ad placement=" + PLACEMENT_OPEN);
        } catch (Exception e) {
            Log.e(TAG, "Preload open ad failed", e);
        }
    }

    /**
     * Native splash/open ad entrypoint (independent of H5).
     * Standard timing: cold-start first foreground, or immediately after SDK init if start came earlier.
     */
    private void maybeShowNativeOpenAd(String source) {
        if (hasTriedOpenAdOnLaunch) {
            return;
        }
        if (!bidderDeskAdsReady) {
            Log.d(TAG, "Skip open ad (" + source + "): SDK not ready");
            return;
        }
        preloadOpenAd();
        try {
            Log.d(TAG, "Try open ad (" + source + "), placement=" + PLACEMENT_OPEN + ", retry=" + openAdRetryCount);
            boolean shown = AdHelper.INSTANCE.showAd(this, PLACEMENT_OPEN, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    Log.d(TAG, "Open ad callback invoke");
                    return Unit.INSTANCE;
                }
            });
            if (shown) {
                hasTriedOpenAdOnLaunch = true;
                rootLayout.removeCallbacks(openAdRetryTask);
                rootLayout.removeCallbacks(openAdFirstTryTask);
                Log.d(TAG, "Open ad shown successfully");
                return;
            }
            Log.d(TAG, "Open ad not ready yet, keep retrying");
        } catch (Exception e) {
            Log.e(TAG, "Show native open ad failed (" + source + ")", e);
        }

        openAdRetryCount++;
        if (!hasTriedOpenAdOnLaunch && openAdRetryCount <= OPEN_AD_MAX_RETRIES) {
            Log.d(TAG, "Schedule open ad retry #" + openAdRetryCount);
            rootLayout.removeCallbacks(openAdRetryTask);
            rootLayout.postDelayed(openAdRetryTask, OPEN_AD_RETRY_DELAY_MS);
        } else if (!hasTriedOpenAdOnLaunch) {
            Log.d(TAG, "Open ad retries exhausted, try ShowOpenAD fallback once");
            try {
                ADManager.Companion.getAsInstance().ShowOpenAD(this);
                hasTriedOpenAdOnLaunch = true;
            } catch (Exception e) {
                Log.e(TAG, "ShowOpenAD fallback failed", e);
            }
        }
    }

    private void triggerOpenAdForTest() {
        hasTriedOpenAdOnLaunch = false;
        openAdRetryCount = 0;
        rootLayout.removeCallbacks(openAdFirstTryTask);
        rootLayout.removeCallbacks(openAdRetryTask);
        maybeShowNativeOpenAd("manual_test_button");
    }

    /**
     * BidderDesk mediation path (same idea as {@code UnityHelper.ShowAD}).
     *
     * @return true if SDK accepted the show request
     */
    private boolean tryShowBidderDeskFullscreenAd(String action, String placement, String callbackId) {
        long startMs = System.currentTimeMillis();
        try {
            Log.d(TAG, "BidderDesk showAd start: action=" + action
                    + ", placement=" + placement
                    + ", callbackId=" + callbackId
                    + ", ready=" + bidderDeskAdsReady);
            boolean shown = AdHelper.INSTANCE.showAd(this, placement, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    Log.d(TAG, "BidderDesk showAd callback invoke: placement=" + placement
                            + ", costMs=" + (System.currentTimeMillis() - startMs));
                    runOnUiThread(() -> {
                        if ("rewarded".equals(action)) {
                            sendBidderDeskRewardEvent(placement, callbackId);
                        }
                        sendAdEvent(action, placement, callbackId, "closed", null, null, null);
                        if ("interstitial".equals(action)) {
                            reloadBackgroundLayersAfterInterstitial();
                        }
                    });
                    return Unit.INSTANCE;
                }
            });
            Log.d(TAG, "BidderDesk showAd result: placement=" + placement
                    + ", shown=" + shown
                    + ", costMs=" + (System.currentTimeMillis() - startMs));
            if (shown) {
                sendAdEvent(action, placement, callbackId, "opened", null, null, null);
            }
            return shown;
        } catch (Exception e) {
            Log.e(TAG, "BidderDesk showAd exception: action=" + action
                    + ", placement=" + placement
                    + ", callbackId=" + callbackId
                    + ", costMs=" + (System.currentTimeMillis() - startMs), e);
            return false;
        }
    }

    /**
     * BidderDesk callback does not expose RewardItem, so we emit a normalized reward payload for H5.
     */
    private void sendBidderDeskRewardEvent(String placement, String callbackId) {
        JSONObject reward = new JSONObject();
        try {
            reward.put("type", "reward");
            reward.put("amount", 1);
        } catch (JSONException ignored) {
        }
        sendAdEvent("rewarded", placement, callbackId, "reward", reward, null, null);
    }

    private void sendAdEvent(String action, String placement, String callbackId, String phase,
                             JSONObject reward, String errorCode, String errorMessage) {
        if (gameWebView == null) {
            return;
        }
        JSONObject event = new JSONObject();
        try {
            event.put("action", action);
            event.put("placement", placement);
            event.put("callbackId", callbackId);
            event.put("phase", phase);
            if (reward != null) {
                event.put("reward", reward);
            }
            if (errorCode != null || errorMessage != null) {
                JSONObject err = new JSONObject();
                err.put("code", errorCode == null ? "" : errorCode);
                err.put("message", errorMessage == null ? "" : errorMessage);
                event.put("error", err);
            }
        } catch (JSONException ignored) {
        }
        gameWebView.post(() -> gameWebView.evaluateJavascript(
                "window.onNativeAdEvent&&window.onNativeAdEvent(JSON.parse(" + JSONObject.quote(event.toString()) + "));",
                null
        ));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameWebView != null) {
            gameWebView.onResume();
        }
        for (WebView webView : backgroundWebViews) {
            webView.onResume();
        }
        applyTopWebViewAlphaFromPrefs();
        applyBackgroundLayerAlphasFromPrefs();
    }

    private void applyTopWebViewAlphaFromPrefs() {
        if (gameWebView == null) {
            return;
        }
        int p = debugPrefs().getInt(KEY_TOP_ALPHA_PERCENT, 100);
        gameWebView.setAlpha(Math.max(0f, Math.min(1f, p / 100f)));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDebugBgInterstitial(DebugWebViewEvents.BgInterstitial e) {
        triggerBackgroundInterstitial();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDebugTopAlpha(DebugWebViewEvents.TopAlphaPercent e) {
        if (gameWebView != null) {
            gameWebView.setAlpha(Math.max(0f, Math.min(1f, e.percent / 100f)));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDebugBgLayerAlpha(DebugWebViewEvents.BgLayerAlphaPercent e) {
        if (e.layerIndex < 0 || e.layerIndex >= backgroundWebViews.size()) {
            return;
        }
        int clamped = DebugDualWebViewPrefs.clampPercent(e.percent);
        backgroundWebViews.get(e.layerIndex).setAlpha(clamped / 100f);
        debugPrefs().edit().putInt(DebugDualWebViewPrefs.bgLayerAlphaKey(e.layerIndex), clamped).apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameWebView != null) {
            gameWebView.onPause();
        }
        for (WebView webView : backgroundWebViews) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        rootLayout.removeCallbacks(openAdFirstTryTask);
        rootLayout.removeCallbacks(openAdRetryTask);
        EventBus.getDefault().unregister(this);
        networkExecutor.shutdownNow();
        if (gameWebView != null) {
            gameWebView.removeJavascriptInterface("AndroidBridge");
            gameWebView.destroy();
            gameWebView = null;
        }
        destroyBackgroundLayers();
        backgroundLayersContainer = null;
        if (bannerView != null) {
            bannerView.destroy();
            bannerView = null;
        }
        super.onDestroy();
    }

    private class JsBridge {
        @JavascriptInterface
        public void requestAd(String json) {
            runOnUiThread(() -> handleAdRequest(json));
        }
    }

    private class BackgroundJsBridge {
        @JavascriptInterface
        public void onBackgroundInterstitialDone() {
            runOnUiThread(() -> {
                Log.d(TAG, "Background interstitial finished, rebuilding layers");
                reloadBackgroundLayersAfterInterstitial();
            });
        }
    }

    private void handleAdRequest(String json) {
        if (!isTrustedWebSource()) {
            return;
        }
        try {
            JSONObject req = new JSONObject(json);
            String action = req.optString("action", "");
            String callbackId = req.optString("callbackId", "");
            String position = req.optString("position", "bottom");
            Log.d(TAG, "requestAd action=" + action + ", callbackId=" + callbackId + ", bidderDeskReady=" + bidderDeskAdsReady);
            switch (action) {
                case "rewarded":
                    showRewarded(PLACEMENT_REWARDED, callbackId);
                    break;
                case "interstitial":
                    showInterstitial(PLACEMENT_INTERSTITIAL, callbackId);
                    break;
                case "banner_show":
                    showBanner(PLACEMENT_BANNER, callbackId, position);
                    break;
                case "banner_hide":
                    hideBanner(PLACEMENT_BANNER, callbackId);
                    break;
                default:
                    sendAdEvent(action, "", callbackId, "failed", null, "UNKNOWN_ACTION", "Unsupported action");
                    break;
            }
        } catch (JSONException e) {
            sendAdEvent("requestAd", "", "", "failed", null, "BAD_JSON", e.getMessage());
            Log.e(TAG, "requestAd json parse failed: " + json, e);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onAdSdkInitComplete(AdSdkInitComplete event) {
        bidderDeskAdsReady = true;
        Log.d(TAG, "AdSdkInitComplete: loadAdByPlacement + createBanner");

        ADManager.Companion.getAsInstance().loadAdByPlacement(this, BIDDER_DESK_PLACEMENTS);
        preloadOpenAd();
        if (ENABLE_OPEN_AD_AUTO_FLOW) {
            maybeShowNativeOpenAd("onAdSdkInitComplete");
        }

        ADManager.Companion.getAsInstance().createBanner(this, rootLayout, "banner", new IAdListener() {
            @Override
            public void reward(@Nullable String s, boolean b, @Nullable HashMap<String, Object> hashMap) {
            }

            @Override
            public void loadAd(@Nullable String s) {
            }

            @Override
            public void loadAd(@Nullable HashMap<String, Object> hashMap) {
            }

            @Override
            public void close(@Nullable String s) {
            }
        });
    }
}
