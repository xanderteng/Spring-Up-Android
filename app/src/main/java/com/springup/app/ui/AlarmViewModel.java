package com.springup.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.data.repository.AlarmRepository;
import com.springup.app.engine.AlarmScheduler;

import java.util.List;

public class AlarmViewModel extends AndroidViewModel {

    private final AlarmRepository repository;
    private final LiveData<List<AlarmEntity>> allAlarms;

    public AlarmViewModel(@NonNull Application application) {
        super(application);
        this.repository = new AlarmRepository(application);
        this.allAlarms = repository.getAllAlarms();
    }

    public LiveData<List<AlarmEntity>> getAllAlarms() {
        return allAlarms;
    }

    public void insertAlarm(AlarmEntity alarm) {
        repository.insert(alarm);
        if (alarm.isEnabled()) {
            AlarmScheduler.scheduleAlarm(getApplication(), alarm);
        }
    }

    public void toggleAlarm(AlarmEntity alarm, boolean isEnabled) {
        alarm.setEnabled(isEnabled);
        repository.toggleAlarm(alarm.getId(), isEnabled);

        if (isEnabled) {
            AlarmScheduler.scheduleAlarm(getApplication(), alarm);
        } else {
            AlarmScheduler.cancelAlarm(getApplication(), alarm.getId());
            AlarmScheduler.cancelWakeUpCheck(getApplication(), alarm.getId());
        }
    }

    public void deleteAlarm(AlarmEntity alarm) {
        repository.delete(alarm);
    }
}