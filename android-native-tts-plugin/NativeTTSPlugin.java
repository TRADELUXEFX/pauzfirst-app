package com.pauzfirst.app;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;

@CapacitorPlugin(name = "NativeTTS")
public class NativeTTSPlugin extends Plugin implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private boolean isReady = false;
    private final java.util.List<PluginCall> pendingSpeakCalls = new java.util.ArrayList<>();

    @Override
    public void load() {
        tts = new TextToSpeech(getContext(), this);
        try {
            Intent serviceIntent = new Intent(getContext(), TtsForegroundService.class);
            getContext().startForegroundService(serviceIntent);
        } catch (Exception e) {
            // The foreground service is only there to keep playback alive with the
            // screen locked — it must never be allowed to take TTS down with it.
            // Common failure here: POST_NOTIFICATIONS not granted at runtime on
            // Android 13+, or the OS refusing a foreground-service start outright.
            // Speech still works fine without it, so just log and move on.
            android.util.Log.w("NativeTTSPlugin", "TTS foreground service failed to start; speech will still work in foreground", e);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int langResult = tts.setLanguage(Locale.US);
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // US English voice data isn't installed/available on this device.
                // Fall back to whatever the engine's default voice is rather than
                // leaving TTS silently broken.
                tts.setLanguage(Locale.getDefault());
            }
            isReady = true;
            // Flush anything that tried to speak before init finished.
            synchronized (pendingSpeakCalls) {
                for (PluginCall pending : pendingSpeakCalls) {
                    doSpeak(pending);
                }
                pendingSpeakCalls.clear();
            }
        } else {
            // Init failed outright — reject anything queued so callers aren't left hanging.
            synchronized (pendingSpeakCalls) {
                for (PluginCall pending : pendingSpeakCalls) {
                    pending.reject("TTS engine failed to initialize");
                }
                pendingSpeakCalls.clear();
            }
        }
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        if (text.isEmpty()) {
            call.reject("Empty text");
            return;
        }
        if (!isReady) {
            // Engine hasn't finished initializing yet (common right after app launch) —
            // queue this call instead of dropping it, and resolve it once onInit fires.
            call.setKeepAlive(true);
            synchronized (pendingSpeakCalls) {
                pendingSpeakCalls.add(call);
            }
            return;
        }
        doSpeak(call);
    }

    private void doSpeak(PluginCall call) {
        String text = call.getString("text", "");
        Double rateD = call.getDouble("rate", 1.0);
        Double pitchD = call.getDouble("pitch", 1.0);
        float rate = rateD.floatValue();
        float pitch = pitchD.floatValue();

        tts.setSpeechRate(rate);
        tts.setPitch(pitch);

        String utteranceId = "PAUZFIRST_" + System.currentTimeMillis();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String id) {
                JSObject ret = new JSObject();
                ret.put("event", "start");
                notifyListeners("speechEvent", ret);
            }

            @Override
            public void onDone(String id) {
                JSObject ret = new JSObject();
                ret.put("event", "done");
                notifyListeners("speechEvent", ret);
            }

            @Override
            public void onError(String id) {
                JSObject ret = new JSObject();
                ret.put("event", "error");
                notifyListeners("speechEvent", ret);
            }
        });

        Bundle params = new Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);

        JSObject result = new JSObject();
        result.put("started", true);
        call.resolve(result);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) {
            tts.stop();
        }
        call.resolve();
    }

    @PluginMethod
    public void getVoices(PluginCall call) {
        JSObject result = new JSObject();
        if (tts != null) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (android.speech.tts.Voice v : tts.getVoices()) {
                names.add(v.getName());
            }
            result.put("voices", names);
        }
        call.resolve(result);
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        Intent serviceIntent = new Intent(getContext(), TtsForegroundService.class);
        getContext().stopService(serviceIntent);
        super.handleOnDestroy();
    }
}
