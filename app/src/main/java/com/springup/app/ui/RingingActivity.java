package com.springup.app.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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
import com.springup.app.receiver.AlarmReceiver;
import com.springup.app.service.AlarmRingingService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RingingActivity extends AppCompatActivity implements SensorEventListener {

    public static final String EXTRA_SNOOZE_COUNT = "EXTRA_SNOOZE_COUNT";
    private static final int MAX_SNOOZE_COUNT = 3;
    private static final int SHAKE_THRESHOLD = 2000;

    private ActivityRingingBinding binding;
    private AlarmEntity currentAlarm;
    private List<MissionModel> missionQueue = new ArrayList<>();
    private int currentMissionIndex = 0;
    private boolean isGauntletComplete = false;
    private int currentSnoozeCount = 0;

    // Math Mission State
    private int mathTargetAnswer;
    private int mathCompletedCount = 0;
    private int mathRequiredCount = 3;
    private int mathDifficulty = 1;
    private final Random random = new Random();

    // Shake Mission State
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int shakeCount = 0;
    private int shakeRequiredCount = 25;
    private float lastX, lastY, lastZ;
    private long lastShakeTimestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreenFlags();

        binding = ActivityRingingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentSnoozeCount = getIntent().getIntExtra(EXTRA_SNOOZE_COUNT, 0);

        // Anti-Bypass: Nullify system back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(RingingActivity.this, "Complete all missions to dismiss!", Toast.LENGTH_SHORT).show();
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        long alarmId = getIntent().getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        loadAlarmData(alarmId);
        setupListeners();
        setupSnoozeButton();
    }

    private void setupScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Anti-Bypass: If user presses Home or Recents, bounce them right back
        if (!isGauntletComplete && !isFinishing() && !isDestroyed()) {
            reclaimFocus();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Anti-Bypass: If user pulls status bar or attempts to multitask, reclaim focus
        if (!hasFocus && !isGauntletComplete && !isFinishing() && !isDestroyed()) {
            reclaimFocus();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isGauntletComplete && !isFinishing() && !isDestroyed()) {
            reclaimFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterShakeSensor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isGauntletComplete && currentMissionIndex < missionQueue.size()) {
            MissionModel currentMission = missionQueue.get(currentMissionIndex);
            if (currentMission != null && "POWER_SHAKE".equalsIgnoreCase(currentMission.getMinigame())) {
                registerShakeSensor();
            }
        }
    }

    private void reclaimFocus() {
        if (isGauntletComplete || isFinishing() || isDestroyed()) return;
        try {
            Intent reclaim = new Intent(this, RingingActivity.class);
            reclaim.putExtras(getIntent());
            reclaim.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(reclaim);
        } catch (Exception ignored) {}
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
                if (isFinishing() || isDestroyed()) return;
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

    private void setupSnoozeButton() {
        if (currentSnoozeCount >= MAX_SNOOZE_COUNT) {
            binding.btnSnooze.setVisibility(View.GONE);
        } else {
            binding.btnSnooze.setVisibility(View.VISIBLE);
            int remaining = MAX_SNOOZE_COUNT - currentSnoozeCount;
            binding.btnSnooze.setText(String.format(Locale.US, "SNOOZE 5M (%d LEFT)", remaining));
            binding.btnSnooze.setOnClickListener(v -> executeSnooze());
        }
    }

    private void executeSnooze() {
        isGauntletComplete = true;
        unregisterShakeSensor();
        AlarmScheduler.stopRingingService(this);

        // Schedule snooze 5 minutes out
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null && currentAlarm != null) {
            Intent snoozeIntent = new Intent(this, AlarmReceiver.class);
            snoozeIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, currentAlarm.getId());
            snoozeIntent.putExtra(AlarmScheduler.EXTRA_ALARM_TITLE, currentAlarm.getTitle() + " (Snooze)");
            snoozeIntent.putExtra(EXTRA_SNOOZE_COUNT, currentSnoozeCount + 1);

            PendingIntent op = PendingIntent.getBroadcast(
                    this,
                    (int) (currentAlarm.getId() + 700000),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            long trigger = System.currentTimeMillis() + (5 * 60 * 1000L);
            AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(trigger, op);
            alarmManager.setAlarmClock(clockInfo, op);
        }

        Toast.makeText(this, "Alarm snoozed for 5 minutes.", Toast.LENGTH_SHORT).show();
        finish();
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
            mathDifficulty = mission.getDifficulty() > 0 ? mission.getDifficulty() : 1;
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
        switch (mathDifficulty) {
            case 3: // Hard: 2-digit multiplication or 3 operands
                int m1 = random.nextInt(12) + 4;
                int m2 = random.nextInt(9) + 3;
                mathTargetAnswer = m1 * m2;
                binding.tvMathEquation.setText(String.format(Locale.US, "%d × %d = ?", m1, m2));
                break;
            case 2: // Medium: 2-digit addition/subtraction
                int a2 = random.nextInt(50) + 20;
                int b2 = random.nextInt(40) + 10;
                if (random.nextBoolean()) {
                    mathTargetAnswer = a2 + b2;
                    binding.tvMathEquation.setText(String.format(Locale.US, "%d + %d = ?", a2, b2));
                } else {
                    mathTargetAnswer = a2;
                    binding.tvMathEquation.setText(String.format(Locale.US, "%d - %d = ?", a2 + b2, b2));
                }
                break;
            case 1: // Easy: 1-digit
            default:
                int a1 = random.nextInt(9) + 2;
                int b1 = random.nextInt(9) + 2;
                mathTargetAnswer = a1 + b1;
                binding.tvMathEquation.setText(String.format(Locale.US, "%d + %d = ?", a1, b1));
                break;
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
        isGauntletComplete = true;
        unregisterShakeSensor();

        // Silence audio service
        AlarmScheduler.stopRingingService(this);

        // Arm or reset the unified Watchdog for the latest ringing alarm
        if (currentAlarm != null && currentAlarm.isWakeUpCheckEnabled()) {
            AlarmScheduler.scheduleWakeUpCheck(this, currentAlarm.getId(), currentAlarm.getWakeUpCheckDelayMinutes());
        }

        // Post telemetry to backend
        if (currentAlarm != null && currentAlarm.getServerId() != null) {
            VerificationRequest req = new VerificationRequest(
                    currentAlarm.getServerId(),
                    "MISSION_GAUNTLET",
                    "PASSED",
                    null,
                    30,
                    currentSnoozeCount
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