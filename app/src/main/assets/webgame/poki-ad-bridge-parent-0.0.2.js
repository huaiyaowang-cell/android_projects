/**
 * Talking Tom Gold Run — 父页面广告桥接（Android 原生优先，fallback AdSense）
 */
(function () {
  "use strict";

  var gameFrame = document.getElementById("ttgrGameFrame");
  var nativePending = Object.create(null);

  function isOfflineEnvironment() {
    var isFileProtocol = false;
    try {
      isFileProtocol = window.location && window.location.protocol === "file:";
    } catch (e) {}
    return isFileProtocol || (typeof navigator !== "undefined" && navigator.onLine === false);
  }

  function isNativeBridgeReady() {
    return !!(window.AndroidBridge && typeof window.AndroidBridge.requestAd === "function");
  }

  function setGameFrameBlocked(blocked) {
    if (!gameFrame) return;
    try {
      gameFrame.style.pointerEvents = blocked ? "none" : "";
    } catch (e) {}
  }

  function notifyIframePhase(requestId, phaseType) {
    try {
      if (!gameFrame || !gameFrame.contentWindow || requestId == null) return;
      gameFrame.contentWindow.postMessage(
        { type: phaseType, requestId: requestId },
        "*"
      );
    } catch (e) {}
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

  function focusForAd() {
    try { window.focus(); } catch (e) {}
    try {
      if (document.body && typeof document.body.focus === "function") document.body.focus();
    } catch (e2) {}
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
      // 兼容原生只发 reward、未发 closed 的情况（正常应先 reward 再 closed）
      if (pending.action === "rewarded") {
        setTimeout(function () {
          var still = nativePending[event.callbackId];
          if (!still || still.action !== "rewarded") return;
          delete nativePending[event.callbackId];
          still.resolve({ rewardGranted: true });
        }, 300);
      }
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
    try { handleNativeEvent(event); } catch (e) {}
    if (typeof previousNativeAdEvent === "function") {
      try { previousNativeAdEvent(event); } catch (e) {}
    }
  };

  function waitForGoogleAdsReady(timeoutMs) {
    timeoutMs = timeoutMs == null ? 8000 : timeoutMs;
    return new Promise(function (resolve) {
      if (window.__googleAdsReady && typeof window.adBreak === "function") {
        return resolve(true);
      }
      var start = Date.now();
      var timer = setInterval(function () {
        if (window.__googleAdsReady && typeof window.adBreak === "function") {
          clearInterval(timer);
          resolve(true);
          return;
        }
        if (Date.now() - start >= timeoutMs) {
          clearInterval(timer);
          resolve(false);
        }
      }, 200);
    });
  }

  function showCommercialBreakWeb(requestId) {
    return waitForGoogleAdsReady(8000).then(function (sdkReady) {
      return new Promise(function (resolve) {
        if (isOfflineEnvironment() || !sdkReady) {
          return resolve({ skipped: true, reason: isOfflineEnvironment() ? "offline" : "sdk_not_ready" });
        }

        var settled = false;
        function finish(result) {
          if (settled) return;
          settled = true;
          setGameFrameBlocked(false);
          resolve(result || {});
        }

        focusForAd();
        setGameFrameBlocked(true);
        window.adBreak({
          type: "browse",
          name: "talking-tom-gold-run-commercial",
          beforeAd: function () {
            focusForAd();
            notifyIframePhase(requestId, "poki_ad_before");
          },
          afterAd: function () { setGameFrameBlocked(false); },
          adBreakDone: function (placementInfo) {
            try { history.pushState(null, null, location.href); } catch (e) {}
            finish({ breakStatus: placementInfo && placementInfo.breakStatus || null, skipped: false });
          },
        });
      });
    });
  }

  function showRewardedBreakWeb(requestId) {
    return waitForGoogleAdsReady(8000).then(function (sdkReady) {
      return new Promise(function (resolve) {
        if (isOfflineEnvironment() || !sdkReady) {
          return resolve({ rewardGranted: false, skipped: true, reason: isOfflineEnvironment() ? "offline" : "sdk_not_ready" });
        }

        var settled = false;
        var rewardEarned = false;

        function finish(granted, extra) {
          if (settled) return;
          settled = true;
          setGameFrameBlocked(false);
          var result = { rewardGranted: !!granted, skipped: false };
          if (extra) { for (var k in extra) { if (Object.prototype.hasOwnProperty.call(extra, k)) result[k] = extra[k]; } }
          resolve(result);
        }

        focusForAd();
        setGameFrameBlocked(true);
        window.adBreak({
          type: "reward",
          name: "talking-tom-gold-run-reward",
          beforeAd: function () {
            focusForAd();
            notifyIframePhase(requestId, "poki_ad_reward_start");
          },
          afterAd: function () { setGameFrameBlocked(false); },
          beforeReward: function (showAdFn) { if (showAdFn) try { showAdFn(); } catch (e) {} },
          adDismissed: function () { if (!settled) finish(false, { breakStatus: "dismissed" }); },
          adViewed: function () { rewardEarned = true; if (!settled) finish(true, { breakStatus: "viewed" }); },
          adBreakDone: function (placementInfo) {
            if (settled) return;
            var st = placementInfo && placementInfo.breakStatus;
            finish(rewardEarned || (st && String(st).toLowerCase() === "viewed"), { breakStatus: st || null });
          },
        });
      });
    });
  }

  window.commercialBreakBlockCOunt = 0;

  function showCommercialBreak(requestId) {
    if (isNativeBridgeReady()) {
      focusForAd();
      setGameFrameBlocked(true);
      notifyIframePhase(requestId, "poki_ad_before");
      return nativeRequest("interstitial", "poki_commercial", "bottom")
        .then(function (result) {
          setGameFrameBlocked(false);
          return result || {};
        })
        .catch(function () {
          setGameFrameBlocked(false);
          return { skipped: true, reason: "native_failed" };
        });
    }

    if (window.commercialBreakBlockCOunt < 1) {
      window.commercialBreakBlockCOunt++;
      return Promise.resolve({ skipped: true, reason: "commercialBreakBlockCOunt" });
    }
    return showCommercialBreakWeb(requestId);
  }

  function showRewardedBreak(requestId) {
    if (isNativeBridgeReady()) {
      focusForAd();
      setGameFrameBlocked(true);
      notifyIframePhase(requestId, "poki_ad_reward_start");
      return nativeRequest("rewarded", "poki_rewarded", "bottom")
        .then(function (result) {
          setGameFrameBlocked(false);
          return { rewardGranted: !!(result && result.rewardGranted) };
        })
        .catch(function () {
          setGameFrameBlocked(false);
          return { rewardGranted: false, skipped: true, reason: "native_failed" };
        });
    }
    return showRewardedBreakWeb(requestId);
  }

  function hideBannerOnGameplayStart() {
    if (isNativeBridgeReady()) {
      return nativeRequest("banner_hide", "poki_gameplay", "bottom").catch(function () { return {}; });
    }
    return Promise.resolve({});
  }

  function showBannerOnGameplayStop() {
    if (isNativeBridgeReady()) {
      return nativeRequest("banner_show", "poki_gameplay", "bottom").catch(function () { return {}; });
    }
    return Promise.resolve({});
  }

  function handle(payload, requestId) {
    payload = payload || {};
    if (payload.kind === "commercialBreak") return showCommercialBreak(requestId);
    if (payload.kind === "rewardedBreak") return showRewardedBreak(requestId);
    if (payload.kind === "gameplayStart") return hideBannerOnGameplayStart();
    if (payload.kind === "gameplayStop") return showBannerOnGameplayStop();
    return Promise.reject(new Error("invalid_poki_ad_kind"));
  }

  function dispatchAdRequest(payload, requestId, sourceWindow) {
    var fakeEvent = { source: sourceWindow };
    handle(payload, requestId)
      .then(function (result) { sendResponse(fakeEvent, requestId, true, result, null); })
      .catch(function (err) {
        setGameFrameBlocked(false);
        sendResponse(fakeEvent, requestId, false, {}, err && err.message ? err.message : String(err));
      });
  }

  window.__ttgrHandleAdRequest = function (payload, requestId, sourceWindow) {
    if (!sourceWindow) return;
    dispatchAdRequest(payload, requestId, sourceWindow);
  };

  window.addEventListener("message", function (event) {
    var data = event && event.data;
    if (!data || data.type !== "poki_ad_request") return;
    if (gameFrame && gameFrame.contentWindow && event.source !== gameFrame.contentWindow) return;
    if (data.requestId == null) return;
    dispatchAdRequest(data.payload, data.requestId, event.source);
  });
})();
