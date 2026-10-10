package com.springup.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    public static final String ACTION_RESTORE_NOTIFICATION = "ACTION_RESTORE_NOTIFICATION";
    public static final String EXTRA_CUSTOM_RINGTONE_URI = "EXTRA_CUSTOM_RINGTONE_URI";

    private static final String TAG = "AlarmRingingService";
    private static final String CHANNEL_ID = "SPRING_UP_RINGING_CHANNEL";
    private static final int NOTIFICATION_ID = 4040;
    private static final String PREF_NAME = "SPRING_UP_ACTIVE_ALARM";

    public static volatile long currentlyRingingAlarmId = -1L;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    private String currentTitle = "Alarm";
    private boolean currentVibrate = true;
    private int currentVolume = 100;
    private String currentRingtoneUri = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_ALARM.equals(intent.getAction())) {
            stopForegroundAndCleanUp();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_RESTORE_NOTIFICATION.equals(intent.getAction())) {
            // Samsung S23 anti-delete: If notification was dismissed, re-post immediately
            restoreActiveAlarmState();
            postForegroundNotification();
            return START_STICKY;
        }

        if (intent != null) {
            long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
            if (alarmId != -1L) currentlyRingingAlarmId = alarmId;

            String title = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TITLE);
            if (title != null && !title.isEmpty()) currentTitle = title;

            currentVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true);
            currentVolume = intent.getIntExtra(AlarmScheduler.EXTRA_VOLUME, 100);

            String uri = intent.getStringExtra(EXTRA_CUSTOM_RINGTONE_URI);
            if (uri == null) {
                uri = intent.getStringExtra(AlarmScheduler.EXTRA_CUSTOM_RINGTONE_URI);
            }
            if (uri != null && !uri.isEmpty()) {
                currentRingtoneUri = uri;
            }

            saveActiveAlarmState();
        } else {
            // System restarted service after kill/swipe: restore previous custom ringtone and volume
            restoreActiveAlarmState();
        }

        // Keep foreground status active and persistent
        postForegroundNotification();

        // Start playback only if not already playing, preserving custom audio and volume
        startAudioAndVibration(currentVibrate, currentVolume, currentRingtoneUri);

        return START_STICKY;
    }

    private void postForegroundNotification() {
        Notification notification = buildPersistentRingingNotification(currentTitle, currentlyRingingAlarmId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private synchronized void startAudioAndVibration(boolean shouldVibrate, int volumeLevel, @Nullable String customAudioUriStr) {
        // If media player is already happily playing, DO NOT reset audio
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            return;
        }

        stopAlarmPlayback();

        try {
            Uri alarmUri = null;
            if (customAudioUriStr != null && !customAudioUriStr.trim().isEmpty()) {
                try {
                    alarmUri = Uri.parse(customAudioUriStr);
                } catch (Exception ignored) {}
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
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

            float volScalar = Math.max(0.01f, Math.min(1.0f, volumeLevel / 100.0f));
            mediaPlayer.setVolume(volScalar, volScalar);
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing alarm audio stream", e);
            // Fallback to default if custom file had an I/O error
            try {
                Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (fallback != null) {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(this, fallback);
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                    mediaPlayer.setLooping(true);
                    mediaPlayer.prepare();
                    float volScalar = Math.max(0.01f, Math.min(1.0f, volumeLevel / 100.0f));
                    mediaPlayer.setVolume(volScalar, volScalar);
                    mediaPlayer.start();
                }
            } catch (Exception ignored) {}
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        }
    }

    private synchronized void stopAlarmPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
            vibrator = null;
        }
    }

    private void stopForegroundAndCleanUp() {
        currentlyRingingAlarmId = -1L;
        clearActiveAlarmState();
        stopAlarmPlayback();
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
        stopSelf();
    }

    private Notification buildPersistentRingingNotification(String title, long alarmId) {
        Intent fullScreenIntent = new Intent(this, RingingActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                (int) (alarmId != -1L ? alarmId : 9999),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Anti-deletion intent: If notification is swiped away, this intent immediately restores it
        Intent restoreIntent = new Intent(this, AlarmRingingService.class);
        restoreIntent.setAction(ACTION_RESTORE_NOTIFICATION);
        PendingIntent deletePendingIntent = PendingIntent.getService(
                this,
                NOTIFICATION_ID,
                restoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title != null ? title : "Spring Up Alarm")
                .setContentText("Mission in progress — tap to open")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setDeleteIntent(deletePendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

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
            channel.setDescription("Critical notifications for active ringing alarms");
            channel.setSound(null, null); // MediaPlayer manages audio to support custom tones & volume
            channel.enableVibration(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void saveActiveAlarmState() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        sp.edit()
                .putLong("alarm_id", currentlyRingingAlarmId)
                .putString("title", currentTitle)
                .putBoolean("vibrate", currentVibrate)
                .putInt("volume", currentVolume)
                .putString("ringtone_uri", currentRingtoneUri)
                .apply();
    }

    private void restoreActiveAlarmState() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentlyRingingAlarmId = sp.getLong("alarm_id", currentlyRingingAlarmId);
        currentTitle = sp.getString("title", currentTitle);
        currentVibrate = sp.getBoolean("vibrate", currentVibrate);
        currentVolume = sp.getInt("volume", currentVolume);
        currentRingtoneUri = sp.getString("ringtone_uri", currentRingtoneUri);
    }

    private void clearActiveAlarmState() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        sp.edit().clear().apply();
    }

    @Override
    public void onDestroy() {
        stopForegroundAndCleanUp();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}