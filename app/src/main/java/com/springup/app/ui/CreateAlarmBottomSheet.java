package com.springup.app.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.springup.app.R;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.data.local.model.MissionModel;
import com.springup.app.util.AudioStorageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateAlarmBottomSheet extends BottomSheetDialogFragment {

    public interface OnAlarmCreatedListener {
        void onAlarmCreated(AlarmEntity alarm);
    }

    private OnAlarmCreatedListener listener;
    private AlarmEntity editingAlarm;

    private int selectedHour = 7;
    private int selectedMinute = 0;
    private String selectedRingtoneUri = null;

    // Day toggle views
    private TextView dayMon, dayTue, dayWed, dayThu, dayFri, daySat, daySun;

    // Mission switches + config
    private MaterialSwitch switchMissionMath, switchMissionShake, switchWatchdog;
    private SeekBar seekMathDifficulty, seekShakeCount, seekVolume;
    private TextView tvMathDifficultyLabel, tvShakeCountLabel, tvVolumeLabel;
    private TextView tvMathCount, tvShakeCount;
    private View layoutMathDifficulty, layoutShakeConfig;

    private TextView tvSelectedTime;
    private TextView tvRingInInfo;
    private TextInputEditText etAlarmTitle;
    private TextView tvSelectedAudioName;
    private MaterialButton btnPickAudio;
    private MaterialButton btnSaveAlarm;

    private final ActivityResultLauncher<String> audioPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String permanentUri = AudioStorageHelper.copyAudioToInternalStorage(requireContext(), uri);
                    if (permanentUri != null) {
                        selectedRingtoneUri = permanentUri;
                        String name = AudioStorageHelper.getFileName(requireContext(), uri);
                        tvSelectedAudioName.setText(name != null ? name : "Custom Song Selected");
                        Toast.makeText(requireContext(), "Imported to app storage!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Failed to read audio file", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    public static CreateAlarmBottomSheet newInstance(OnAlarmCreatedListener listener) {
        CreateAlarmBottomSheet sheet = new CreateAlarmBottomSheet();
        sheet.listener = listener;
        sheet.editingAlarm = null;
        return sheet;
    }

    public static CreateAlarmBottomSheet newInstance(AlarmEntity alarmToEdit, OnAlarmCreatedListener listener) {
        CreateAlarmBottomSheet sheet = new CreateAlarmBottomSheet();
        sheet.listener = listener;
        sheet.editingAlarm = alarmToEdit;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_create_alarm, container, false);
        initViews(view);
        setupDayToggles();
        populateInitialState();
        setupGestureAndTimePicker(view);
        setupAudioPicker();
        setupMissionSwitches();
        setupSliders();
        setupWatchdogInfo(view);
        setupSaveListener();
        return view;
    }

    private void initViews(View view) {
        tvSelectedTime = view.findViewById(R.id.tvSelectedTime);
        tvRingInInfo   = view.findViewById(R.id.tvRingInInfo);
        etAlarmTitle   = view.findViewById(R.id.etAlarmTitle);

        dayMon = view.findViewById(R.id.dayMon);
        dayTue = view.findViewById(R.id.dayTue);
        dayWed = view.findViewById(R.id.dayWed);
        dayThu = view.findViewById(R.id.dayThu);
        dayFri = view.findViewById(R.id.dayFri);
        daySat = view.findViewById(R.id.daySat);
        daySun = view.findViewById(R.id.daySun);

        switchMissionMath  = view.findViewById(R.id.switchMissionMath);
        switchMissionShake = view.findViewById(R.id.switchMissionShake);
        switchWatchdog     = view.findViewById(R.id.switchWatchdog);

        seekMathDifficulty = view.findViewById(R.id.seekMathDifficulty);
        seekShakeCount     = view.findViewById(R.id.seekShakeCount);
        seekVolume         = view.findViewById(R.id.seekVolume);

        tvMathDifficultyLabel = view.findViewById(R.id.tvMathDifficultyLabel);
        tvShakeCountLabel     = view.findViewById(R.id.tvShakeCountLabel);
        tvVolumeLabel         = view.findViewById(R.id.tvVolumeLabel);
        tvMathCount           = view.findViewById(R.id.tvMathCount);
        tvShakeCount          = view.findViewById(R.id.tvShakeCount);

        layoutMathDifficulty = view.findViewById(R.id.layoutMathDifficulty);
        layoutShakeConfig    = view.findViewById(R.id.layoutShakeConfig);

        tvSelectedAudioName = view.findViewById(R.id.tvSelectedAudioName);
        btnPickAudio        = view.findViewById(R.id.btnPickAudio);
        btnSaveAlarm        = view.findViewById(R.id.btnSaveAlarm);
    }

    private void setupDayToggles() {
        TextView[] days = {dayMon, dayTue, dayWed, dayThu, dayFri, daySat, daySun};
        for (TextView day : days) {
            day.setOnClickListener(v -> {
                v.setActivated(!v.isActivated());
                updateTimeDisplay();
            });
        }
    }

    private void setDayMask(int mask) {
        int[] bits = {1, 2, 4, 8, 16, 32, 64};
        TextView[] days = {dayMon, dayTue, dayWed, dayThu, dayFri, daySat, daySun};
        for (int i = 0; i < 7; i++) {
            days[i].setActivated((mask & bits[i]) != 0);
        }
    }

    private int getDayMask() {
        int mask = 0;
        int[] bits = {1, 2, 4, 8, 16, 32, 64};
        TextView[] days = {dayMon, dayTue, dayWed, dayThu, dayFri, daySat, daySun};
        for (int i = 0; i < 7; i++) {
            if (days[i].isActivated()) mask |= bits[i];
        }
        return mask;
    }

    private void setupMissionSwitches() {
        switchMissionMath.setOnCheckedChangeListener((btn, checked) -> {
            layoutMathDifficulty.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        switchMissionShake.setOnCheckedChangeListener((btn, checked) -> {
            layoutShakeConfig.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
    }

    private void setupSliders() {
        // Math difficulty
        String[] diffLabels = {"Easy", "Medium", "Hard"};
        seekMathDifficulty.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                tvMathDifficultyLabel.setText(diffLabels[p]);
                tvMathCount.setText("3 problems · " + diffLabels[p]);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Shake count (seekbar max=75, actual count = progress+10 so range 10–85)
        seekShakeCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                int count = p + 10;
                tvShakeCountLabel.setText(String.valueOf(count));
                tvShakeCount.setText(count + " shakes");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Volume
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                tvVolumeLabel.setText(p + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void setupWatchdogInfo(View view) {
        View btnInfo = view.findViewById(R.id.btnWatchdogInfo);
        if (btnInfo != null) {
            btnInfo.setOnClickListener(v -> {
                Context context = getContext();
                if (context == null) return;
                new AlertDialog.Builder(context)
                        .setTitle("Anti-Relapse Watchdog")
                        .setMessage("After you dismiss the alarm, the Watchdog activates a 5-minute check-in timer.\n\n" +
                                "If you don't confirm you're awake within that time, the alarm rings again — " +
                                "preventing you from falling back asleep.\n\n" +
                                "Recommended for people who tend to snooze without realising.")
                        .setPositiveButton("Got it", null)
                        .show();
            });
        }
    }

    private void populateInitialState() {
        if (editingAlarm != null) {
            selectedHour = editingAlarm.getHour();
            selectedMinute = editingAlarm.getMinute();
            etAlarmTitle.setText(editingAlarm.getTitle());
            btnSaveAlarm.setText("UPDATE ALARM");
            selectedRingtoneUri = editingAlarm.getRingtoneUri();

            if (selectedRingtoneUri != null) {
                try {
                    String path = Uri.parse(selectedRingtoneUri).getPath();
                    tvSelectedAudioName.setText(path != null ? new File(path).getName() : "Custom Tone");
                } catch (Exception e) {
                    tvSelectedAudioName.setText("Custom Tone");
                }
            } else {
                tvSelectedAudioName.setText("Default Siren");
            }

            setDayMask(editingAlarm.getRepeatDaysMask());

            boolean hasMath = false, hasShake = false;
            int mathDiff = 0, shakeCount = 25;
            if (editingAlarm.getMissions() != null) {
                for (MissionModel m : editingAlarm.getMissions()) {
                    if ("MATH".equalsIgnoreCase(m.getMinigame())) {
                        hasMath = true;
                        mathDiff = Math.max(0, m.getDifficulty() - 1);
                    }
                    if ("POWER_SHAKE".equalsIgnoreCase(m.getMinigame())) {
                        hasShake = true;
                        shakeCount = m.getRequiredCompletions() > 0 ? m.getRequiredCompletions() : 25;
                    }
                }
            }
            switchMissionMath.setChecked(hasMath);
            seekMathDifficulty.setProgress(mathDiff);
            layoutMathDifficulty.setVisibility(hasMath ? View.VISIBLE : View.GONE);

            switchMissionShake.setChecked(hasShake);
            seekShakeCount.setProgress(Math.max(0, shakeCount - 10));
            layoutShakeConfig.setVisibility(hasShake ? View.VISIBLE : View.GONE);

            switchWatchdog.setChecked(editingAlarm.isWakeUpCheckEnabled());

            int vol = editingAlarm.getVolumeLevel();
            seekVolume.setProgress(vol);
            tvVolumeLabel.setText(vol + "%");

        } else {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, 2);
            selectedHour = cal.get(Calendar.HOUR_OF_DAY);
            selectedMinute = cal.get(Calendar.MINUTE);
            selectedRingtoneUri = null;
            tvSelectedAudioName.setText("Default Siren");
            btnSaveAlarm.setText("SAVE ALARM");
            // Default: weekdays on
            setDayMask(31); // Mon-Fri
            switchMissionMath.setChecked(true);
            switchMissionShake.setChecked(true);
            seekMathDifficulty.setProgress(0);
            seekShakeCount.setProgress(15); // 25 shakes
            seekVolume.setProgress(100);
            tvVolumeLabel.setText("100%");
        }

        updateTimeDisplay();
        // Trigger slider label updates
        String[] diffLabels = {"Easy", "Medium", "Hard"};
        tvMathDifficultyLabel.setText(diffLabels[seekMathDifficulty.getProgress()]);
        tvMathCount.setText("3 problems · " + diffLabels[seekMathDifficulty.getProgress()]);
        int shakeAmt = seekShakeCount.getProgress() + 10;
        tvShakeCountLabel.setText(String.valueOf(shakeAmt));
        tvShakeCount.setText(shakeAmt + " shakes");
    }

    private void updateTimeDisplay() {
        int displayHour = selectedHour % 12;
        if (displayHour == 0) displayHour = 12;
        String period = selectedHour >= 12 ? "PM" : "AM";
        tvSelectedTime.setText(String.format(Locale.US, "%02d:%02d %s", displayHour, selectedMinute, period));
        if (tvRingInInfo != null) {
            tvRingInInfo.setText(AlarmAdapter.calculateRingIn(selectedHour, selectedMinute, getDayMask()));
        }
    }

    private void setupGestureAndTimePicker(View view) {
        View cardTimeSelector = view.findViewById(R.id.cardTimeSelector);
        GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) { return true; }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                showTimePicker(MaterialTimePicker.INPUT_MODE_CLOCK);
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                showTimePicker(MaterialTimePicker.INPUT_MODE_KEYBOARD);
                return true;
            }
        });
        cardTimeSelector.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void showTimePicker(int inputMode) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setInputMode(inputMode)
                .setTitleText(inputMode == MaterialTimePicker.INPUT_MODE_KEYBOARD
                        ? "Type Alarm Time"
                        : "Select Alarm Time")
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            selectedHour = picker.getHour();
            selectedMinute = picker.getMinute();
            updateTimeDisplay();
        });
        picker.show(getParentFragmentManager(), "MATERIAL_TIME_PICKER");
    }

    private void setupAudioPicker() {
        if (btnPickAudio != null) {
            btnPickAudio.setOnClickListener(v -> audioPickerLauncher.launch("audio/*"));
        }
    }

    private void setupSaveListener() {
        btnSaveAlarm.setOnClickListener(v -> {
            String title = etAlarmTitle.getText() != null ? etAlarmTitle.getText().toString().trim() : "";
            if (title.isEmpty()) title = "Morning Wake Up";

            int mask = getDayMask();

            List<MissionModel> missions = new ArrayList<>();
            int stepOrder = 1;

            if (switchMissionMath.isChecked()) {
                int difficulty = seekMathDifficulty.getProgress() + 1; // 1=Easy, 2=Med, 3=Hard
                missions.add(new MissionModel(stepOrder++, "MATH", difficulty, 3, null));
            }
            if (switchMissionShake.isChecked()) {
                int shakeCount = seekShakeCount.getProgress() + 10;
                missions.add(new MissionModel(stepOrder++, "POWER_SHAKE", 1, shakeCount, null));
            }

            if (missions.isEmpty()) {
                Toast.makeText(getContext(), "Enable at least one mission!", Toast.LENGTH_SHORT).show();
                return;
            }

            int volume = seekVolume.getProgress();

            AlarmEntity alarmToSave;
            if (editingAlarm != null) {
                editingAlarm.setTitle(title);
                editingAlarm.setHour(selectedHour);
                editingAlarm.setMinute(selectedMinute);
                editingAlarm.setEnabled(true);
                editingAlarm.setRepeatDaysMask(mask);
                editingAlarm.setWakeUpCheckEnabled(switchWatchdog.isChecked());
                editingAlarm.setWakeUpCheckDelayMinutes(5);
                editingAlarm.setMissions(missions);
                editingAlarm.setVibrate(true);
                editingAlarm.setVolumeLevel(volume);
                editingAlarm.setRingtoneUri(selectedRingtoneUri);
                alarmToSave = editingAlarm;
            } else {
                alarmToSave = new AlarmEntity(
                        title,
                        selectedHour,
                        selectedMinute,
                        true,
                        mask,
                        switchWatchdog.isChecked(),
                        5,
                        missions,
                        true,
                        volume,
                        selectedRingtoneUri
                );
            }

            if (listener != null) {
                listener.onAlarmCreated(alarmToSave);
            }
            String ringIn = AlarmAdapter.calculateRingIn(selectedHour, selectedMinute, mask);
            Toast.makeText(requireContext(), "Alarm set! " + ringIn, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }
}