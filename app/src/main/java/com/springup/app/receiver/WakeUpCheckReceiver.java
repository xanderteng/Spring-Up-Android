package com.springup.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import com.springup.app.engine.AlarmScheduler;
import com.springup.app.ui.WatchdogActivity;

public class WakeUpCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);

        // 1. Hold CPU awake for 5 seconds to guarantee the Activity launches smoothly
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SpringUp:WatchdogWakeLock"
            );
            wakeLock.acquire(5 * 1000L);
        }

        // 2. Launch WatchdogActivity directly over lock screen / background
        Intent activityIntent = new Intent(context, WatchdogActivity.class);
        activityIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        activityIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        );
        context.startActivity(activityIntent);
    }
}