/**
 * Happy Glass — 父页面广告桥接（AdSense / adBreak）
 * 与原生通信统一经 {@link window.NativeAdmobJSSDK}（见 native-admob-jssdk.js）。
 */
(function () {
  "use strict";

  var gameFrame = document.getElementById("hgGameFrame");

  function isOfflineEnvironment() {
    var isFileProtocol = false;
    try {
      isFileProtocol = window.location && window.location.protocol === "file:";
    } catch (e) {}
    return isFileProtocol || (typeof navigator !== "undefined" && navigator.onLine === false);
  }

  function sendResponse(event, requestId, ok, result, error) {
    try {
      if (!event || !event.source || typeof event.source.postMessage !== "function") return;
      event.source.postMessage(
        { type: "poki_ad_response", requestId: requestId, ok: !!ok, result: result || {}, error: error || null },
        "*"
      );
    } catch (e) {}
  }

  function showCommercialBreak() {
    return new Promise(function (resolve) {
      if (!window.__googleAdsReady) return resolve({});
      if (typeof window.adBreak !== "function") return resolve({});
      window.adBreak({
        type: "browse",
        name: "happy-glass-commercial",
        beforeAd: function () {},
        afterAd: function () {},
        adBreakDone: function () {
          try { history.pushState(null, null, location.href); } catch (e) {}
          resolve({});
        },
      });
    });
  }

  function showRewardedBreak() {
    return new Promise(function (resolve) {
      if (!window.__googleAdsReady) return resolve({ rewardGranted: false });
      if (typeof window.adBreak !== "function") return resolve({ rewardGranted: false });
      window.adBreak({
        type: "reward",
        name: "happy-glass-reward",
        beforeAd: function () {},
        afterAd: function () {},
        beforeReward: function (showAdFn) {
          showAdFn && showAdFn();
        },
        adDismissed: function () {},
        adViewed: function () {},
        adBreakDone: function (placementInfo) {
          var viewed = placementInfo && placementInfo.breakStatus === "viewed";
          if (viewed) resolve({ rewardGranted: true });
          else resolve({ rewardGranted: false });
        },
      });
    });
  }

  function sdk() {
    return window.NativeAdmobJSSDK;
  }

  function isNativeBridgeReady() {
    return sdk() && typeof sdk().isAvailable === "function" && sdk().isAvailable();
  }

  function showCommercialBreakNativeFirst() {
    if (isNativeBridgeReady()) {
      return sdk().showInterstitial("poki_commercial");
    }
    return showCommercialBreak();
  }

  function showRewardedBreakNativeFirst() {
    if (isNativeBridgeReady()) {
      return sdk().showRewarded("poki_rewarded");
    }
    return showRewardedBreak();
  }

  function hideBannerOnGameplayStart() {
    if (isNativeBridgeReady()) {
      return sdk().hideBanner("poki_gameplay");
    }
    return Promise.resolve({});
  }

  function showBannerOnGameplayStop() {
    if (isNativeBridgeReady()) {
      return sdk().showBanner("bottom", "poki_gameplay");
    }
    return Promise.resolve({});
  }

  function handle(payload) {
    payload = payload || {};
    if (payload.kind === "commercialBreak") return showCommercialBreakNativeFirst();
    if (payload.kind === "rewardedBreak") return showRewardedBreakNativeFirst();
    if (payload.kind === "gameplayStart") return hideBannerOnGameplayStart();
    if (payload.kind === "gameplayStop") return showBannerOnGameplayStop();
    return Promise.reject(new Error("invalid_poki_ad_kind"));
  }

  window.addEventListener("message", function (event) {
    var data = event && event.data;
    if (!data || data.type !== "poki_ad_request") return;
    if (gameFrame && gameFrame.contentWindow && event.source !== gameFrame.contentWindow) return;
    if (data.requestId == null) return;

    handle(data.payload)
      .then(function (result) { sendResponse(event, data.requestId, true, result, null); })
      .catch(function (err) {
        sendResponse(event, data.requestId, false, {}, err && err.message ? err.message : String(err));
      });
  });
})();
