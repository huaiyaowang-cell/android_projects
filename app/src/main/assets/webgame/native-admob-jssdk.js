(function () {
    function makeRequest(payload) {
        return new Promise(function (resolve, reject) {
            try {
                if (!window.AndroidBridge || typeof window.AndroidBridge.requestAd !== "function") {
                    reject(new Error("AndroidBridge unavailable"));
                    return;
                }
                var callbackId = "cb_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
                payload.callbackId = callbackId;
                window.AndroidBridge.requestAd(JSON.stringify(payload));
                resolve({ callbackId: callbackId });
            } catch (e) {
                reject(e);
            }
        });
    }

    window.NativeAdmobJSSDK = {
        showInterstitial: function (placement) {
            return makeRequest({ action: "interstitial", placement: placement || "" });
        },
        showRewarded: function (placement) {
            return makeRequest({ action: "rewarded", placement: placement || "" });
        },
        showBanner: function (position, placement) {
            return makeRequest({ action: "banner_show", position: position || "bottom", placement: placement || "" });
        },
        hideBanner: function (placement) {
            return makeRequest({ action: "banner_hide", placement: placement || "" });
        }
    };
})();
