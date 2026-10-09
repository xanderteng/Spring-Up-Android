package com.springup.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.springup.app.data.local.AppDatabase;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.engine.AlarmScheduler;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(context);
                List<AlarmEntity> enabledAlarms = db.alarmDao().getEnabledAlarmsSync();
                for (AlarmEntity alarm : enabledAlarms) {
                    AlarmScheduler.scheduleAlarm(context, alarm);
                }
            });
        }
    }
}