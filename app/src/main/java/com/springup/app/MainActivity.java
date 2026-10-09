package com.springup.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
                    Toast.makeText(this, "Notification permission is required for alarms to alert you.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkNotificationPermission();
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

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new AlarmAdapter((alarm, isEnabled) -> {
            if (alarm.getId() == AlarmRingingService.currentlyRingingAlarmId) {
                Toast.makeText(this, "Complete the mission to disable this alarm!", Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
                return;
            }
            viewModel.toggleAlarm(alarm, isEnabled);
        });

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
                    AlarmEntity alarmToDelete = adapter.getCurrentList().get(position);
                    viewModel.deleteAlarm(alarmToDelete);
                    Toast.makeText(MainActivity.this, "Alarm deleted: " + alarmToDelete.getTitle(), Toast.LENGTH_SHORT).show();
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvAlarms);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(AlarmViewModel.class);
        viewModel.getAllAlarms().observe(this, alarms -> {
            adapter.submitList(alarms);
            binding.layoutEmptyState.setVisibility(
                    (alarms == null || alarms.isEmpty()) ? View.VISIBLE : View.GONE
            );
        });
    }

    private void setupFab() {
        binding.fabAddAlarm.setOnClickListener(v -> {
            CreateAlarmBottomSheet bottomSheet = CreateAlarmBottomSheet.newInstance(alarm -> {
                viewModel.insertAlarm(alarm);
                Toast.makeText(this,
                        String.format(Locale.US, "Alarm set for %02d:%02d!", alarm.getHour(), alarm.getMinute()),
                        Toast.LENGTH_SHORT).show();
            });
            bottomSheet.show(getSupportFragmentManager(), "CREATE_ALARM_SHEET");
        });
    }
}