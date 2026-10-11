// Bridges the page's speechSynthesis calls to the native Android TTS plugin
// when running inside the Capacitor app. In a normal browser it uses the
// browser's own speechSynthesis untouched.
//
// Callers pass the SpeechSynthesisUtterance itself: NativeTTSBridge.speak(utterance)
// so utterance.onend / onerror keep working (training flow depends on onend).
(function () {
  const isNative = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());

  const Bridge = { isNative: isNative, speak: function (u) { window.speechSynthesis.speak(u); }, stop: function () { window.speechSynthesis.cancel(); } };

  if (isNative) {
    const getPlugin = () => (window.Capacitor.Plugins || {}).NativeTTS;
    let seq = 0;
    let current = null;      // { id, u }
    let pending = 0;

    if (typeof window.SpeechSynthesisUtterance === 'undefined') {
      window.SpeechSynthesisUtterance = function (text) {
        this.text = text || ''; this.rate = 1; this.pitch = 1; this.volume = 1; this.voice = null;
        this.onend = null; this.onerror = null; this.onstart = null;
      };
    }

    function finish(kind, id, err) {
      if (!current || current.id !== id) return;   // stale event from a flushed utterance
      const u = current.u;
      current = null; pending = 0;
      try {
        if (kind === 'end' && typeof u.onend === 'function') u.onend({});
        if (kind === 'error' && typeof u.onerror === 'function') u.onerror({ error: err || 'synthesis-failed' });
      } catch (e) { console.error(e); }
    }

    const native = {
      get speaking() { return !!current; },
      get pending() { return false; },
      paused: false,
      onvoiceschanged: null,
      getVoices: function () { return []; },
      cancel: function () {
        current = null; pending = 0;
        const p = getPlugin();
        if (p) p.stop().catch(function () {});
      },
      speak: function (u) { Bridge.speak(u); },
      pause: function () {}, resume: function () {}
    };

    // Always use the native engine inside the app (the WebView's own
    // speechSynthesis is missing or silent on many Android devices).
    try { Object.defineProperty(window, 'speechSynthesis', { value: native, configurable: true }); }
    catch (e) { window.speechSynthesis = native; }

    let listening = false;
    function ensureListener() {
      const p = getPlugin();
      if (!p || listening) return;
      listening = true;
      p.addListener('speechEvent', function (e) {
        if (!e) return;
        if (e.event === 'done') finish('end', e.id);
        else if (e.event === 'error') finish('error', e.id);
      });
    }

    Bridge.speak = function (u) {
      const p = getPlugin();
      if (!p) {
        console.error('NativeTTS plugin is not registered in MainActivity');
        if (u && typeof u.onerror === 'function') setTimeout(function () { u.onerror({ error: 'synthesis-unavailable' }); }, 0);
        return Promise.resolve();
      }
      ensureListener();
      const text = (u && u.text) || '';
      if (!text) { return Promise.resolve(); }
      const id = 'pf' + (++seq);
      current = { id: id, u: u };
      return p.speak({ text: text, rate: u.rate || 1.0, pitch: u.pitch || 1.0, id: id }).catch(function (err) {
        finish('error', id, String(err && err.message || err));
      });
    };
    Bridge.stop = function () { native.cancel(); return Promise.resolve(); };
  }

  window.NativeTTSBridge = Bridge;
})();
