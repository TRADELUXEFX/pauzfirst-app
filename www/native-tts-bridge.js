const NativeTTSBridge = {
  isNative: !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform()),

  speak(text, opts = {}) {
    if (this.isNative) {
      const { NativeTTS } = window.Capacitor.Plugins;
      return NativeTTS.speak({
        text: text,
        rate: opts.rate || 1.0,
        pitch: opts.pitch || 1.0
      });
    } else {
      const u = new SpeechSynthesisUtterance(text);
      if (opts.rate) u.rate = opts.rate;
      if (opts.pitch) u.pitch = opts.pitch;
      window.speechSynthesis.speak(u);
      return Promise.resolve({ started: true });
    }
  },

  stop() {
    if (this.isNative) {
      const { NativeTTS } = window.Capacitor.Plugins;
      return NativeTTS.stop();
    } else {
      window.speechSynthesis.cancel();
      return Promise.resolve();
    }
  }
};

window.NativeTTSBridge = NativeTTSBridge;
