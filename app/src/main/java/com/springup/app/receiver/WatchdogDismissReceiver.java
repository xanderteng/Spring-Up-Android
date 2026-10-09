package com.springup.app.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.springup.app.engine.AlarmScheduler;

public class WatchdogDismissReceiver extends BroadcastReceiver {

    public static final String ACTION_DISARM_WATCHDOG = "com.springup.app.ACTION_DISARM_WATCHDOG";

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);

        // 1. Cancel the hardware timer
        AlarmScheduler.cancelWakeUpCheck(context, alarmId);

        // 2. Clear the heads-up notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel((int) (alarmId + 200000));
        }

        Toast.makeText(context, "Watchdog disarmed. Stay sharp!", Toast.LENGTH_LONG).show();
    }
}