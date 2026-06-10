/**
 * Talking Tom Gold Run — 父页面广告桥接（AdSense / adBreak；iframe id 为 ttgrGameFrame）
 */
(function () {
  "use strict";

  var gameFrame = document.getElementById("ttgrGameFrame");

  function isOfflineEnvironment() {
    var isFileProtocol = false;
    try {
      isFileProtocol = window.location && window.location.protocol === "file:";
    } catch (e) {}
    return isFileProtocol || (typeof navigator !== "undefined" && navigator.onLine === false);
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
    try {
      window.focus();
    } catch (e) {}
    try {
      if (document.body && typeof document.body.focus === "function") document.body.focus();
    } catch (e2) {}
  }

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

  window.commercialBreakBlockCOunt = 0;
  function showCommercialBreak(requestId) {
    if (window.commercialBreakBlockCOunt < 1) {
      window.commercialBreakBlockCOunt++;
      return resolve({ skipped: true, reason: "commercialBreakBlockCOunt" });
    }
    return waitForGoogleAdsReady(8000).then(function (sdkReady) {
      return new Promise(function (resolve) {
        if (isOfflineEnvironment()) {
          console.warn("[poki-ad-parent][插屏] 离线或 file:// 环境，跳过");
          return resolve({ skipped: true, reason: "offline" });
        }
        if (!sdkReady) {
          console.warn("[poki-ad-parent][插屏] Google Ads SDK 未就绪，跳过", {
            googleAdsReady: !!window.__googleAdsReady,
            hasAdBreak: typeof window.adBreak === "function",
          });
          return resolve({ skipped: true, reason: "sdk_not_ready" });
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
        console.log("[poki-ad-parent][插屏] 调用 adBreak", { requestId: requestId });

        window.adBreak({
          type: "browse",
          name: "talking-tom-gold-run-commercial",
          beforeAd: function () {
            focusForAd();
            notifyIframePhase(requestId, "poki_ad_before");
          },
          afterAd: function () {
            setGameFrameBlocked(false);
          },
          adBreakDone: function (placementInfo) {
            try { history.pushState(null, null, location.href); } catch (e) {}
            var breakStatus = placementInfo && placementInfo.breakStatus;
            console.log("[poki-ad-parent][插屏] 完成", breakStatus, placementInfo);
            finish({ breakStatus: breakStatus || null, skipped: false });
          },
        });
      });
    });
  }

  function showRewardedBreak(requestId) {
    return waitForGoogleAdsReady(8000).then(function (sdkReady) {
      return new Promise(function (resolve) {
        if (isOfflineEnvironment()) {
          console.warn("[poki-ad-parent][激励] 离线或 file:// 环境，跳过");
          return resolve({ rewardGranted: false, skipped: true, reason: "offline" });
        }
        if (!sdkReady) {
          console.warn("[poki-ad-parent][激励] Google Ads SDK 未就绪，跳过", {
            googleAdsReady: !!window.__googleAdsReady,
            hasAdBreak: typeof window.adBreak === "function",
          });
          return resolve({ rewardGranted: false, skipped: true, reason: "sdk_not_ready" });
        }

        var settled = false;
        var rewardEarnedByViewCallback = false;
        var pendingFalseTimer = null;

        function finish(granted, extra) {
          if (settled) return;
          settled = true;
          setGameFrameBlocked(false);
          try {
            if (pendingFalseTimer) clearTimeout(pendingFalseTimer);
          } catch (e) {}
          pendingFalseTimer = null;
          try { history.pushState(null, null, location.href); } catch (e2) {}
          var result = { rewardGranted: !!granted, skipped: false };
          if (extra && typeof extra === "object") {
            for (var k in extra) {
              if (Object.prototype.hasOwnProperty.call(extra, k)) result[k] = extra[k];
            }
          }
          resolve(result);
        }

        function tryFinishAfterDone(placementInfo) {
          if (settled) return;
          var st = placementInfo && placementInfo.breakStatus;
          var viewedByStatus = st != null && String(st).toLowerCase() === "viewed";
          if (viewedByStatus || rewardEarnedByViewCallback) {
            finish(true, { breakStatus: st || "viewed" });
            return;
          }
          try {
            if (pendingFalseTimer) clearTimeout(pendingFalseTimer);
          } catch (e) {}
          pendingFalseTimer = setTimeout(function () {
            pendingFalseTimer = null;
            if (settled) return;
            finish(rewardEarnedByViewCallback, { breakStatus: st || null });
          }, 150);
        }

        focusForAd();
        setGameFrameBlocked(true);
        console.log("[poki-ad-parent][激励] 调用 adBreak", { requestId: requestId });

        window.adBreak({
          type: "reward",
          name: "talking-tom-gold-run-reward",
          beforeAd: function () {
            focusForAd();
            notifyIframePhase(requestId, "poki_ad_reward_start");
          },
          afterAd: function () {
            setGameFrameBlocked(false);
          },
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
            if (!settled) finish(false, { breakStatus: "dismissed" });
          },
          adViewed: function () {
            rewardEarnedByViewCallback = true;
            try {
              if (pendingFalseTimer) {
                clearTimeout(pendingFalseTimer);
                pendingFalseTimer = null;
              }
            } catch (e) {}
            if (!settled) finish(true, { breakStatus: "viewed" });
          },
          adBreakDone: function (placementInfo) {
            console.log("[poki-ad-parent][激励] 完成", placementInfo && placementInfo.breakStatus, placementInfo);
            tryFinishAfterDone(placementInfo);
          },
        });
      });
    });
  }

  function handle(payload, requestId) {
    payload = payload || {};
    if (payload.kind === "commercialBreak") return showCommercialBreak(requestId);
    if (payload.kind === "rewardedBreak") return showRewardedBreak(requestId);
    return Promise.reject(new Error("invalid_poki_ad_kind"));
  }

  function dispatchAdRequest(payload, requestId, sourceWindow) {
    var fakeEvent = {
      source: sourceWindow,
    };
    handle(payload, requestId)
      .then(function (result) {
        sendResponse(fakeEvent, requestId, true, result, null);
      })
      .catch(function (err) {
        setGameFrameBlocked(false);
        sendResponse(fakeEvent, requestId, false, {}, err && err.message ? err.message : String(err));
      });
  }

  window.__ttgrHandleAdRequest = function (payload, requestId, sourceWindow) {
    if (!sourceWindow) return;
    console.log("[poki-ad-parent] 收到 iframe 广告请求(直连)", payload && payload.kind, requestId);
    dispatchAdRequest(payload, requestId, sourceWindow);
  };

  window.addEventListener("message", function (event) {
    var data = event && event.data;
    if (!data || data.type !== "poki_ad_request") return;
    if (gameFrame && gameFrame.contentWindow && event.source !== gameFrame.contentWindow) return;
    if (data.requestId == null) return;

    console.log("[poki-ad-parent] 收到 iframe 广告请求", data.payload && data.payload.kind, data.requestId);

    dispatchAdRequest(data.payload, data.requestId, event.source);
  });
})();
