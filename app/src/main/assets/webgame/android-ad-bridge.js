/**
 * Android WebView 原生广告桥接垫片。
 *
 * 背景：
 *   游戏的广告调用走 Poki Web 模式（index.html 父页 + iframe + AdSense adBreak）。
 *   但 Android 包在 MainActivity 里直连 game.html（无父 iframe），于是
 *   poki-ad-bridge-client.js 检测到 window.parent === window 后直接跳过，
 *   PokiSDK.rewardedBreak / commercialBreak 退化为 poki-sdk-stub 的空操作
 *   （立刻回调 success、任何广告都不弹）。原生侧已经建好了
 *   AndroidBridge.requestAd({action, callbackId}) + window.onNativeAdEvent 回传通道，
 *   但 H5 从未调用它，导致两端彻底断开。
 *
 * 本垫片：当检测到 Android WebView（存在 window.AndroidBridge.requestAd）时，
 * 把 PokiSDK.rewardedBreak / commercialBreak 转接到原生 BidderDesk/AdMob 广告，
 * 并把 window.onNativeAdEvent 的回传路由回 PokiSDK 的 promise / callback。
 *
 * 非 Android 环境（Poki web iframe、本地预览等）不激活，保持原有行为，零回归。
 */
(function () {
  "use strict";

  function isAndroidWebView() {
    try {
      return (
        typeof window.AndroidBridge !== "undefined" &&
        typeof window.AndroidBridge.requestAd === "function"
      );
    } catch (e) {
      return false;
    }
  }

  if (!isAndroidWebView()) {
    // 不在 Android WebView 内：交给 Poki 的 stub / iframe 桥接处理，不干预。
    return;
  }

  if (!window.PokiSDK) {
    console.warn("[android-ad-bridge] PokiSDK 未定义，跳过原生广告桥接");
    return;
  }

  var pending = Object.create(null);

  function genCallbackId() {
    return "adb_" + Date.now().toString(36) + "_" + Math.random().toString(36).slice(2);
  }

  // 兜底超时：原生侧自带 closed/failed 终态回调（rewarded 还有 120s 兜底），
  // 这里再加一层保险，避免极端情况下游戏永久等待。
  var SAFETY_TIMEOUT_MS = 150000;

  function finish(p, granted) {
    if (p.done) return;
    p.done = true;
    if (p.cb) {
      try { p.cb(); } catch (e) {}
      p.cb = null;
    }
    if (p.timer) {
      clearTimeout(p.timer);
      p.timer = null;
    }
    delete pending[p.callbackId];
    p.resolve(!!granted);
  }

  function requestNative(action, userCallback) {
    return new Promise(function (resolve) {
      var callbackId = genCallbackId();
      var p = {
        action: action,
        callbackId: callbackId,
        cb: (typeof userCallback === "function") ? userCallback : null,
        rewardGranted: false,
        done: false,
        resolve: resolve,
        timer: null
      };
      pending[callbackId] = p;
      console.log("[android-ad-bridge] 收到广告请求 action=" + action + " callbackId=" + callbackId);

      var send = function () {
        try {
          window.AndroidBridge.requestAd(JSON.stringify({
            action: action,
            callbackId: callbackId
          }));
        } catch (e) {
          console.error("[android-ad-bridge] requestAd 调用失败:", e);
          // 调用失败：回退到原始 Poki 逻辑（通常为空操作，保证游戏不卡死）
          fallbackToOrig(action, p);
          return;
        }
        p.timer = setTimeout(function () {
          console.warn("[android-ad-bridge] 超时兜底（" + action + "）");
          finish(p, p.rewardGranted);
        }, SAFETY_TIMEOUT_MS);
      };

      // 若原生通道在当前环境不可用，直接走原始实现
      if (typeof window.AndroidBridge === "undefined" || !window.AndroidBridge.requestAd) {
        fallbackToOrig(action, p);
        return;
      }
      send();
    });
  }

  function fallbackToOrig(action, p) {
    var orig = (action === "rewarded") ? _origRewarded : _origCommercial;
    // 交回原始实现处理；原始 stub 会自行调用游戏回调，故先清空 p.cb 避免 finish 重复回调
    var gameCb = p.cb;
    p.cb = null;
    try {
      if (typeof orig === "function") {
        var ret = orig(gameCb);
        if (ret && typeof ret.then === "function") {
          ret.then(function () { finish(p, p.rewardGranted); })
              .catch(function () { finish(p, false); });
          return;
        }
      }
    } catch (e) {}
    // 无法回退时，直接结束（不发放奖励），防止游戏挂起
    finish(p, false);
  }

  // 原生 → H5 回传入口。原生侧 sendAdEvent 会以
  // window.onNativeAdEvent(JSON.parse(eventString)) 调用本函数。
  function onNativeAdEvent(event) {
    if (!event || !event.callbackId) return;
    var p = pending[event.callbackId];
    if (!p) return;
    var phase = event.phase;
    var action = event.action;

    if (action === "rewarded") {
      if (phase === "reward") {
        // 原生确认已发奖：立即给游戏奖励，并等待 closed 再结束
        p.rewardGranted = true;
        if (p.cb) {
          try { p.cb(); } catch (e) {}
          p.cb = null;
        }
      } else if (phase === "closed") {
        finish(p, p.rewardGranted);
      } else if (phase === "failed") {
        finish(p, false);
      }
      // "opened" 忽略
      return;
    }

    if (action === "interstitial") {
      if (phase === "closed") {
        finish(p, false);
      } else if (phase === "failed") {
        finish(p, false);
      }
      // "opened" 忽略
      return;
    }
  }

  // 保留原始实现，供异常时回退
  var _origRewarded = PokiSDK.rewardedBreak;
  var _origCommercial = PokiSDK.commercialBreak;

  // 覆盖 PokiSDK 广告接口，转接原生桥
  PokiSDK.rewardedBreak = function (userCallback) {
    return requestNative("rewarded", userCallback);
  };
  PokiSDK.commercialBreak = function (userCallback) {
    return requestNative("interstitial", userCallback);
  };

  // 注册原生回传监听（仅在未定义时设置，避免覆盖其它实现）
  if (typeof window.onNativeAdEvent !== "function") {
    window.onNativeAdEvent = onNativeAdEvent;
  } else {
    var _prev = window.onNativeAdEvent;
    window.onNativeAdEvent = function (event) {
      try { _prev(event); } catch (e) {}
      onNativeAdEvent(event);
    };
  }

  console.log("[android-ad-bridge] 已激活：PokiSDK 广告转接原生 AndroidBridge");
})();
