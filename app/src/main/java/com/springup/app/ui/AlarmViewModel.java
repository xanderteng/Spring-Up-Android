package com.springup.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.data.repository.AlarmRepository;

import java.util.List;

public class AlarmViewModel extends AndroidViewModel {

    private final AlarmRepository repository;
    private final LiveData<List<AlarmEntity>> allAlarms;

    public AlarmViewModel(@NonNull Application application) {
        super(application);
        repository = new AlarmRepository(application);
        allAlarms = repository.getAllAlarms();
    }

    public LiveData<List<AlarmEntity>> getAllAlarms() {
        return allAlarms;
    }

    public void insertAlarm(AlarmEntity alarm) {
        repository.insert(alarm);
    }

    public void updateAlarm(AlarmEntity alarm) {
        repository.update(alarm);
    }

    public void toggleAlarm(AlarmEntity alarm, boolean isEnabled) {
        repository.toggleAlarm(alarm.getId(), isEnabled);
    }

    public void deleteAlarm(AlarmEntity alarm) {
        repository.delete(alarm);
    }
}