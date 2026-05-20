/**
 * Count War — 父页面广告桥接（AdSense / adBreak，与 happy-glass 同款；iframe id 为 cwGameFrame）
 */
(function () {
  "use strict";

  var gameFrame = document.getElementById("cwGameFrame");

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
      if (isOfflineEnvironment() || !window.__googleAdsReady) return resolve({});
      if (typeof window.adBreak !== "function") return resolve({});
      window.adBreak({
        type: "browse",
        name: "count-war-commercial",
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
      if (isOfflineEnvironment() || !window.__googleAdsReady) return resolve({ rewardGranted: false });
      if (typeof window.adBreak !== "function") return resolve({ rewardGranted: false });

      var settled = false;
      var rewardEarnedByViewCallback = false;
      var pendingFalseTimer = null;

      function finish(granted) {
        if (settled) return;
        settled = true;
        try {
          if (pendingFalseTimer) clearTimeout(pendingFalseTimer);
        } catch (e) {}
        pendingFalseTimer = null;
        try { history.pushState(null, null, location.href); } catch (e2) {}
        resolve({ rewardGranted: !!granted });
      }

      function tryFinishAfterDone(placementInfo) {
        if (settled) return;
        var st = placementInfo && placementInfo.breakStatus;
        var viewedByStatus = st != null && String(st).toLowerCase() === "viewed";
        if (viewedByStatus || rewardEarnedByViewCallback) {
          finish(true);
          return;
        }
        try {
          if (pendingFalseTimer) clearTimeout(pendingFalseTimer);
        } catch (e) {}
        pendingFalseTimer = setTimeout(function () {
          pendingFalseTimer = null;
          if (settled) return;
          finish(rewardEarnedByViewCallback);
        }, 150);
      }

      window.adBreak({
        type: "reward",
        name: "count-war-reward",
        beforeAd: function () {},
        afterAd: function () {},
        beforeReward: function (showAdFn) {
          if (showAdFn) {
            try {
              showAdFn();
            } catch (eShow) {}
          }
        },
        adDismissed: function () {
          try {
            if (pendingFalseTimer) {
              clearTimeout(pendingFalseTimer);
              pendingFalseTimer = null;
            }
          } catch (e) {}
          if (!settled) finish(false);
        },
        adViewed: function () {
          rewardEarnedByViewCallback = true;
          try {
            if (pendingFalseTimer) {
              clearTimeout(pendingFalseTimer);
              pendingFalseTimer = null;
            }
          } catch (e) {}
          if (!settled) finish(true);
        },
        adBreakDone: function (placementInfo) {
          tryFinishAfterDone(placementInfo);
        },
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
      .then(function (result) { sendResponse(event, data.requestId, true, result, null); })
      .catch(function (err) {
        sendResponse(event, data.requestId, false, {}, err && err.message ? err.message : String(err));
      });
  });
})();
