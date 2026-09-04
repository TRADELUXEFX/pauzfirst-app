package com.pauzfirst.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class TtsForegroundService extends Service {

    private static final String CHANNEL_ID = "pauzfirst_tts_channel";
    private static final int NOTIF_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "PauzFirst Training",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps question playback running while screen is locked");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PauzFirst")
            .setContentText("Training session active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build();

        startForeground(NOTIF_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
