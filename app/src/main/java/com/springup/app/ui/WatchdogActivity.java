package com.springup.app.ui;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.springup.app.R;
import com.springup.app.databinding.ActivityWatchdogBinding;
import com.springup.app.engine.AlarmScheduler;
import com.springup.app.receiver.AlarmReceiver;

import java.util.Locale;

public class WatchdogActivity extends AppCompatActivity {

    private ActivityWatchdogBinding binding;
    private CountDownTimer countDownTimer;
    private MediaPlayer alertPlayer;
    private long alarmId = -1L;
    private boolean isAwakeConfirmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreenFlags();

        binding = ActivityWatchdogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        alarmId = getIntent().getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(WatchdogActivity.this, "Confirm you are awake!", Toast.LENGTH_SHORT).show();
            }
        });

        startWarningAudio();
        startCountdown();

        binding.btnImAwake.setOnClickListener(v -> confirmAwake());
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
        if (!isAwakeConfirmed && !isFinishing() && !isDestroyed()) {
            reclaimFocus();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && !isAwakeConfirmed && !isFinishing() && !isDestroyed()) {
            reclaimFocus();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isAwakeConfirmed && !isFinishing() && !isDestroyed()) {
            reclaimFocus();
        }
    }

    private void reclaimFocus() {
        if (isAwakeConfirmed || isFinishing() || isDestroyed()) return;
        try {
            Intent reclaim = new Intent(this, WatchdogActivity.class);
            reclaim.putExtras(getIntent());
            reclaim.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(reclaim);
        } catch (Exception ignored) {}
    }

    private void startWarningAudio() {
        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            alertPlayer = new MediaPlayer();
            alertPlayer.setDataSource(this, soundUri);
            alertPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            alertPlayer.setLooping(false);
            alertPlayer.prepare();
            alertPlayer.start();
        } catch (Exception ignored) {}
    }

    private void startCountdown() {
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                binding.tvCountdown.setText(String.format(Locale.US, "%02d", seconds));
            }

            @Override
            public void onFinish() {
                if (!isAwakeConfirmed) {
                    reRingAlarm();
                }
            }
        }.start();
    }

    private void confirmAwake() {
        isAwakeConfirmed = true;
        stopAlertAudio();
        if (countDownTimer != null) countDownTimer.cancel();
        AlarmScheduler.cancelWakeUpCheck(this, alarmId);
        Toast.makeText(this, "Watchdog disarmed! Great job staying awake.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void reRingAlarm() {
        stopAlertAudio();
        Intent reringIntent = new Intent(this, AlarmReceiver.class);
        reringIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        reringIntent.putExtra(AlarmScheduler.EXTRA_ALARM_TITLE, "EMERGENCY: Watchdog Expired!");
        sendBroadcast(reringIntent);
        finish();
    }

    private void stopAlertAudio() {
        if (alertPlayer != null) {
            try {
                if (alertPlayer.isPlaying()) alertPlayer.stop();
                alertPlayer.release();
            } catch (Exception ignored) {}
            alertPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopAlertAudio();
        if (countDownTimer != null) countDownTimer.cancel();
        super.onDestroy();
    }
}