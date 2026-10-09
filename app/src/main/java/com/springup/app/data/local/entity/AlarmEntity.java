package com.springup.app.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.springup.app.data.local.model.MissionModel;

import java.util.List;

@Entity(tableName = "alarms")
public class AlarmEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "server_id")
    private Long serverId;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "hour")
    private int hour;

    @ColumnInfo(name = "minute")
    private int minute;

    @ColumnInfo(name = "is_enabled")
    private boolean isEnabled;

    @ColumnInfo(name = "repeat_days_mask")
    private int repeatDaysMask;

    // Wake Up Check Watchdog
    @ColumnInfo(name = "wake_up_check_enabled")
    private boolean wakeUpCheckEnabled;

    @ColumnInfo(name = "wake_up_check_delay_minutes")
    private int wakeUpCheckDelayMinutes;

    // Multiple Mission Chain
    @ColumnInfo(name = "missions")
    private List<MissionModel> missions;

    @ColumnInfo(name = "vibrate")
    private boolean vibrate;

    @ColumnInfo(name = "volume_level")
    private int volumeLevel;

    @ColumnInfo(name = "is_synced")
    private boolean isSynced;

    public AlarmEntity(String title, int hour, int minute, boolean isEnabled,
                       int repeatDaysMask, boolean wakeUpCheckEnabled, int wakeUpCheckDelayMinutes,
                       List<MissionModel> missions, boolean vibrate, int volumeLevel) {
        this.title = title;
        this.hour = hour;
        this.minute = minute;
        this.isEnabled = isEnabled;
        this.repeatDaysMask = repeatDaysMask;
        this.wakeUpCheckEnabled = wakeUpCheckEnabled;
        this.wakeUpCheckDelayMinutes = wakeUpCheckDelayMinutes;
        this.missions = missions;
        this.vibrate = vibrate;
        this.volumeLevel = volumeLevel;
        this.isSynced = false;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getHour() { return hour; }
    public void setHour(int hour) { this.hour = hour; }
    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }
    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }
    public int getRepeatDaysMask() { return repeatDaysMask; }
    public void setRepeatDaysMask(int repeatDaysMask) { this.repeatDaysMask = repeatDaysMask; }
    public boolean isWakeUpCheckEnabled() { return wakeUpCheckEnabled; }
    public void setWakeUpCheckEnabled(boolean wakeUpCheckEnabled) { this.wakeUpCheckEnabled = wakeUpCheckEnabled; }
    public int getWakeUpCheckDelayMinutes() { return wakeUpCheckDelayMinutes; }
    public void setWakeUpCheckDelayMinutes(int wakeUpCheckDelayMinutes) { this.wakeUpCheckDelayMinutes = wakeUpCheckDelayMinutes; }
    public List<MissionModel> getMissions() { return missions; }
    public void setMissions(List<MissionModel> missions) { this.missions = missions; }
    public boolean isVibrate() { return vibrate; }
    public void setVibrate(boolean vibrate) { this.vibrate = vibrate; }
    public int getVolumeLevel() { return volumeLevel; }
    public void setVolumeLevel(int volumeLevel) { this.volumeLevel = volumeLevel; }
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
}