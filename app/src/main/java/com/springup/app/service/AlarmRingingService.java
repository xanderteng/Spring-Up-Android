package com.springup.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.springup.app.R;
import com.springup.app.engine.AlarmScheduler;
import com.springup.app.ui.RingingActivity;

public class AlarmRingingService extends Service {

    public static final String ACTION_STOP_ALARM = "ACTION_STOP_ALARM";
    private static final String TAG = "AlarmRingingService";
    private static final String CHANNEL_ID = "SPRING_UP_RINGING_CHANNEL";
    private static final int NOTIFICATION_ID = 4040;

    // Track active ringing alarm for anti-cheat UI locking
    public static volatile long currentlyRingingAlarmId = -1L;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_ALARM.equals(intent.getAction())) {
            currentlyRingingAlarmId = -1L;
            stopAlarmPlayback();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        long alarmId = intent != null ? intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) : -1L;
        currentlyRingingAlarmId = alarmId;
        String title = intent != null ? intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TITLE) : "Alarm";
        boolean vibrate = intent == null || intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true);

        Notification notification = buildRingingNotification(title, alarmId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startAudioAndVibration(vibrate);

        return START_STICKY;
    }

    private void startAudioAndVibration(boolean shouldVibrate) {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing alarm audio", e);
        }

        if (shouldVibrate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 600, 400, 600, 400};
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            }
        }
    }

    private void stopAlarmPlayback() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private Notification buildRingingNotification(String title, long alarmId) {
        Intent fullScreenIntent = new Intent(this, RingingActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                (int) (alarmId != -1L ? alarmId : 9999),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("MISSION ACTIVE: " + title)
                .setContentText("Complete the mission gauntlet to dismiss the alarm!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPendingIntent, true);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Spring Up Alarm Ringing",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Critical notifications for ringing alarms");
            channel.setSound(null, null);
            channel.enableVibration(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopAlarmPlayback();
        currentlyRingingAlarmId = -1L;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}