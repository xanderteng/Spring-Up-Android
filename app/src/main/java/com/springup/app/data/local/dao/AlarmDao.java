package com.springup.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.springup.app.data.local.entity.AlarmEntity;

import java.util.List;

@Dao
public interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    LiveData<List<AlarmEntity>> getAllAlarmsLiveData();

    @Query("SELECT * FROM alarms WHERE is_enabled = 1 ORDER BY hour ASC, minute ASC")
    List<AlarmEntity> getEnabledAlarmsSync();

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    AlarmEntity getAlarmById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AlarmEntity alarm);

    @Update
    void update(AlarmEntity alarm);

    @Delete
    void delete(AlarmEntity alarm);

    @Query("UPDATE alarms SET is_enabled = :isEnabled WHERE id = :id")
    void updateAlarmStatus(long id, boolean isEnabled);
}