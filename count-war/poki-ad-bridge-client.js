/**
 * Count War — iframe 广告桥接（happy-glass + Unity window.commercialBreak / rewardedBreak）
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
  if (!parentWin || parentWin === window) {
    console.log("[poki-ad-client] 非 iframe 环境，使用本地 stub 广告逻辑");
    return;
  }

  var pending = Object.create(null);

  function genRequestId() {
    return Date.now().toString(36) + "_" + Math.random().toString(36).slice(2);
  }

  function postRequest(payload) {
    var requestId = genRequestId();
    var kind = payload && payload.kind;
    if (kind === "commercialBreak") {
      console.log("[count-war][插屏] iframe → 父页 postMessage", { requestId: requestId, kind: kind });
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
