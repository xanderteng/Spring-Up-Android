package com.springup.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import com.springup.app.engine.AlarmScheduler;
import com.springup.app.service.AlarmRingingService;
import com.springup.app.ui.RingingActivity;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);

        // 1. Acquire temporary 10s wakelock to ensure CPU stays on for the handoff
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SpringUp:AlarmWakeLock"
            );
            wakeLock.acquire(10 * 1000L);
        }

        // 2. Start the foreground audio & vibration service
        Intent serviceIntent = new Intent(context, AlarmRingingService.class);
        if (intent.getExtras() != null) {
            serviceIntent.putExtras(intent.getExtras());
        }
        ContextCompat.startForegroundService(context, serviceIntent);

        // 3. Force-launch RingingActivity directly to the foreground
        Intent activityIntent = new Intent(context, RingingActivity.class);
        activityIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        activityIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        );
        context.startActivity(activityIntent);
    }
}