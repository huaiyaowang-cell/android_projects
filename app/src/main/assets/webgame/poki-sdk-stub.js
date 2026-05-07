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

  window.PokiSDK = {
    init: _pp,
    gameplayStart: _pn,
    gameplayStop: _pn,
    commercialBreak: function() {
      console.log('commercialBreak');
      return Promise.resolve(true);
    },
    rewardedBreak: function(arg) {
      if (typeof arg === "function") {
        try { arg(); } catch (e) {}
      } else if (arg && typeof arg === "object" && typeof arg.onStart === "function") {
        try { arg.onStart(); } catch (e) {}
      }
      console.log('rewardedBreak', arg);
      return new Promise(function(resolve) {
        setTimeout(function() { resolve(true); }, 5000);
      });
    },
    displayAd: _pn,
    destroyAd: _pn,
    setDebug: _pn,
    getURLParam: function() { return ""; },
    shareableURL: function() { return Promise.resolve(""); },
    isAdBlocked: function() { return false; },
    gameLoadingStart: _pn,
    gameLoadingFinished: _hideLoading,
    gameLoadingProgress: _pn,
    gameInteractive: _hideLoading,
    customEvent: _pn,
    happyTime: _pn,
    logError: _pn,
    roundStart: _pn,
    roundEnd: _pn,
    muteAd: _pn,
    sendHighscore: _pn,
    togglePlayerAdvertisingConsent: _pn,
    disableDOMChangeObservation: _pn
  };
  console.log("[poki-dl] PokiSDK stub active");

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

  window.__unitySend = function(gameObjectName, methodName, param) {
    var ug = window.unityGame;
    if (!ug || typeof ug.SendMessage !== "function") {
      console.warn("[poki-dl] unityGame 未就绪");
      return false;
    }
    try {
      if (param === undefined) ug.SendMessage(gameObjectName, methodName);
      else ug.SendMessage(gameObjectName, methodName, param);
      return true;
    } catch (e) {
      console.error("[poki-dl] SendMessage 失败:", e);
      return false;
    }
  };

  var _spyInstalled = false;
  function _installSendMessageSpy() {
    if (_spyInstalled) return;
    var ug = window.unityGame;
    if (!ug || typeof ug.SendMessage !== "function") return;
    _spyInstalled = true;
    var orig = ug.SendMessage.bind(ug);
    ug.SendMessage = function(go, method, param) {
      if (window.__UNITY_SPY_SENDMESSAGE) {
        console.log("[Unity SendMessage]", go, method, param);
      }
      return orig(go, method, param);
    };
  }
  var _spyTimer = setInterval(function() {
    _installSendMessageSpy();
    if (_spyInstalled) clearInterval(_spyTimer);
  }, 200);
  setTimeout(function() { clearInterval(_spyTimer); }, 120000);

  window.__installUnitySendMessageSpy = function() {
    window.__UNITY_SPY_SENDMESSAGE = true;
    _installSendMessageSpy();
    console.log("[poki-dl] SendMessage 监听已开，过关时看控制台 [Unity SendMessage]");
  };

  var _HACK_OBJECTS = [
    "LevelGameManager",
    "GameManager",
    "GameController",
    "PokiUnitySDK",
    "(singleton) PokiUnitySDK"
  ];
  var _HACK_METHODS = [
    "LevelComplete", "OnLevelComplete", "NextLevel", "WinLevel", "Win", "OnWin",
    "CompleteLevel", "PassLevel", "SkipLevel", "ForceWin", "ShowWin", "OpenWin",
    "GoToNextLevel", "Continue", "Success", "Victory", "LevelWin", "OnSuccess",
    "FinishLevel", "Complete", "Next", "OnNext", "Restart", "Replay"
  ];

  function _tapGameUiCorner() {
    var el = document.querySelector("#game") || document.querySelector("canvas");
    if (!el) return false;
    var r = el.getBoundingClientRect();
    var x = r.left + r.width * 0.88;
    var y = r.top + r.height * 0.12;
    var ev = { bubbles: true, clientX: x, clientY: y, button: 0, buttons: 1 };
    try {
      el.dispatchEvent(new PointerEvent("pointerdown", ev));
      el.dispatchEvent(new MouseEvent("mousedown", ev));
      el.dispatchEvent(new PointerEvent("pointerup", ev));
      el.dispatchEvent(new MouseEvent("mouseup", ev));
      el.dispatchEvent(new MouseEvent("click", ev));
      return true;
    } catch (e) {
      return false;
    }
  }

  window.hackPassLevel = function(options) {
    options = options || {};
    var ug = window.unityGame;
    if (!ug || typeof ug.SendMessage !== "function") {
      console.warn("[poki-dl] hackPassLevel: 等 Unity 加载完成后再执行");
      return { ok: false, reason: "unityGame not ready" };
    }
    _installSendMessageSpy();
    var objects = options.objects || (options.brute ? _HACK_OBJECTS : [
      "LevelGameManager", "GameManager", "GameController"
    ]);
    var methods = options.methods || (options.brute ? _HACK_METHODS : [
      "LevelComplete", "OnLevelComplete", "NextLevel", "Win", "CompleteLevel",
      "PassLevel", "SkipLevel", "Victory", "Next", "Success"
    ]);
    var tried = [];
    var verbose = !!options.verbose;
    for (var i = 0; i < objects.length; i++) {
      for (var j = 0; j < methods.length; j++) {
        var go = objects[i];
        var me = methods[j];
        try {
          ug.SendMessage(go, me);
          tried.push(go + "." + me + "()");
          if (verbose) console.log("[hackPassLevel]", go, me);
        } catch (err) {
          tried.push(go + "." + me + " ERROR: " + err);
        }
      }
    }
    var tap = false;
    if (options.tapUi !== false) {
      tap = _tapGameUiCorner();
    }
    console.log("[poki-dl] hackPassLevel 已尝试 SendMessage 组合数:", tried.length, "tapUi:", tap);
    return { ok: true, tried: tried, tapUi: tap, hint: "若无效：控制台执行 __installUnitySendMessageSpy() 后刷新，正常过关一次看真实 SendMessage；或 hackPassLevel({ brute: true })" };
  };
})();