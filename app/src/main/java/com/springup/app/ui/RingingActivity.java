package com.springup.app.ui;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.springup.app.data.local.AppDatabase;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.data.local.model.MissionModel;
import com.springup.app.data.remote.RetrofitClient;
import com.springup.app.data.remote.dto.AlarmApiModels.VerificationRequest;
import com.springup.app.data.remote.dto.AlarmApiModels.VerificationResponse;
import com.springup.app.databinding.ActivityRingingBinding;
import com.springup.app.engine.AlarmScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RingingActivity extends AppCompatActivity implements SensorEventListener {

    private ActivityRingingBinding binding;
    private AlarmEntity currentAlarm;
    private List<MissionModel> missionQueue = new ArrayList<>();
    private int currentMissionIndex = 0;

    // Math Mission State
    private int mathTargetAnswer;
    private int mathCompletedCount = 0;
    private int mathRequiredCount = 3;
    private final Random random = new Random();

    // Shake Mission State
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int shakeCount = 0;
    private int shakeRequiredCount = 25;
    private float lastX, lastY, lastZ;
    private long lastShakeTimestamp;
    private static final int SHAKE_THRESHOLD = 800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreenFlags();

        binding = ActivityRingingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        long alarmId = getIntent().getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        loadAlarmData(alarmId);
        setupListeners();
    }

    private void setupScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void loadAlarmData(long alarmId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);

            if (alarmId != -1L) {
                currentAlarm = db.alarmDao().getAlarmById(alarmId);
            }

            if (currentAlarm == null) {
                List<AlarmEntity> enabled = db.alarmDao().getEnabledAlarmsSync();
                if (enabled != null && !enabled.isEmpty()) {
                    currentAlarm = enabled.get(0);
                }
            }

            runOnUiThread(() -> {
                if (currentAlarm != null) {
                    binding.tvRingingAlarmTime.setText(
                            String.format(Locale.US, "%02d:%02d", currentAlarm.getHour(), currentAlarm.getMinute())
                    );
                    binding.tvRingingAlarmTitle.setText(currentAlarm.getTitle());

                    if (currentAlarm.getMissions() != null && !currentAlarm.getMissions().isEmpty()) {
                        missionQueue = new ArrayList<>(currentAlarm.getMissions());
                    } else {
                        missionQueue = new ArrayList<>(Collections.singletonList(
                                new MissionModel(1, "MATH", 1, 3, null)
                        ));
                    }
                } else {
                    binding.tvRingingAlarmTime.setText("ALARM");
                    binding.tvRingingAlarmTitle.setText("Wake Up!");
                    missionQueue = new ArrayList<>(Collections.singletonList(
                            new MissionModel(1, "MATH", 1, 3, null)
                    ));
                }

                startMissionStage(0);
            });
        });
    }

    private void setupListeners() {
        binding.btnSubmitMath.setOnClickListener(v -> checkMathAnswer());
    }

    private void startMissionStage(int index) {
        if (index >= missionQueue.size()) {
            completeAllMissions();
            return;
        }

        currentMissionIndex = index;
        MissionModel mission = missionQueue.get(index);
        binding.tvCurrentStageIndicator.setText(String.format(Locale.US, "STAGE %d OF %d: %s",
                index + 1, missionQueue.size(), mission.getMinigame()));

        if ("MATH".equalsIgnoreCase(mission.getMinigame())) {
            binding.layoutMathMission.setVisibility(View.VISIBLE);
            binding.layoutShakeMission.setVisibility(View.GONE);
            mathCompletedCount = 0;
            mathRequiredCount = mission.getRequiredCompletions() > 0 ? mission.getRequiredCompletions() : 3;
            generateMathEquation();
            unregisterShakeSensor();
        } else if ("POWER_SHAKE".equalsIgnoreCase(mission.getMinigame())) {
            binding.layoutMathMission.setVisibility(View.GONE);
            binding.layoutShakeMission.setVisibility(View.VISIBLE);
            shakeCount = 0;
            shakeRequiredCount = mission.getRequiredCompletions() > 0 ? mission.getRequiredCompletions() : 25;
            binding.tvShakeCounter.setText(String.valueOf(shakeRequiredCount));
            binding.progressShake.setMax(shakeRequiredCount);
            binding.progressShake.setProgress(0);
            registerShakeSensor();
        }
    }

    private void generateMathEquation() {
        int a = random.nextInt(20) + 5;
        int b = random.nextInt(20) + 5;
        boolean isAddition = random.nextBoolean();

        if (isAddition) {
            mathTargetAnswer = a + b;
            binding.tvMathEquation.setText(String.format(Locale.US, "%d + %d = ?", a, b));
        } else {
            mathTargetAnswer = a;
            int sum = a + b;
            binding.tvMathEquation.setText(String.format(Locale.US, "%d - %d = ?", sum, b));
        }

        if (binding.etMathAnswer.getText() != null) {
            binding.etMathAnswer.getText().clear();
        }
        binding.tvMissionProgress.setText(String.format(Locale.US, "Problem %d of %d",
                mathCompletedCount + 1, mathRequiredCount));
    }

    private void checkMathAnswer() {
        if (binding.etMathAnswer.getText() == null) return;
        String answerStr = binding.etMathAnswer.getText().toString().trim();
        if (answerStr.isEmpty()) return;

        try {
            int userAnswer = Integer.parseInt(answerStr);
            if (userAnswer == mathTargetAnswer) {
                mathCompletedCount++;
                if (mathCompletedCount >= mathRequiredCount) {
                    Toast.makeText(this, "Math Stage Complete!", Toast.LENGTH_SHORT).show();
                    startMissionStage(currentMissionIndex + 1);
                } else {
                    generateMathEquation();
                }
            } else {
                Toast.makeText(this, "Incorrect! Try again.", Toast.LENGTH_SHORT).show();
                generateMathEquation();
            }
        } catch (NumberFormatException e) {
            if (binding.etMathAnswer.getText() != null) {
                binding.etMathAnswer.getText().clear();
            }
        }
    }

    private void registerShakeSensor() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterShakeSensor() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        long curTime = System.currentTimeMillis();
        if ((curTime - lastShakeTimestamp) > 100) {
            long diffTime = (curTime - lastShakeTimestamp);
            lastShakeTimestamp = curTime;

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000;

            if (speed > SHAKE_THRESHOLD) {
                shakeCount++;
                binding.tvShakeCounter.setText(String.valueOf(Math.max(0, shakeRequiredCount - shakeCount)));
                binding.progressShake.setProgress(shakeCount);

                if (shakeCount >= shakeRequiredCount) {
                    unregisterShakeSensor();
                    Toast.makeText(this, "Shake Stage Complete!", Toast.LENGTH_SHORT).show();
                    startMissionStage(currentMissionIndex + 1);
                }
            }

            lastX = x;
            lastY = y;
            lastZ = z;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void completeAllMissions() {
        unregisterShakeSensor();

        // 1. Silence audio service
        AlarmScheduler.stopRingingService(this);

        // 2. Arm Watchdog if enabled
        if (currentAlarm != null && currentAlarm.isWakeUpCheckEnabled()) {
            AlarmScheduler.scheduleWakeUpCheck(this, currentAlarm.getId(), currentAlarm.getWakeUpCheckDelayMinutes());
            Toast.makeText(this, "Watchdog armed! Stay awake.", Toast.LENGTH_LONG).show();
        }

        // 3. Post telemetry to Spring Boot backend
        if (currentAlarm != null && currentAlarm.getServerId() != null) {
            VerificationRequest req = new VerificationRequest(
                    currentAlarm.getServerId(),
                    "MISSION_GAUNTLET",
                    "PASSED",
                    null,
                    30,
                    0
            );
            RetrofitClient.getApiService().verifySolution(req).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<VerificationResponse> call, @NonNull Response<VerificationResponse> response) {}

                @Override
                public void onFailure(@NonNull Call<VerificationResponse> call, @NonNull Throwable t) {}
            });
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        unregisterShakeSensor();
        super.onDestroy();
    }
}