/**
 * Bullet Bros — 父页面广告桥接
 * 优先 Android 原生（AndroidBridge）；否则回退 AdSense adBreak。
 * 由 index.html 加载：响应 iframe 内游戏通过 postMessage 发来的 Poki 广告请求。
 */
(function () {
  "use strict";

  var gameFrame = document.getElementById("bbGameFrame");
  var pendingNativeAds = Object.create(null);

  function isAndroidBridgeAvailable() {
    try {
      return !!(
        window.AndroidBridge &&
        typeof window.AndroidBridge.requestAd === "function"
      );
    } catch (e) {
      return false;
    }
  }

  function genCallbackId() {
    return Date.now().toString(36) + "_" + Math.random().toString(36).slice(2);
  }

  window.onNativeAdEvent = function (event) {
    if (!event || !event.callbackId) return;
    var pending = pendingNativeAds[event.callbackId];
    if (!pending) return;

    var phase = event.phase;

    if (phase === "opened") {
      console.log("[poki-ad-parent] native ad opened:", event.action, event.callbackId);
      return;
    }

    if (phase === "reward" && event.action === "rewarded") {
      pending.rewardGranted = true;
      return;
    }

    if (phase === "closed") {
      delete pendingNativeAds[event.callbackId];
      if (pending.action === "interstitial") {
        pending.resolve({});
        return;
      }
      if (pending.action === "rewarded") {
        if (pending.rewardGranted) {
          pending.resolve({ rewardGranted: true });
        } else {
          pending.reject({ rewardGranted: false });
        }
      }
      return;
    }

    if (phase === "failed") {
      delete pendingNativeAds[event.callbackId];
      var errObj = event.error || {};
      var msg = errObj.message || errObj.code || "native_ad_failed";
      console.warn("[poki-ad-parent] native ad failed:", event.action, msg);
      if (pending.action === "interstitial") {
        pending.reject(new Error(msg));
      } else {
        pending.reject({ rewardGranted: false, error: msg });
      }
    }
  };

  function requestNativeAd(action) {
    return new Promise(function (resolve, reject) {
      var callbackId = genCallbackId();
      pendingNativeAds[callbackId] = {
        action: action,
        resolve: resolve,
        reject: reject,
        rewardGranted: false,
      };
      try {
        window.AndroidBridge.requestAd(
          JSON.stringify({ action: action, callbackId: callbackId })
        );
      } catch (e) {
        delete pendingNativeAds[callbackId];
        reject(e);
      }
    });
  }

  function showNativeCommercialBreak() {
    console.log("[poki-ad-parent] commercialBreak -> AndroidBridge interstitial");
    return requestNativeAd("interstitial");
  }

  function showNativeRewardedBreak() {
    console.log("[poki-ad-parent] rewardedBreak -> AndroidBridge rewarded");
    return requestNativeAd("rewarded");
  }

  function exitFullscreenIfNeeded() {
    var d = document;
    var exitFn =
      d.exitFullscreen ||
      d.webkitExitFullscreen ||
      d.mozCancelFullScreen ||
      d.msExitFullscreen;

    if (typeof exitFn !== "function") return Promise.resolve();
    if (
      !d.fullscreenElement &&
      !d.webkitFullscreenElement &&
      !d.mozFullScreenElement &&
      !d.msFullscreenElement
    ) {
      return Promise.resolve();
    }

    try {
      var ret = exitFn.call(d);
      if (ret && typeof ret.then === "function") return ret.catch(function () {});
    } catch (e) {}
    return Promise.resolve();
  }

  function sendResponse(event, requestId, ok, result, error) {
    try {
      if (!event || !event.source || typeof event.source.postMessage !== "function")
        return;
      event.source.postMessage(
        {
          type: "poki_ad_response",
          requestId: requestId,
          ok: !!ok,
          result: result || {},
          error: error || null,
        },
        "*"
      );
    } catch (e) {}
  }

  function showCommercialBreak() {
    if (isAndroidBridgeAvailable()) {
      return showNativeCommercialBreak();
    }
    return new Promise(function (resolve, reject) {
      if (typeof window.adBreak !== "function") {
        console.warn("[poki-ad-parent] adBreak 不可用，跳过 commercialBreak");
        resolve({});
        return;
      }
      if (!window.__googleAdsReady) {
        console.warn("[poki-ad-parent] Google Ads SDK 未准备好，跳过 commercialBreak");
        resolve({});
        return;
      }
      exitFullscreenIfNeeded().then(function () {
        window.adBreak({
          type: "browse",
          name: "bullet-bros-commercial",
          beforeAd: function () {},
          afterAd: function () {},
          adBreakDone: function (placementInfo) {
            history.pushState(null, null, location.href);
            console.log(
              "[poki-ad-parent] commercial adBreakDone:",
              placementInfo && placementInfo.breakStatus
            );
            if (window.LogFirebaseEvent && typeof window.LogFirebaseEvent === "function") {
              window.LogFirebaseEvent(
                placementInfo && placementInfo.breakStatus === "viewed"
                  ? "ads_interstitial_ad_viewed"
                  : "ads_interstitial_ad_unknown_fail",
                { adSlot: "bullet-bros-commercial" }
              );
            }
            resolve({});
          },
        });
      });
    });
  }

  function showRewardedBreak() {
    if (isAndroidBridgeAvailable()) {
      return showNativeRewardedBreak();
    }
    return new Promise(function (resolve, reject) {
      if (typeof window.adBreak !== "function") {
        console.warn("[poki-ad-parent] adBreak 不可用，跳过 rewardedBreak（视为已发奖）");
        resolve({ rewardGranted: true });
        return;
      }
      exitFullscreenIfNeeded().then(function () {
        window.adBreak({
          type: "reward",
          name: "bullet-bros-reward",
          beforeAd: function () {},
          afterAd: function () {},
          beforeReward: function (showAdFn) {
            showAdFn && showAdFn();
          },
          adDismissed: function () {},
          adViewed: function () {},
          adBreakDone: function (placementInfo) {
            var viewed = placementInfo && placementInfo.breakStatus === "viewed";
            if (viewed) {
              history.pushState(null, null, location.href);
              if (window.LogFirebaseEvent && typeof window.LogFirebaseEvent === "function") {
                window.LogFirebaseEvent("ads_reward_ad_viewed", { adSlot: "bullet-bros-reward" });
              }
              resolve({ rewardGranted: true });
            } else if (placementInfo && placementInfo.breakStatus === "dismissed") {
              history.pushState(null, null, location.href);
              if (window.LogFirebaseEvent && typeof window.LogFirebaseEvent === "function") {
                window.LogFirebaseEvent("ads_reward_ad_dismissed", { adSlot: "bullet-bros-reward" });
              }
              reject({ rewardGranted: false });
            } else {
              if (window.LogFirebaseEvent && typeof window.LogFirebaseEvent === "function") {
                window.LogFirebaseEvent("ads_reward_ad_unknown_fail", { adSlot: "bullet-bros-reward" });
              }
              reject({ rewardGranted: false });
            }
          },
        });
      });
    });
  }

  function handlePokiAdRequest(payload) {
    payload = payload || {};
    var kind = payload.kind;
    switch (kind) {
      case "commercialBreak":
        return showCommercialBreak();
      case "rewardedBreak":
        return showRewardedBreak();
      default:
        return Promise.reject(new Error("invalid_poki_ad_kind"));
    }
  }

  if (isAndroidBridgeAvailable()) {
    console.log("[poki-ad-parent] AndroidBridge 已启用，插屏/激励走原生广告");
  }

  window.addEventListener("message", function (event) {
    var data = event && event.data;
    if (!data || data.type !== "poki_ad_request") return;
    if (gameFrame && gameFrame.contentWindow && event.source !== gameFrame.contentWindow) {
      return;
    }

    var requestId = data.requestId;
    if (requestId == null) return;

    handlePokiAdRequest(data.payload)
      .then(function (result) {
        sendResponse(event, requestId, true, result, null);
      })
      .catch(function (err) {
        sendResponse(
          event,
          requestId,
          false,
          {},
          err && err.message ? err.message : String(err)
        );
      });
  });
})();
