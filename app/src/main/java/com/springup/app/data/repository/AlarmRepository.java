package com.springup.app.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.springup.app.data.local.AppDatabase;
import com.springup.app.data.local.dao.AlarmDao;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.data.local.model.MissionModel;
import com.springup.app.data.remote.RetrofitClient;
import com.springup.app.data.remote.SpringUpApiService;
import com.springup.app.data.remote.dto.AlarmApiModels.AlarmRequest;
import com.springup.app.data.remote.dto.AlarmApiModels.AlarmResponse;
import com.springup.app.data.remote.dto.AlarmApiModels.MissionConfigDto;
import com.springup.app.engine.AlarmScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmRepository {

    private final Application application;
    private final AlarmDao alarmDao;
    private final SpringUpApiService apiService;
    private final LiveData<List<AlarmEntity>> allAlarms;

    public AlarmRepository(Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getDatabase(application);
        this.alarmDao = db.alarmDao();
        this.apiService = RetrofitClient.getApiService();
        this.allAlarms = alarmDao.getAllAlarmsLiveData();
    }

    public LiveData<List<AlarmEntity>> getAllAlarms() {
        return allAlarms;
    }

    public void insert(AlarmEntity alarm) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long localId = alarmDao.insert(alarm);
            alarm.setId(localId);

            if (alarm.isEnabled()) {
                AlarmScheduler.scheduleAlarm(application, alarm);
            }

            AlarmRequest request = mapToRequest(alarm);
            apiService.createAlarm(request).enqueue(new Callback<AlarmResponse>() {
                @Override
                public void onResponse(@NonNull Call<AlarmResponse> call, @NonNull Response<AlarmResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Log.d("SpringUpSync", "Sync Success! Server ID: " + response.body().id);
                        alarm.setServerId(response.body().id);
                        AppDatabase.databaseWriteExecutor.execute(() -> alarmDao.update(alarm));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<AlarmResponse> call, @NonNull Throwable t) {
                    Log.e("SpringUpSync", "Sync Failure: " + t.getMessage());
                }
            });
        });
    }

    public void update(AlarmEntity alarm) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            alarmDao.update(alarm);
            if (alarm.isEnabled()) {
                AlarmScheduler.scheduleAlarm(application, alarm);
            } else {
                AlarmScheduler.cancelAlarm(application, alarm.getId());
                AlarmScheduler.cancelWakeUpCheck(application, alarm.getId());
            }
        });
    }

    public void toggleAlarm(long id, boolean isEnabled) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            alarmDao.updateAlarmStatus(id, isEnabled);
            AlarmEntity updated = alarmDao.getAlarmById(id);
            if (updated != null) {
                if (isEnabled) {
                    AlarmScheduler.scheduleAlarm(application, updated);
                } else {
                    AlarmScheduler.cancelAlarm(application, updated.getId());
                    AlarmScheduler.cancelWakeUpCheck(application, updated.getId());
                }
            }
        });
    }

    public void delete(AlarmEntity alarm) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            alarmDao.delete(alarm);
            AlarmScheduler.cancelAlarm(application, alarm.getId());
            AlarmScheduler.cancelWakeUpCheck(application, alarm.getId());

            if (alarm.getServerId() != null) {
                apiService.deleteAlarm(alarm.getServerId()).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {}

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {}
                });
            }
        });
    }

    private AlarmRequest mapToRequest(AlarmEntity alarm) {
        String alarmTime = String.format(Locale.US, "%02d:%02d:00", alarm.getHour(), alarm.getMinute());

        List<MissionConfigDto> missionDtos = new ArrayList<>();
        if (alarm.getMissions() != null) {
            for (MissionModel m : alarm.getMissions()) {
                missionDtos.add(new MissionConfigDto(
                        m.getStepOrder(),
                        m.getMinigame(),
                        m.getDifficulty(),
                        m.getRequiredCompletions(),
                        m.getTargetBarcodeHash()
                ));
            }
        }

        return new AlarmRequest(
                alarm.getTitle(),
                alarmTime,
                alarm.isEnabled(),
                alarm.getRepeatDaysMask(),
                alarm.isWakeUpCheckEnabled(),
                alarm.getWakeUpCheckDelayMinutes(),
                missionDtos,
                alarm.isVibrate(),
                alarm.getVolumeLevel()
        );
    }
}