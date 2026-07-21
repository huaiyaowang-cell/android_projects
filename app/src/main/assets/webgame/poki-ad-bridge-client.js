/**
 * Marina Club Rush — iframe / APK WebView 广告桥接（Unity commercialBreak / rewardedBreak）
 */
(function () {
  "use strict";

  if (!window.PokiSDK) {
    console.warn("[poki-ad-client] PokiSDK 未定义，跳过桥接");
    return;
  }

  var parentWin;
  try {
    parentWin = window.parent;
  } catch (e) {
    parentWin = null;
  }
  function rememberPokiBridge(name) {
    if (name == null || name === "") return;
    var s = String(name);
    window.pokiBridge = s;
    window.__pokiBridgeName = s;
  }

  function unityRewardedParam(granted) {
    return granted === true || granted === "true" || granted === "True" ? "true" : "false";
  }

  function getRewardTargets() {
    var list = [];
    if (window.pokiBridge) list.push(window.pokiBridge);
    if (window.__pokiBridgeName && list.indexOf(window.__pokiBridgeName) < 0) {
      list.push(window.__pokiBridgeName);
    }
    ["PokiUnitySDK", "(singleton) PokiUnitySDK"].forEach(function (name) {
      if (list.indexOf(name) < 0) list.push(name);
    });
    return list;
  }

  function trySendUnityRewarded(granted) {
    if (!window.unityGame || typeof window.unityGame.SendMessage !== "function") {
      return false;
    }
    var s = unityRewardedParam(granted);
    var targets = getRewardTargets();
    for (var i = 0; i < targets.length; i++) {
      try {
        window.unityGame.SendMessage(targets[i], "rewardedBreakCompleted", s);
        rememberPokiBridge(targets[i]);
        return true;
      } catch (eTry) {}
    }
    return false;
  }

  function notifyUnityRewarded(granted) {
    var attempt = 0;
    function tick() {
      attempt++;
      if (trySendUnityRewarded(granted)) return;
      if (attempt < 30) setTimeout(tick, 100);
    }
    setTimeout(tick, 0);
  }

  if (!parentWin || parentWin === window) {
    if (window.AndroidBridge && window.NativeAdmobJSSDK) {
      applyNativeAndroidBridge();
      console.log("[poki-ad-client] APK WebView 直连模式，使用 Native 广告");
      return;
    }
    console.log("[poki-ad-client] 非 iframe 环境，使用本地 stub 广告逻辑");
    return;
  }

  function applyNativeAndroidBridge() {
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
        function finish(ok, err) {
          if (done) return;
          done = true;
          delete pendingNative[callbackId];
          if (ok) resolve({});
          else reject(err || new Error("native_ad_failed"));
        }
        var timeout = setTimeout(function () {
          finish(false, new Error("native_ad_timeout"));
        }, 120000);
        pendingNative[callbackId] = function (ev) {
          if (!ev || ev.action !== action) return;
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
          if (ev.phase === "closed" || ev.phase === "failed") finishSuccess();
        };
      });
    }

    function showCommercialBreak() {
      hookNativeAdEventOnce();
      if (!window.NativeAdmobJSSDK) return Promise.resolve({});
      return window.NativeAdmobJSSDK.showInterstitial("interstitial_01").then(function (r) {
        return waitForNativeComplete(r.callbackId, "interstitial");
      }).catch(function () { return {}; });
    }

    function showRewardedBreak(arg) {
      hookNativeAdEventOnce();
      if (!window.NativeAdmobJSSDK) return Promise.resolve(false);
      return window.NativeAdmobJSSDK.showRewarded("reward_01").then(function (r) {
        return waitForNativeRewardedComplete(r.callbackId).then(function (res) {
          var granted = !!(res && res.rewardGranted);
          if (typeof arg === "function") {
            try { arg(granted); } catch (e) {}
          }
          return granted;
        });
      }).catch(function () { return false; });
    }

    PokiSDK.commercialBreak = showCommercialBreak;
    PokiSDK.gameplayStop = function () {
      console.log("[marina-club-rush][插屏] gameplayStop → commercialBreak");
      return showCommercialBreak();
    };
    PokiSDK.rewardedBreak = showRewardedBreak;
    window.commercialBreak = showCommercialBreak;
    window.rewardedBreak = function (arg) {
      return showRewardedBreak(arg).then(function (granted) {
        notifyUnityRewarded(granted);
        return granted;
      });
    };
  }

  var pending = Object.create(null);

  function genRequestId() {
    return Date.now().toString(36) + "_" + Math.random().toString(36).slice(2);
  }

  function postRequest(payload) {
    var requestId = genRequestId();
    var kind = payload && payload.kind;
    if (kind === "commercialBreak") {
      console.log("[marina-club-rush][插屏] iframe → 父页 postMessage", { requestId: requestId, kind: kind });
    }
    return new Promise(function (resolve, reject) {
      pending[requestId] = { resolve: resolve, reject: reject };
      try {
        parentWin.postMessage(
          { type: "poki_ad_request", requestId: requestId, payload: payload || {} },
          "*"
        );
      } catch (e) {
        delete pending[requestId];
        reject(e);
      }
    });
  }

  window.addEventListener("message", function (event) {
    var data = event && event.data;
    if (!data || data.type !== "poki_ad_response") return;
    var p = pending[data.requestId];
    if (!p) return;
    delete pending[data.requestId];
    if (data.ok) p.resolve(data.result || {});
    else p.reject(new Error(data.error || "poki_ad_request_failed"));
  });

  function runRewardedBreak(arg) {
    return postRequest({ kind: "rewardedBreak" })
      .then(function (result) {
        var granted = !!(result && result.rewardGranted);
        if (typeof arg === "function") {
          try { arg(granted); } catch (e) {}
        } else if (arg && typeof arg === "object") {
          if (typeof arg.onComplete === "function") {
            try { arg.onComplete(granted); } catch (e2) {}
          } else if (typeof arg.finished === "function") {
            try { arg.finished(granted); } catch (e3) {}
          }
        }
        return granted;
      })
      .catch(function (err) {
        console.warn("[poki-ad-client] rewardedBreak 失败:", err);
        return false;
      });
  }

  var origCommercial =
    typeof PokiSDK.commercialBreak === "function"
      ? PokiSDK.commercialBreak.bind(PokiSDK)
      : null;
  var origGameplayStop =
    typeof PokiSDK.gameplayStop === "function"
      ? PokiSDK.gameplayStop.bind(PokiSDK)
      : null;
  var commercialBreakInFlight = false;

  function applyBridge() {
    PokiSDK.commercialBreak = function () {
      return postRequest({ kind: "commercialBreak" }).catch(function (err) {
        console.warn("[poki-ad-client] commercialBreak 失败，回退本地:", err);
        return origCommercial ? origCommercial() : Promise.resolve();
      });
    };

    PokiSDK.gameplayStop = function () {
      if (origGameplayStop) {
        try {
          origGameplayStop();
        } catch (eStop) {}
      }
      if (commercialBreakInFlight) {
        return Promise.resolve();
      }
      console.log("[count-war][插屏] 关卡结束 gameplayStop → commercialBreak");
      commercialBreakInFlight = true;
      return PokiSDK.commercialBreak().finally(function () {
        commercialBreakInFlight = false;
      });
    };

    PokiSDK.rewardedBreak = function (arg) {
      return runRewardedBreak(arg);
    };

    window.commercialBreak = function () {
      return PokiSDK.commercialBreak();
    };

    window.rewardedBreak = function () {
      var arg = arguments[0];
      return runRewardedBreak(arg)
        .then(function (granted) {
          notifyUnityRewarded(granted);
          return granted;
        })
        .catch(function (err) {
          console.warn("[poki-ad-client] window.rewardedBreak 失败:", err);
          notifyUnityRewarded(false);
          return false;
        });
    };

    window.initPokiBridge = function (bridgeName) {
      rememberPokiBridge(bridgeName);
    };
  }

  applyBridge();

  var hookTimer = setInterval(function () {
    applyBridge();
  }, 400);
  setTimeout(function () {
    clearInterval(hookTimer);
  }, 180000);

  console.log(
    "[poki-ad-client] 已桥接（gameplayStop→commercialBreak、commercialBreak/rewardedBreak → 父页）"
  );
})();
