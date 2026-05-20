/**
 * Count War — 父页面广告桥接（Native Java 广告，与 APK WebView 一致）
 */
(function () {
  "use strict";
  var gameFrame = document.getElementById("cwGameFrame");

  function sendResponse(event, requestId, ok, result, error) {
    try {
      if (!event || !event.source || typeof event.source.postMessage !== "function") return;
      event.source.postMessage(
        { type: "poki_ad_response", requestId: requestId, ok: !!ok, result: result || {}, error: error || null },
        "*"
      );
    } catch (e) {}
  }

  var pendingNative = Object.create(null);

  function hookNativeAdEventOnce() {
    if (window.__nativeAdEventHooked) return;
    window.__nativeAdEventHooked = true;

    var prev = window.onNativeAdEvent;
    window.onNativeAdEvent = function (event) {
      try {
        if (event && event.callbackId && pendingNative[event.callbackId]) {
          pendingNative[event.callbackId](event);
        }
      } catch (e) {}
      try {
        if (typeof prev === "function") return prev(event);
      } catch (e2) {}
    };
  }

  function waitForNativeComplete(callbackId, action) {
    return new Promise(function (resolve, reject) {
      var done = false;
      var rewardGranted = false;

      function finish(ok, err) {
        if (done) return;
        done = true;
        delete pendingNative[callbackId];
        if (ok) resolve({ rewardGranted: rewardGranted });
        else reject(err || new Error("native_ad_failed"));
      }

      var timeout = setTimeout(function () {
        finish(false, new Error("native_ad_timeout"));
      }, 120000);

      pendingNative[callbackId] = function (ev) {
        if (!ev || ev.action !== action) return;
        if (ev.phase === "reward") rewardGranted = true;
        if (ev.phase === "closed") {
          clearTimeout(timeout);
          finish(true, null);
        } else if (ev.phase === "failed") {
          clearTimeout(timeout);
          finish(false, new Error((ev.error && ev.error.message) || "native_ad_failed"));
        }
      };
    });
  }

  function waitForNativeRewardedComplete(callbackId) {
    return new Promise(function (resolve) {
      var done = false;
      var rewardGranted = false;

      function finishSuccess() {
        if (done) return;
        done = true;
        clearTimeout(timeout);
        delete pendingNative[callbackId];
        resolve({ rewardGranted: rewardGranted });
      }

      var timeout = setTimeout(function () {
        rewardGranted = false;
        finishSuccess();
      }, 120000);

      pendingNative[callbackId] = function (ev) {
        if (!ev || ev.action !== "rewarded") return;
        if (ev.phase === "reward") rewardGranted = true;
        if (ev.phase === "closed") {
          finishSuccess();
        } else if (ev.phase === "failed") {
          rewardGranted = false;
          finishSuccess();
        }
      };
    });
  }

  function showCommercialBreak() {
    hookNativeAdEventOnce();
    if (!window.NativeAdmobJSSDK || typeof window.NativeAdmobJSSDK.showInterstitial !== "function") {
      return Promise.resolve({});
    }
    return window.NativeAdmobJSSDK.showInterstitial("interstitial_01").then(function (r) {
      return waitForNativeComplete(r.callbackId, "interstitial").then(function () {
        return {};
      });
    });
  }

  function showRewardedBreak() {
    hookNativeAdEventOnce();
    if (!window.NativeAdmobJSSDK || typeof window.NativeAdmobJSSDK.showRewarded !== "function") {
      return Promise.resolve({ rewardGranted: false });
    }
    return window.NativeAdmobJSSDK.showRewarded("reward_01").then(function (r) {
      return waitForNativeRewardedComplete(r.callbackId).then(function (res) {
        return { rewardGranted: !!(res && res.rewardGranted) };
      });
    });
  }

  function handle(payload) {
    payload = payload || {};
    if (payload.kind === "commercialBreak") return showCommercialBreak();
    if (payload.kind === "rewardedBreak") return showRewardedBreak();
    return Promise.reject(new Error("invalid_poki_ad_kind"));
  }

  window.addEventListener("message", function (event) {
    var data = event && event.data;
    if (!data || data.type !== "poki_ad_request") return;
    if (gameFrame && gameFrame.contentWindow && event.source !== gameFrame.contentWindow) return;
    if (data.requestId == null) return;

    handle(data.payload)
      .then(function (result) {
        sendResponse(event, data.requestId, true, result, null);
      })
      .catch(function (err) {
        sendResponse(event, data.requestId, false, {}, err && err.message ? err.message : String(err));
      });
  });
})();
