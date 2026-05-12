/**
 * 与 Android {@code AndroidBridge.requestAd} 的唯一收口；{@code window.onNativeAdEvent} 由本文件安装路由并分发给未完成 Promise。
 * 与 {@code assets/webgame/native-admob-jssdk.js} 保持一致，便于双端维护。
 */
(function () {
    "use strict";

    var pending = Object.create(null);

    function isBridgeAvailable() {
        return !!(window.AndroidBridge && typeof window.AndroidBridge.requestAd === "function");
    }

    function routePending(event) {
        if (!event || !event.callbackId) {
            return;
        }
        var entry = pending[event.callbackId];
        if (!entry) {
            return;
        }
        var action = entry.action;

        if (event.phase === "reward") {
            entry.rewarded = true;
            return;
        }

        if (event.phase === "failed") {
            delete pending[event.callbackId];
            var msg = event.error && event.error.message ? event.error.message : "native_ad_failed";
            entry.reject(new Error(msg));
            return;
        }

        if (action === "rewarded" && event.phase === "closed") {
            delete pending[event.callbackId];
            entry.resolve({ rewardGranted: !!entry.rewarded });
            return;
        }

        if (action === "interstitial" && event.phase === "closed") {
            delete pending[event.callbackId];
            entry.resolve({});
            return;
        }

        if (action === "banner_show" && event.phase === "opened") {
            delete pending[event.callbackId];
            entry.resolve({});
            return;
        }

        if (action === "banner_hide" && event.phase === "closed") {
            delete pending[event.callbackId];
            entry.resolve({});
            return;
        }

        if (action === "rebuild_layers" && event.phase === "closed") {
            delete pending[event.callbackId];
            entry.resolve({});
            return;
        }

        if (action === "show_interstitial_in_layers" && event.phase === "opened") {
            delete pending[event.callbackId];
            entry.resolve({});
            return;
        }
    }

    (function installNativeAdEventRouter() {
        var prev = window.onNativeAdEvent;
        window.onNativeAdEvent = function (event) {
            try {
                routePending(event);
            } catch (e) {}
            if (typeof prev === "function") {
                try {
                    prev(event);
                } catch (e2) {}
            }
        };
    })();

    function requestNativeAd(payload) {
        payload = payload || {};
        return new Promise(function (resolve, reject) {
            if (!isBridgeAvailable()) {
                reject(new Error("AndroidBridge unavailable"));
                return;
            }
            var callbackId = "cb_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
            payload.callbackId = callbackId;
            pending[callbackId] = {
                action: payload.action,
                resolve: resolve,
                reject: reject,
                rewarded: false
            };
            try {
                window.AndroidBridge.requestAd(JSON.stringify(payload));
            } catch (e) {
                delete pending[callbackId];
                reject(e);
            }
        });
    }

    window.NativeAdmobJSSDK = {
        isAvailable: isBridgeAvailable,
        requestNativeAd: requestNativeAd,
        showInterstitial: function (placement) {
            return requestNativeAd({
                action: "interstitial",
                placement: placement || "",
                position: "bottom"
            });
        },
        showRewarded: function (placement) {
            return requestNativeAd({
                action: "rewarded",
                placement: placement || "",
                position: "bottom"
            });
        },
        showBanner: function (position, placement) {
            return requestNativeAd({
                action: "banner_show",
                position: position || "bottom",
                placement: placement || ""
            });
        },
        hideBanner: function (placement) {
            return requestNativeAd({
                action: "banner_hide",
                placement: placement || "",
                position: "bottom"
            });
        },
        rebuildLayers: function () {
            return requestNativeAd({
                action: "rebuild_layers",
                placement: "",
                position: "bottom"
            });
        },
        /**
         * 通知每一个下层 WebView 播放页内开屏类广告：每层依次尝试
         * showOpenAd、showSplashAd、playOpenAd、showInterstitialAd（仅调用第一个存在的）。
         */
        showInterstitialInLayers: function () {
            return requestNativeAd({
                action: "show_interstitial_in_layers",
                placement: "",
                position: "bottom"
            });
        }
    };
})();
