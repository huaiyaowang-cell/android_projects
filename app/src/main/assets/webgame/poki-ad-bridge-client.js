/**
 * Talking Tom Gold Run — iframe 广告桥接（PlayCanvas commercialBreak / rewardedBreak）
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
  var commercialBreakInFlight = false;

  function genRequestId() {
    return Date.now().toString(36) + "_" + Math.random().toString(36).slice(2);
  }

  function focusParent() {
    try {
      if (parentWin && typeof parentWin.focus === "function") parentWin.focus();
    } catch (e) {}
  }

  function postRequest(payload, hooks) {
    var requestId = genRequestId();
    var kind = payload && payload.kind;
    if (kind === "commercialBreak") {
      console.log("[talking-tom-gold-run][插屏] iframe → 父页 postMessage", { requestId: requestId, kind: kind });
    } else if (kind === "rewardedBreak") {
      console.log("[talking-tom-gold-run][激励] iframe → 父页 postMessage", { requestId: requestId, kind: kind });
    }
    return new Promise(function (resolve, reject) {
      pending[requestId] = {
        resolve: resolve,
        reject: reject,
        beforeFn: hooks && hooks.beforeFn,
        onStart: hooks && hooks.onStart,
      };
      focusParent();
      try {
        if (typeof parentWin.__ttgrHandleAdRequest === "function") {
          parentWin.__ttgrHandleAdRequest(payload || {}, requestId, window);
        } else {
          parentWin.postMessage(
            { type: "poki_ad_request", requestId: requestId, payload: payload || {} },
            "*"
          );
        }
      } catch (e) {
        delete pending[requestId];
        reject(e);
      }
    });
  }

  window.addEventListener("message", function (event) {
    var data = event && event.data;
    if (!data) return;

    if (data.type === "poki_ad_before") {
      var beforeEntry = pending[data.requestId];
      if (beforeEntry && typeof beforeEntry.beforeFn === "function") {
        try {
          beforeEntry.beforeFn();
        } catch (eBefore) {}
      }
      return;
    }

    if (data.type === "poki_ad_reward_start") {
      var startEntry = pending[data.requestId];
      if (startEntry && typeof startEntry.onStart === "function") {
        try {
          startEntry.onStart();
        } catch (eStart) {}
      }
      return;
    }

    if (data.type !== "poki_ad_response") return;
    var p = pending[data.requestId];
    if (!p) return;
    delete pending[data.requestId];
    console.log("[talking-tom-gold-run][广告] 父页 ← 响应", {
      requestId: data.requestId,
      ok: data.ok,
      result: data.result,
      error: data.error,
    });
    if (data.ok) p.resolve(data.result || {});
    else p.reject(new Error(data.error || "poki_ad_request_failed"));
  });

  function runRewardedBreak(arg) {
    var onStart =
      arg && typeof arg === "object" && typeof arg.onStart === "function" ? arg.onStart : null;
    return postRequest({ kind: "rewardedBreak" }, { onStart: onStart })
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

  function applyBridge() {
    PokiSDK.commercialBreak = function (beforeFn) {
      if (commercialBreakInFlight) {
        return Promise.resolve();
      }
      commercialBreakInFlight = true;
      return postRequest({ kind: "commercialBreak" }, { beforeFn: beforeFn })
        .catch(function (err) {
          console.warn("[poki-ad-client] commercialBreak 失败，回退本地:", err);
          return origCommercial ? origCommercial(beforeFn) : Promise.resolve();
        })
        .finally(function () {
          commercialBreakInFlight = false;
        });
    };
    PokiSDK.commercialBreak.__pokiAdHook = true;

    PokiSDK.rewardedBreak = function (arg) {
      return runRewardedBreak(arg);
    };
    PokiSDK.rewardedBreak.__pokiAdHook = true;

    window.commercialBreak = function (beforeFn) {
      return PokiSDK.commercialBreak(beforeFn);
    };
    window.commercialBreak.__pokiAdHook = true;

    window.rewardedBreak = function () {
      return PokiSDK.rewardedBreak.apply(PokiSDK, arguments);
    };
    window.rewardedBreak.__pokiAdHook = true;

    window.initPokiBridge = function (bridgeName) {
      if (bridgeName != null && bridgeName !== "") {
        window.pokiBridge = String(bridgeName);
        window.__pokiBridgeName = String(bridgeName);
      }
    };
    window.initPokiBridge.__pokiAdHook = true;
  }

  applyBridge();
  setInterval(applyBridge, 400);

  console.log(
    "[poki-ad-client] 已桥接（commercialBreak/rewardedBreak → 父页；beforeFn 在 beforeAd 时触发）"
  );
})();
