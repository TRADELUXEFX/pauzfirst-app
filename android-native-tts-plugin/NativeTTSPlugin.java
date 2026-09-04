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

    @Override
    public void load() {
        tts = new TextToSpeech(getContext(), this);
        Intent serviceIntent = new Intent(getContext(), TtsForegroundService.class);
        getContext().startForegroundService(serviceIntent);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            isReady = true;
        }
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        Double rateD = call.getDouble("rate", 1.0);
        Double pitchD = call.getDouble("pitch", 1.0);
        float rate = rateD.floatValue();
        float pitch = pitchD.floatValue();

        if (!isReady || text.isEmpty()) {
            call.reject("TTS not ready or empty text");
            return;
        }

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
