(function() {
  var _oeSetter = null;
  Object.defineProperty(window, "onerror", {
    configurable: true,
    set: function(fn) { _oeSetter = fn; },
    get: function() {
      return function(msg, url, line, col, err) {
        console.error("[poki-dl] onerror:", msg, url, line);
        return true;
      };
    }
  });

  var _pn = function() {};
  var _pp = function() { return Promise.resolve(); };

  var _loadingGone = false;
  var _loadingIds = [
    "loading-screen-container", "loader", "loading", "progress-container",
    "splash", "defold-progress", "unity-loading-bar", "application-splash-wrapper"
  ];

  function _hideLoading() {
    if (_loadingGone) return;
    var found = false;
    for (var i = 0; i < _loadingIds.length; i++) {
      var el = document.querySelector("#" + CSS.escape(_loadingIds[i]) + ":not([data-poki-placeholder])");
      if (el && el.parentElement) {
        el.parentElement.removeChild(el);
        found = true;
      }
    }
    try {
      if (typeof ProgressView !== "undefined"
          && ProgressView.progress
          && ProgressView.progress.parentElement
          && !ProgressView.progress.dataset.pokiPlaceholder) {
        ProgressView.progress.parentElement.removeChild(ProgressView.progress);
        found = true;
      }
    } catch (e) {}
    if (found) {
      _loadingGone = true;
      console.log("[poki-dl] loading overlay removed");
    }
  }

  var _hlTimer = setInterval(function() {
    _hideLoading();
    if (_loadingGone) clearInterval(_hlTimer);
  }, 2000);
  setTimeout(function() { clearInterval(_hlTimer); }, 30000);

  function _breakWithCb(cb) {
    if (typeof cb === "function") {
      try { cb(); } catch (e) {}
    }
    return Promise.resolve();
  }
  function _rewardedWithCb(cb) {
    if (typeof cb === "function") {
      try { cb(); } catch (e) {}
    }
    return Promise.resolve(true);
  }

  var _pokiBase = {
    init: function() {
      window.PokiSDK_OK = true;
      return Promise.resolve();
    },
    setDebug: _pn,
    setLogging: _pn,
    gameLoadingStart: _pn,
    gameLoadingFinished: function() {
      _pn("gameLoadingFinished");
      _hideLoading();
      return Promise.resolve();
    },
    gameLoadingProgress: _pn,
    gameInteractive: function() {
      _hideLoading();
      return Promise.resolve();
    },
    gameplayStart: function() {
      console.log("[poki-dl] gameplayStart");
      return Promise.resolve();
    },
    gameplayStop: function() {
      console.log("[poki-dl] gameplayStop");
      return Promise.resolve();
    },
    commercialBreak: _breakWithCb,
    rewardedBreak: _rewardedWithCb,
    measure: _pn,
    captureError: _pn,
    logError: _pn,
    customEvent: _pn,
    trackEvent: _pn,
    logEvent: _pn,
    happyTime: _pn,
    roundStart: _pn,
    roundEnd: _pn,
    displayAd: _pn,
    destroyAd: _pn,
    muteAd: _pn,
    requestAd: _pp,
    cancelAd: _pn,
    getURLParam: function() { return ""; },
    shareableURL: function() { return Promise.resolve(""); },
    isAdBlocked: function() { return false; },
    isPlayingOnPoki: function() { return false; },
    getLanguage: function() { return "en"; },
    getDevice: function() { return "desktop"; },
    sendHighscore: _pn,
    submitScore: _pn,
    setPlayerAge: _pn,
    setConsentString: _pn,
    setVolume: _pn,
    togglePlayerAdvertisingConsent: _pn,
    disableDOMChangeObservation: _pn,
    movePill: _pn,
    openExternalLink: _pn,
    playtestSetCanvas: _pn,
    playtestCaptureHtmlOnce: _pn,
    playtestCaptureHtmlForce: _pn,
    playtestCaptureHtmlOn: _pn,
    playtestCaptureHtmlOff: _pn
  };

  var _pokiStub = new Proxy(_pokiBase, {
    get: function(target, prop) {
      if (prop in target) return target[prop];
      if (prop === "then" || typeof prop === "symbol") return undefined;
      return _pn;
    }
  });

  try {
    Object.defineProperty(window, "PokiSDK", {
      value: _pokiStub,
      writable: false,
      configurable: false
    });
  } catch (e) {
    window.PokiSDK = _pokiStub;
  }
  console.log("[poki-dl] PokiSDK stub active (proxy)");

  var _origGBI = Document.prototype.getElementById;
  Document.prototype.getElementById = function(id) {
    var el = _origGBI.call(this, id);
    if (!el) {
      var sid = (id || "").toLowerCase();
      var isCanvasLike = sid.indexOf("canvas") >= 0 || sid === "gl" || sid === "webgl"
        || sid === "renderer" || sid === "three" || sid === "gl-canvas"
        || sid === "webgl-canvas";
      el = document.createElement(isCanvasLike ? "canvas" : "div");
      el.id = id;
      if (!isCanvasLike) el.style.display = "none";
      if (isCanvasLike) { el.width = 800; el.height = 600; el.style.display = "block"; }
      el.dataset.pokiPlaceholder = "1";
      if (document.body) document.body.appendChild(el);
    }
    return el;
  };
})();