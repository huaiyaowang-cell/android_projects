/**
 * Happy Glass — iframe 内游戏侧广告桥接
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

  var origCommercial =
    typeof PokiSDK.commercialBreak === "function"
      ? PokiSDK.commercialBreak.bind(PokiSDK)
      : null;

  PokiSDK.commercialBreak = function () {
    return postRequest({ kind: "commercialBreak" }).catch(function (err) {
      console.warn("[poki-ad-client] commercialBreak 失败，回退本地:", err);
      return origCommercial ? origCommercial() : Promise.resolve();
    });
  };

  PokiSDK.rewardedBreak = function () {
    return postRequest({ kind: "rewardedBreak" })
      .then(function (result) {
        return !!(result && result.rewardGranted);
      })
      .catch(function (err) {
        console.warn("[poki-ad-client] rewardedBreak 失败，回退本地:", err);
        return Promise.resolve(false);
      });
  };
})();

