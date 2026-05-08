/**
 * Happy Glass — 父页面广告桥接（AdSense / adBreak）
 */
(function () {
  "use strict";

  var gameFrame = document.getElementById("hgGameFrame");
  var nativePending = Object.create(null);

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

  function isNativeBridgeReady() {
    return !!(window.AndroidBridge && typeof window.AndroidBridge.requestAd === "function");
  }

  function nativeRequest(action, placement, position) {
    return new Promise(function (resolve, reject) {
      if (!isNativeBridgeReady()) {
        reject(new Error("native_bridge_unavailable"));
        return;
      }
      var callbackId = "native_" + Date.now().toString(36) + "_" + Math.random().toString(36).slice(2);
      nativePending[callbackId] = { action: action, resolve: resolve, reject: reject, rewarded: false };
      try {
        window.AndroidBridge.requestAd(JSON.stringify({
          action: action,
          placement: placement || "",
          position: position || "bottom",
          callbackId: callbackId
        }));
      } catch (e) {
        delete nativePending[callbackId];
        reject(e);
      }
    });
  }

  function handleNativeEvent(event) {
    if (!event || !event.callbackId) return;
    var pending = nativePending[event.callbackId];
    if (!pending) return;

    if (event.phase === "reward") {
      pending.rewarded = true;
      return;
    }

    if (event.phase === "failed") {
      delete nativePending[event.callbackId];
      var errMsg = event.error && event.error.message ? event.error.message : "native_ad_failed";
      pending.reject(new Error(errMsg));
      return;
    }

    if (pending.action === "rewarded" && event.phase === "closed") {
      delete nativePending[event.callbackId];
      pending.resolve({ rewardGranted: !!pending.rewarded });
      return;
    }

    if (pending.action === "interstitial" && event.phase === "closed") {
      delete nativePending[event.callbackId];
      pending.resolve({});
      return;
    }

    if (pending.action === "banner_show" && event.phase === "opened") {
      delete nativePending[event.callbackId];
      pending.resolve({});
      return;
    }

    if (pending.action === "banner_hide" && event.phase === "closed") {
      delete nativePending[event.callbackId];
      pending.resolve({});
    }
  }

  var previousNativeAdEvent = window.onNativeAdEvent;
  window.onNativeAdEvent = function (event) {
    try {
      handleNativeEvent(event);
    } catch (e) {}
    if (typeof previousNativeAdEvent === "function") {
      try { previousNativeAdEvent(event); } catch (e) {}
    }
  };

  function showCommercialBreakNativeFirst() {
    if (isNativeBridgeReady()) {
      return nativeRequest("interstitial", "poki_commercial", "bottom");
    }
    return showCommercialBreak();
  }

  function showRewardedBreakNativeFirst() {
    if (isNativeBridgeReady()) {
      return nativeRequest("rewarded", "poki_rewarded", "bottom")
        .then(function (result) {
          return { rewardGranted: !!(result && result.rewardGranted) };
        });
    }
    return showRewardedBreak();
  }

  function hideBannerOnGameplayStart() {
    if (isNativeBridgeReady()) {
      return nativeRequest("banner_hide", "poki_gameplay", "bottom");
    }
    return Promise.resolve({});
  }

  function showBannerOnGameplayStop() {
    if (isNativeBridgeReady()) {
      return nativeRequest("banner_show", "poki_gameplay", "bottom");
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

