/**
 * RequestGuard — 拦截 fetch / XMLHttpRequest / sendBeacon
 * 默认阻断部分广告/统计域名后缀，其余放行。
 */
(function (global) {
  "use strict";

  var AD_HOST_SUFFIXES = [
    "poki.com",
    "api.gameanalytics.com",
    "leveldata.poki.io",
    "game-cdn.poki.com",
    "game-cdn.poki.io",
    "game-cdn.poki.net",
    "game-cdn.poki.org",
  ];

  var blockedHostSuffixes = AD_HOST_SUFFIXES.slice();

  function parseUrl(url, base) {
    try {
      return new URL(url, base || global.location.href);
    } catch (e) {
      return null;
    }
  }

  function toAbsoluteHref(url) {
    var u = parseUrl(String(url));
    return u ? u.href : String(url);
  }

  function isHostnameBlocked(hostname) {
    if (!hostname) return false;
    var h = String(hostname).toLowerCase();
    for (var i = 0; i < blockedHostSuffixes.length; i++) {
      var s = String(blockedHostSuffixes[i]).toLowerCase();
      if (!s) continue;
      if (h === s || h.endsWith("." + s)) return true;
    }
    return false;
  }

  function defaultMiddleware(url) {
    var u = parseUrl(String(url));
    if (!u) return true;

    if (u.protocol === "blob:" || u.protocol === "data:" || u.protocol === "about:")
      return true;

    if (isHostnameBlocked(u.hostname)) return false;

    return true;
  }

  var config = {
    middleware: defaultMiddleware,
    debug: false,
    logAllRequests: false,
  };

  var pre = global.__REQUEST_GUARD_CONFIG__;
  if (pre && typeof pre === "object") {
    if (typeof pre.middleware === "function") config.middleware = pre.middleware;
    if (pre.debug) config.debug = !!pre.debug;
    if (pre.logAllRequests) config.logAllRequests = !!pre.logAllRequests;
    if (pre.blockedHostSuffixes && pre.blockedHostSuffixes.length)
      blockedHostSuffixes = pre.blockedHostSuffixes.slice();
  }

  var _origFetch = global.fetch ? global.fetch.bind(global) : null;
  var _origSendBeacon =
    global.navigator && typeof global.navigator.sendBeacon === "function"
      ? global.navigator.sendBeacon.bind(global.navigator)
      : null;

  var XHR = global.XMLHttpRequest;
  var _origXhrOpen = XHR && XHR.prototype.open;
  var _origXhrSend = XHR && XHR.prototype.send;

  function logBlocked(url, kind, extra) {
    try {
      if (config.debug && extra != null) {
        console.warn("[RequestGuard] 已拦截", { url: url, kind: kind, extra: extra });
      } else {
        console.warn("[RequestGuard] 已拦截", toAbsoluteHref(url), "(" + kind + ")");
      }
    } catch (e) {}
  }

  function logRequestLine(kind, url, allowed, extra) {
    if (!config.logAllRequests) return;
    try {
      var abs = toAbsoluteHref(url);
      var line = { kind: kind, url: abs, allowed: allowed };
      if (config.debug && extra != null) line.extra = extra;
      console.log("[RequestGuard] 请求", line);
    } catch (e) {}
  }

  function invokeMiddleware(urlString, kind, extra) {
    if (urlString == null || urlString === "") return false;
    var s = String(urlString);
    try {
      var fn = config.middleware;
      if (typeof fn !== "function") return false;
      var ok = fn(s) === true;
      logRequestLine(kind, s, ok, config.debug ? extra : null);
      if (ok) return true;
      if (!config.logAllRequests) logBlocked(s, kind, config.debug ? extra : null);
      return false;
    } catch (e) {
      logRequestLine(kind, s, false, config.debug ? { error: String(e) } : null);
      if (!config.logAllRequests) logBlocked(s, "middleware_error", e);
      return false;
    }
  }

  function blockError(url, kind) {
    var err = new Error("[RequestGuard] request blocked: " + kind + " " + url);
    err.name = "RequestGuardError";
    return err;
  }

  function installFetch() {
    if (!_origFetch || global.fetch.__requestGuardPatched) return;
    global.fetch = function (input, init) {
      var url =
        typeof input === "string"
          ? input
          : input && typeof input.url === "string"
            ? input.url
            : String(input);

      if (!invokeMiddleware(url, "fetch", { init: init })) {
        return Promise.reject(blockError(url, "fetch"));
      }
      return _origFetch(input, init);
    };
    global.fetch.__requestGuardPatched = true;
  }

  function installXHR() {
    if (!_origXhrOpen || !_origXhrSend || XHR.prototype.open.__requestGuardPatched) return;

    XHR.prototype.open = function (method, url) {
      this.__requestGuardUrl = url;
      this.__requestGuardMethod = method;
      return _origXhrOpen.apply(this, arguments);
    };

    XHR.prototype.send = function (body) {
      var url = this.__requestGuardUrl;
      if (
        !invokeMiddleware(url, "xhr", {
          method: this.__requestGuardMethod,
          body: body,
        })
      ) {
        try { this.dispatchEvent(new Event("error")); } catch (e) {}
        try { if (typeof this.onerror === "function") this.onerror(); } catch (e2) {}
        return;
      }
      return _origXhrSend.apply(this, arguments);
    };

    XHR.prototype.open.__requestGuardPatched = true;
  }

  function installBeacon() {
    if (!_origSendBeacon || global.navigator.sendBeacon.__requestGuardPatched) return;
    global.navigator.sendBeacon = function (url, data) {
      if (!invokeMiddleware(String(url), "beacon", { data: data })) {
        return false;
      }
      return _origSendBeacon(url, data);
    };
    global.navigator.sendBeacon.__requestGuardPatched = true;
  }

  function install() {
    installFetch();
    installXHR();
    installBeacon();
  }

  global.RequestGuard = {
    configure: function (options) {
      options = options || {};
      if ("middleware" in options) {
        config.middleware =
          typeof options.middleware === "function" ? options.middleware : defaultMiddleware;
      }
      if ("debug" in options) config.debug = !!options.debug;
      if ("logAllRequests" in options) config.logAllRequests = !!options.logAllRequests;
      if ("blockedHostSuffixes" in options) {
        if (options.blockedHostSuffixes && options.blockedHostSuffixes.length)
          blockedHostSuffixes = options.blockedHostSuffixes.slice();
        else blockedHostSuffixes = AD_HOST_SUFFIXES.slice();
      }
      install();
      return this;
    },
    install: install,
  };

  install();
})(typeof window !== "undefined" ? window : this);

