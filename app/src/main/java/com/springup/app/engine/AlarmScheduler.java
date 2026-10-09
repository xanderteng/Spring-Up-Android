package com.springup.app.engine;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import com.springup.app.MainActivity;
import com.springup.app.R;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.receiver.AlarmReceiver;
import com.springup.app.receiver.WakeUpCheckReceiver;
import com.springup.app.receiver.WatchdogDismissReceiver;
import com.springup.app.service.AlarmRingingService;

import java.util.Calendar;

public class AlarmScheduler {

    public static final String EXTRA_ALARM_ID = "EXTRA_ALARM_ID";
    public static final String EXTRA_ALARM_TITLE = "EXTRA_ALARM_TITLE";
    public static final String EXTRA_VOLUME = "EXTRA_VOLUME";
    public static final String EXTRA_VIBRATE = "EXTRA_VIBRATE";
    public static final String WATCHDOG_CHANNEL_ID = "SPRING_UP_WATCHDOG_CHANNEL";

    public static void scheduleAlarm(Context context, AlarmEntity alarm) {
        if (!alarm.isEnabled()) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        }

        long triggerMillis = calculateNextTriggerMillis(alarm.getHour(), alarm.getMinute(), alarm.getRepeatDaysMask());

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_ALARM_ID, alarm.getId());
        intent.putExtra(EXTRA_ALARM_TITLE, alarm.getTitle());
        intent.putExtra(EXTRA_VOLUME, alarm.getVolumeLevel());
        intent.putExtra(EXTRA_VIBRATE, alarm.isVibrate());

        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                (int) alarm.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent showIntent = new Intent(context, MainActivity.class);
        PendingIntent showOperation = PendingIntent.getActivity(
                context,
                (int) alarm.getId(),
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(triggerMillis, showOperation);
        alarmManager.setAlarmClock(clockInfo, operation);
    }

    public static void cancelAlarm(Context context, long alarmId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                (int) alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(operation);
    }

    public static void stopRingingService(Context context) {
        Intent stopIntent = new Intent(context, AlarmRingingService.class);
        stopIntent.setAction(AlarmRingingService.ACTION_STOP_ALARM);
        context.startService(stopIntent);
    }

    public static void scheduleWakeUpCheck(Context context, long alarmId, int delayMinutes) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long triggerMillis = System.currentTimeMillis() + (delayMinutes * 60 * 1000L);

        Intent intent = new Intent(context, WakeUpCheckReceiver.class);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);

        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                (int) (alarmId + 100000),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation);

        showWatchdogNotification(context, alarmId, delayMinutes);
    }

    public static void cancelWakeUpCheck(Context context, long alarmId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, WakeUpCheckReceiver.class);
            PendingIntent operation = PendingIntent.getBroadcast(
                    context,
                    (int) (alarmId + 100000),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(operation);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel((int) (alarmId + 200000));
        }
    }

    private static void showWatchdogNotification(Context context, long alarmId, int delayMinutes) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    WATCHDOG_CHANNEL_ID,
                    "Anti-Relapse Watchdog",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Prompts to confirm wakefulness after alarm mission completion");
            manager.createNotificationChannel(channel);
        }

        Intent disarmIntent = new Intent(context, WatchdogDismissReceiver.class);
        disarmIntent.setAction(WatchdogDismissReceiver.ACTION_DISARM_WATCHDOG);
        disarmIntent.putExtra(EXTRA_ALARM_ID, alarmId);

        PendingIntent disarmPendingIntent = PendingIntent.getBroadcast(
                context,
                (int) (alarmId + 300000),
                disarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, WATCHDOG_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Watchdog Active (" + delayMinutes + "m)")
                .setContentText("Tap 'I'M AWAKE' to prevent emergency re-ring!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_media_play, "I'M AWAKE", disarmPendingIntent);

        manager.notify((int) (alarmId + 200000), builder.build());
    }

    private static long calculateNextTriggerMillis(int hour, int minute, int repeatDaysMask) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (repeatDaysMask == 0) {
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }
            return target.getTimeInMillis();
        }

        for (int i = 0; i < 7; i++) {
            int dayOfWeek = target.get(Calendar.DAY_OF_WEEK);
            int maskBit = getMaskBitForCalendarDay(dayOfWeek);

            boolean isDaySelected = (repeatDaysMask & maskBit) != 0;
            if (isDaySelected && target.after(now)) {
                return target.getTimeInMillis();
            }
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        return target.getTimeInMillis();
    }

    private static int getMaskBitForCalendarDay(int calendarDayOfWeek) {
        return switch (calendarDayOfWeek) {
            case Calendar.MONDAY -> 1;
            case Calendar.TUESDAY -> 2;
            case Calendar.WEDNESDAY -> 4;
            case Calendar.THURSDAY -> 8;
            case Calendar.FRIDAY -> 16;
            case Calendar.SATURDAY -> 32;
            case Calendar.SUNDAY -> 64;
            default -> 0;
        };
    }
}