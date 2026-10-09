package com.springup.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.springup.app.R;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.data.local.model.MissionModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateAlarmBottomSheet extends BottomSheetDialogFragment {

    public interface OnAlarmCreatedListener {
        void onAlarmCreated(AlarmEntity alarm);
    }

    private OnAlarmCreatedListener listener;

    private int selectedHour = 7;
    private int selectedMinute = 0;

    private TextView tvSelectedTime;
    private TextInputEditText etAlarmTitle;
    private Chip chipMon, chipTue, chipWed, chipThu, chipFri, chipSat, chipSun;
    private MaterialCheckBox cbMissionMath, cbMissionShake;
    private MaterialSwitch switchWatchdog;
    private MaterialButton btnSaveAlarm;

    public static CreateAlarmBottomSheet newInstance(OnAlarmCreatedListener listener) {
        CreateAlarmBottomSheet sheet = new CreateAlarmBottomSheet();
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_create_alarm, container, false);
        initViews(view);
        setupDefaultTime();
        setupListeners(view);
        return view;
    }

    private void initViews(View view) {
        tvSelectedTime = view.findViewById(R.id.tvSelectedTime);
        etAlarmTitle = view.findViewById(R.id.etAlarmTitle);
        chipMon = view.findViewById(R.id.chipMon);
        chipTue = view.findViewById(R.id.chipTue);
        chipWed = view.findViewById(R.id.chipWed);
        chipThu = view.findViewById(R.id.chipThu);
        chipFri = view.findViewById(R.id.chipFri);
        chipSat = view.findViewById(R.id.chipSat);
        chipSun = view.findViewById(R.id.chipSun);
        cbMissionMath = view.findViewById(R.id.cbMissionMath);
        cbMissionShake = view.findViewById(R.id.cbMissionShake);
        switchWatchdog = view.findViewById(R.id.switchWatchdog);
        btnSaveAlarm = view.findViewById(R.id.btnSaveAlarm);
    }

    private void setupDefaultTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 2); // Default to 2 minutes from now for quick testing
        selectedHour = cal.get(Calendar.HOUR_OF_DAY);
        selectedMinute = cal.get(Calendar.MINUTE);
        updateTimeDisplay();
    }

    private void updateTimeDisplay() {
        int displayHour = selectedHour % 12;
        if (displayHour == 0) displayHour = 12;
        String period = selectedHour >= 12 ? "PM" : "AM";
        tvSelectedTime.setText(String.format(Locale.US, "%02d:%02d %s", displayHour, selectedMinute, period));
    }

    private void setupListeners(View view) {
        view.findViewById(R.id.cardTimeSelector).setOnClickListener(v -> showTimePicker());

        btnSaveAlarm.setOnClickListener(v -> {
            String title = etAlarmTitle.getText() != null ? etAlarmTitle.getText().toString().trim() : "";
            if (title.isEmpty()) title = "Morning Wake Up";

            // Calculate Repeat Days Mask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64
            int mask = 0;
            if (chipMon.isChecked()) mask |= 1;
            if (chipTue.isChecked()) mask |= 2;
            if (chipWed.isChecked()) mask |= 4;
            if (chipThu.isChecked()) mask |= 8;
            if (chipFri.isChecked()) mask |= 16;
            if (chipSat.isChecked()) mask |= 32;
            if (chipSun.isChecked()) mask |= 64;

            // Build Mission Gauntlet
            List<MissionModel> missions = new ArrayList<>();
            int stepOrder = 1;

            if (cbMissionMath.isChecked()) {
                missions.add(new MissionModel(stepOrder++, "MATH", 1, 3, null));
            }
            if (cbMissionShake.isChecked()) {
                missions.add(new MissionModel(stepOrder++, "POWER_SHAKE", 1, 25, null));
            }

            if (missions.isEmpty()) {
                Toast.makeText(getContext(), "Select at least one mission!", Toast.LENGTH_SHORT).show();
                return;
            }

            AlarmEntity alarm = new AlarmEntity(
                    title,
                    selectedHour,
                    selectedMinute,
                    true,
                    mask,
                    switchWatchdog.isChecked(),
                    5, // 5 minutes watchdog delay
                    missions,
                    true,
                    100
            );

            if (listener != null) {
                listener.onAlarmCreated(alarm);
            }
            dismiss();
        });
    }

    private void showTimePicker() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("Select Alarm Time")
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            selectedHour = picker.getHour();
            selectedMinute = picker.getMinute();
            updateTimeDisplay();
        });

        picker.show(getParentFragmentManager(), "MATERIAL_TIME_PICKER");
    }
}