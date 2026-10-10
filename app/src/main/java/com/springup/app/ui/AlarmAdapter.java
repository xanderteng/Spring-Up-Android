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

import java.util.Calendar;
import java.util.Locale;

public class AlarmAdapter extends ListAdapter<AlarmEntity, AlarmAdapter.AlarmViewHolder> {

    public interface OnAlarmToggleListener {
        void onToggle(AlarmEntity alarm, boolean isEnabled);
    }

    public interface OnAlarmClickListener {
        void onAlarmClick(AlarmEntity alarm);
    }

    private final OnAlarmToggleListener toggleListener;
    private final OnAlarmClickListener clickListener;

    public AlarmAdapter(OnAlarmToggleListener toggleListener, OnAlarmClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.toggleListener = toggleListener;
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<AlarmEntity> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull AlarmEntity oldItem, @NonNull AlarmEntity newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull AlarmEntity oldItem, @NonNull AlarmEntity newItem) {
            return oldItem.getHour() == newItem.getHour()
                    && oldItem.getMinute() == newItem.getMinute()
                    && oldItem.isEnabled() == newItem.isEnabled()
                    && oldItem.getRepeatDaysMask() == newItem.getRepeatDaysMask()
                    && oldItem.isWakeUpCheckEnabled() == newItem.isWakeUpCheckEnabled()
                    && oldItem.getWakeUpCheckDelayMinutes() == newItem.getWakeUpCheckDelayMinutes()
                    && oldItem.getVolumeLevel() == newItem.getVolumeLevel()
                    && java.util.Objects.equals(oldItem.getTitle(), newItem.getTitle())
                    && java.util.Objects.equals(oldItem.getRingtoneUri(), newItem.getRingtoneUri())
                    && java.util.Objects.equals(oldItem.getMissions(), newItem.getMissions());
        }
    };

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
        return new AlarmViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public class AlarmViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAlarmTime;
        private final TextView tvAlarmPeriod;
        private final TextView tvRingIn;
        private final TextView tvAlarmDays;
        private final TextView tvMissionSummaryBadge;
        private final TextView tvWatchdogBadge;
        private final MaterialSwitch switchAlarmEnabled;

        public AlarmViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlarmTime = itemView.findViewById(R.id.tvAlarmTime);
            tvAlarmPeriod = itemView.findViewById(R.id.tvAlarmPeriod);
            tvRingIn = itemView.findViewById(R.id.tvRingIn);
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

            if (alarm.isEnabled()) {
                tvRingIn.setVisibility(View.VISIBLE);
                tvRingIn.setText("• " + calculateRingIn(alarm.getHour(), alarm.getMinute(), alarm.getRepeatDaysMask()));
            } else {
                tvRingIn.setVisibility(View.GONE);
            }

            tvAlarmDays.setText(formatRepeatDays(alarm.getRepeatDaysMask()));

            int missionCount = alarm.getMissions() != null ? alarm.getMissions().size() : 0;
            if (missionCount > 0) {
                tvMissionSummaryBadge.setVisibility(View.VISIBLE);
                tvMissionSummaryBadge.setText(missionCount + " MISSION" + (missionCount > 1 ? "S" : ""));
            } else {
                tvMissionSummaryBadge.setVisibility(View.GONE);
            }

            if (alarm.isWakeUpCheckEnabled()) {
                tvWatchdogBadge.setVisibility(View.VISIBLE);
                tvWatchdogBadge.setText("WATCHDOG " + alarm.getWakeUpCheckDelayMinutes() + "M");
            } else {
                tvWatchdogBadge.setVisibility(View.GONE);
            }

            // Dim time text when disabled
            float alpha = alarm.isEnabled() ? 1.0f : 0.45f;
            tvAlarmTime.setAlpha(alpha);
            tvAlarmPeriod.setAlpha(alpha);
            tvAlarmDays.setAlpha(alpha);

            switchAlarmEnabled.setOnCheckedChangeListener(null);
            switchAlarmEnabled.setChecked(alarm.isEnabled());
            switchAlarmEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (toggleListener != null) {
                    toggleListener.onToggle(alarm, isChecked);
                }
            });

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onAlarmClick(alarm);
                }
            });
        }

        private String formatRepeatDays(int mask) {
            if (mask == 0) return "Once";
            if (mask == 127) return "Every day";
            if (mask == 31)  return "Weekdays";
            if (mask == 96)  return "Weekends";

            String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
            int[] bits = {1, 2, 4, 8, 16, 32, 64};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if ((mask & bits[i]) != 0) {
                    sb.append(letters[i]);
                } else {
                    sb.append("·");
                }
                if (i < 6) sb.append("  ");
            }
            return sb.toString();
        }
    }

    public static String calculateRingIn(int targetHour, int targetMinute, int repeatDaysMask) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, targetMinute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (repeatDaysMask == 0) {
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else {
            int currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2..
            int[] daysMap = {64, 1, 2, 4, 8, 16, 32}; // Sunday(1)->64, Monday(2)->1...
            int daysAhead = 0;
            boolean found = false;
            for (int i = 0; i < 7; i++) {
                int checkDay = (currentDayOfWeek - 1 + i) % 7 + 1;
                int bit = daysMap[checkDay - 1];
                if ((repeatDaysMask & bit) != 0) {
                    if (i == 0) {
                        if (target.after(now)) {
                            daysAhead = 0;
                            found = true;
                            break;
                        }
                    } else {
                        daysAhead = i;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                if (!target.after(now)) {
                    daysAhead = 1;
                }
            }
            target.add(Calendar.DAY_OF_YEAR, daysAhead);
        }

        long diffMillis = target.getTimeInMillis() - now.getTimeInMillis();
        if (diffMillis <= 0) diffMillis = 60 * 1000;
        long diffMinutes = diffMillis / (60 * 1000);
        long hours = diffMinutes / 60;
        long days = hours / 24;
        hours = hours % 24;
        long minutes = diffMinutes % 60;

        StringBuilder sb = new StringBuilder("In ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m");
        return sb.toString();
    }
}