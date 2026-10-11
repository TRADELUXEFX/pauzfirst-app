package com.pauzfirst.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Custom plugins must be registered BEFORE super.onCreate, otherwise
        // window.Capacitor.Plugins.NativeTTS does not exist and TTS is silent.
        registerPlugin(NativeTTSPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
