package com.springup.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.springup.app.R;
import com.springup.app.data.local.entity.AlarmEntity;
import com.springup.app.data.local.model.MissionModel;

import java.util.Locale;

public class AlarmAdapter extends ListAdapter<AlarmEntity, AlarmAdapter.AlarmViewHolder> {

    public interface OnAlarmToggleListener {
        void onToggle(AlarmEntity alarm, boolean isEnabled);
    }

    private final OnAlarmToggleListener toggleListener;

    public AlarmAdapter(OnAlarmToggleListener toggleListener) {
        super(DIFF_CALLBACK);
        this.toggleListener = toggleListener;
    }

    private static final DiffUtil.ItemCallback<AlarmEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull AlarmEntity oldItem, @NonNull AlarmEntity newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull AlarmEntity oldItem, @NonNull AlarmEntity newItem) {
                    return oldItem.getHour() == newItem.getHour()
                            && oldItem.getMinute() == newItem.getMinute()
                            && oldItem.isEnabled() == newItem.isEnabled()
                            && oldItem.getTitle().equals(newItem.getTitle())
                            && oldItem.getRepeatDaysMask() == newItem.getRepeatDaysMask();
                }
            };

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alarm, parent, false);
        return new AlarmViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public class AlarmViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAlarmTime;
        private final TextView tvAlarmPeriod;
        private final TextView tvAlarmTitle;
        private final TextView tvAlarmDays;
        private final TextView tvMissionSummaryBadge;
        private final TextView tvWatchdogBadge;
        private final MaterialSwitch switchAlarmEnabled;

        public AlarmViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlarmTime = itemView.findViewById(R.id.tvAlarmTime);
            tvAlarmPeriod = itemView.findViewById(R.id.tvAlarmPeriod);
            tvAlarmTitle = itemView.findViewById(R.id.tvAlarmTitle);
            tvAlarmDays = itemView.findViewById(R.id.tvAlarmDays);
            tvMissionSummaryBadge = itemView.findViewById(R.id.tvMissionSummaryBadge);
            tvWatchdogBadge = itemView.findViewById(R.id.tvWatchdogBadge);
            switchAlarmEnabled = itemView.findViewById(R.id.switchAlarmEnabled);
        }

        public void bind(AlarmEntity alarm) {
            int displayHour = alarm.getHour() % 12;
            if (displayHour == 0) displayHour = 12;
            String period = alarm.getHour() >= 12 ? "PM" : "AM";

            tvAlarmTime.setText(String.format(Locale.US, "%02d:%02d", displayHour, alarm.getMinute()));
            tvAlarmPeriod.setText(period);
            tvAlarmTitle.setText(alarm.getTitle());
            tvAlarmDays.setText(formatRepeatDays(alarm.getRepeatDaysMask()));

            // Render mission summary badge
            int missionCount = alarm.getMissions() != null ? alarm.getMissions().size() : 0;
            if (missionCount > 0) {
                StringBuilder summary = new StringBuilder(missionCount + " MISSIONS (");
                for (int i = 0; i < missionCount; i++) {
                    MissionModel m = alarm.getMissions().get(i);
                    summary.append(m.getMinigame());
                    if (i < missionCount - 1) summary.append(" ➔ ");
                }
                summary.append(")");
                tvMissionSummaryBadge.setVisibility(View.VISIBLE);
                tvMissionSummaryBadge.setText(summary.toString());
            } else {
                tvMissionSummaryBadge.setVisibility(View.GONE);
            }

            // Render watchdog badge
            if (alarm.isWakeUpCheckEnabled()) {
                tvWatchdogBadge.setVisibility(View.VISIBLE);
                tvWatchdogBadge.setText("WATCHDOG " + alarm.getWakeUpCheckDelayMinutes() + "M");
            } else {
                tvWatchdogBadge.setVisibility(View.GONE);
            }

            // Toggle switch listener
            switchAlarmEnabled.setOnCheckedChangeListener(null);
            switchAlarmEnabled.setChecked(alarm.isEnabled());
            switchAlarmEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
                if (toggleListener != null) {
                    toggleListener.onToggle(alarm, isChecked);
                }
            });
        }

        private String formatRepeatDays(int mask) {
            if (mask == 0) return "Once (No Repeat)";
            if (mask == 31) return "Weekdays (Mon - Fri)";
            if (mask == 96) return "Weekends (Sat - Sun)";
            if (mask == 127) return "Every day";

            StringBuilder days = new StringBuilder();
            if ((mask & 1) != 0) days.append("Mon ");
            if ((mask & 2) != 0) days.append("Tue ");
            if ((mask & 4) != 0) days.append("Wed ");
            if ((mask & 8) != 0) days.append("Thu ");
            if ((mask & 16) != 0) days.append("Fri ");
            if ((mask & 32) != 0) days.append("Sat ");
            if ((mask & 64) != 0) days.append("Sun ");
            return days.toString().trim();
        }
    }
}