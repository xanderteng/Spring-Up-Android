package com.springup.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.springup.app.engine.AlarmScheduler;
import com.springup.app.service.AlarmRingingService;

public class WakeUpCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);

        // Emergency Re-Ring: User relapsed into sleep without confirming the watchdog
        Intent serviceIntent = new Intent(context, AlarmRingingService.class);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_ALARM_TITLE, "RELAPSE DETECTED: WAKE UP!");
        serviceIntent.putExtra(AlarmScheduler.EXTRA_VIBRATE, true);

        ContextCompat.startForegroundService(context, serviceIntent);
    }
}