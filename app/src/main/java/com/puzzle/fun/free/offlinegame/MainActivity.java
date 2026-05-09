package com.puzzle.fun.free.offlinegame;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

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
    };
    private static final String PLACEMENT_REWARDED = "reward_01";
    private static final String PLACEMENT_INTERSTITIAL = "interstitial_01";
    private static final String PLACEMENT_BANNER = "banner_01";

    private static final String TEST_BANNER_ID = "ca-app-pub-2915030877224461/9728916209";
    private static final String TEST_INTERSTITIAL_ID = "ca-app-pub-2915030877224461/5570997584";
    private static final String TEST_REWARDED_ID = "ca-app-pub-2915030877224461/4285017834";
    /** Toggle AdMob test ad fallback (TEST_* ids) in code. */
    private static final boolean ENABLE_ADMOB_TEST_FALLBACK = false;

    private FrameLayout rootLayout;
    private WebView gameWebView;
    private WebViewAssetLoader assetLoader;

    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private AdView bannerView;

    /** When true, fullscreen ads from H5 use {@link AdHelper}; banner still uses Web-driven AdMob unless you rely on BidderDesk banner only. */
    private volatile boolean bidderDeskAdsReady;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterFullscreen();
        setupRoot();
        setupWebView();
        if (ENABLE_ADMOB_TEST_FALLBACK) {
            preloadInterstitial();
            preloadRewarded();
        }
        EventBus.getDefault().register(this);
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

        gameWebView = new WebView(this);
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
        if (tryShowBidderDeskFullscreenAd("interstitial", placement, callbackId)) {
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
                sendAdEvent("interstitial", placement, callbackId, "opened", null, null, null);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                sendAdEvent("interstitial", placement, callbackId, "closed", null, null, null);
                preloadInterstitial();
            }
        });
        current.show(this);
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
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        if (gameWebView != null) {
            gameWebView.removeJavascriptInterface("AndroidBridge");
            gameWebView.destroy();
            gameWebView = null;
        }
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
        } catch (JSONException ignored) {
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onAdSdkInitComplete(AdSdkInitComplete event) {
        bidderDeskAdsReady = true;
        Log.d(TAG, "AdSdkInitComplete: loadAdByPlacement + createBanner");

        ADManager.Companion.getAsInstance().loadAdByPlacement(this, BIDDER_DESK_PLACEMENTS);

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
