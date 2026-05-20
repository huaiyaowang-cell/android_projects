/**
 * Unity WebGL WEBAudio 解锁（兼容三星等旧版 WebView：避免 iframe，配合用户触摸）。
 */
(function (global) {
  "use strict";

  var SILENT_WAV =
    "data:audio/wav;base64,UklGRigAAABXQVZFZm10IBIAAAABAAEARKwAAIhYAQACABAAAABkYXRhAgAAAAEA";

  function getWebAudio() {
    try {
      if (typeof WEBAudio !== "undefined") return WEBAudio;
    } catch (e) {}
    try {
      if (global.Module && global.Module.WEBAudio) return global.Module.WEBAudio;
    } catch (e2) {}
    return null;
  }

  function playSilentHtmlAudio() {
    try {
      var a = new Audio(SILENT_WAV);
      a.volume = 0.01;
      var p = a.play();
      if (p && typeof p.then === "function") {
        p.then(function () {
          try { a.pause(); } catch (e) {}
        }).catch(function () {});
      }
    } catch (e) {}
  }

  function resumeWebAudio() {
    playSilentHtmlAudio();
    var wa = getWebAudio();
    if (!wa) return;
    try {
      wa.audioWebEnabled = 1;
    } catch (e) {}
    try {
      if (wa.audioContext) {
        if (wa.audioContext.state === "suspended") {
          var pr = wa.audioContext.resume();
          if (pr && typeof pr.then === "function") {
            pr.then(function () {
              try { wa.contextIsRunning = true; } catch (e3) {}
            }).catch(function () {});
          }
        } else if (wa.audioContext.state === "running") {
          wa.contextIsRunning = true;
        }
      }
    } catch (e2) {}
    try {
      if (global.Module && typeof global.Module.WebAudioContextResume === "function") {
        global.Module.WebAudioContextResume();
      }
    } catch (e4) {}
    try {
      if (global.unityInstance && global.unityInstance.Module
          && typeof global.unityInstance.Module.WebAudioContextResume === "function") {
        global.unityInstance.Module.WebAudioContextResume();
      }
    } catch (e5) {}
  }

  global.__resumeGameAudio = resumeWebAudio;

  function onUserGesture() {
    resumeWebAudio();
  }

  ["touchstart", "touchend", "mousedown", "mouseup", "click", "pointerdown"].forEach(function (ev) {
    global.addEventListener(ev, onUserGesture, { passive: true, capture: true });
  });

  global.addEventListener("message", function (event) {
    var data = event && event.data;
    if (data && data.type === "unlock_audio") onUserGesture();
  });

  // Unity 加载完成后继续尝试 resume（旧 WebView 上 interval 有时不够）
  var tries = 0;
  var poll = setInterval(function () {
    tries++;
    if (getWebAudio() && getWebAudio().audioContext) {
      resumeWebAudio();
      if (getWebAudio().audioContext.state === "running" || tries > 120) {
        clearInterval(poll);
      }
    }
    if (tries > 120) clearInterval(poll);
  }, 500);
})(window);
