package com.springup.app;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.databinding.ActivityMainBinding;
import com.springup.app.engine.AlarmScheduler;
import com.springup.app.service.AlarmRingingService;
import com.springup.app.ui.AlarmAdapter;
import com.springup.app.ui.AlarmViewModel;
import com.springup.app.ui.CreateAlarmBottomSheet;
import com.springup.app.ui.RingingActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AlarmViewModel viewModel;
    private AlarmAdapter adapter;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "Notification permission is needed for alarm alerts.", Toast.LENGTH_LONG).show();
                }
                checkNextPermissions();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkAppPermissions();
        setupRecyclerView();
        setupViewModel();
        setupFab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AlarmRingingService.currentlyRingingAlarmId != -1L) {
            Intent intent = new Intent(this, RingingActivity.class);
            intent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, AlarmRingingService.currentlyRingingAlarmId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        }
    }

    private void checkAppPermissions() {
        // 1. Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        checkNextPermissions();
    }

    private void checkNextPermissions() {
        // 2. Exact Alarm Permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Exact Alarm Permission Required")
                        .setMessage("Android requires explicit permission for Spring Up to ring exactly on time down to the minute.")
                        .setPositiveButton("Grant Permission", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception ignored) {}
                        })
                        .setNegativeButton("Later", (dialog, which) -> checkScreenOverlayPermission())
                        .setCancelable(false)
                        .show();
                return;
            }
        }

        checkScreenOverlayPermission();
    }

    private void checkScreenOverlayPermission() {
        // 3. Appear on Top / Overlay Permission (Required for S23 persistent mission screen)
        if (!Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Appear on Top (Screen Lock)")
                    .setMessage("To prevent accidentally swiping away or bypassing missions on your Samsung Galaxy, please allow 'Appear on top'.")
                    .setPositiveButton("Allow", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("Skip", (dialog, which) -> checkBatteryOptimization())
                    .setCancelable(false)
                    .show();
            return;
        }

        checkBatteryOptimization();
    }

    private void checkBatteryOptimization() {
        // 4. Battery Optimization Exemption for Samsung One UI reliability
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Unrestricted Battery Mode")
                    .setMessage("Samsung One UI aggressively pauses background apps. Disabling battery restrictions ensures alarms trigger without fail.")
                    .setPositiveButton("Optimize", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("Keep Default", null)
                    .show();
        }
    }

    private void setupRecyclerView() {
        adapter = new AlarmAdapter(
                (alarm, isEnabled) -> {
                    if (alarm.getId() == AlarmRingingService.currentlyRingingAlarmId) {
                        Toast.makeText(this, "Complete the mission to disable this alarm!", Toast.LENGTH_SHORT).show();
                        adapter.notifyDataSetChanged();
                        return;
                    }
                    viewModel.toggleAlarm(alarm, isEnabled);
                    if (isEnabled) {
                        String ringIn = AlarmAdapter.calculateRingIn(alarm.getHour(), alarm.getMinute(), alarm.getRepeatDaysMask());
                        Toast.makeText(this, ringIn, Toast.LENGTH_SHORT).show();
                    }
                },
                alarmToEdit -> {
                    CreateAlarmBottomSheet editSheet = CreateAlarmBottomSheet.newInstance(alarmToEdit, updatedAlarm -> {
                        viewModel.updateAlarm(updatedAlarm);
                    });
                    editSheet.show(getSupportFragmentManager(), "EDIT_ALARM_SHEET");
                }
        );

        binding.rvAlarms.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAlarms.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < adapter.getCurrentList().size()) {
                    AlarmEntity alarm = adapter.getCurrentList().get(position);
                    if (alarm.getId() == AlarmRingingService.currentlyRingingAlarmId) {
                        return 0; // Lock swipe on ringing alarm
                    }
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    AlarmEntity deleted = adapter.getCurrentList().get(position);
                    viewModel.deleteAlarm(deleted);

                    com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), "Alarm deleted", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> viewModel.insertAlarm(deleted))
                            .show();
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvAlarms);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(AlarmViewModel.class);
        viewModel.getAllAlarms().observe(this, alarms -> {
            adapter.submitList(alarms != null ? new java.util.ArrayList<>(alarms) : null);
            binding.layoutEmptyState.setVisibility(
                    (alarms == null || alarms.isEmpty()) ? View.VISIBLE : View.GONE
            );
        });
    }

    private void setupFab() {
        binding.fabAddAlarm.setOnClickListener(v -> {
            CreateAlarmBottomSheet bottomSheet = CreateAlarmBottomSheet.newInstance(alarm -> {
                viewModel.insertAlarm(alarm);
            });
            bottomSheet.show(getSupportFragmentManager(), "CREATE_ALARM_SHEET");
        });
    }
}