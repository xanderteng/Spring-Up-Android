package com.springup.app.data.local;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.springup.app.data.local.model.MissionModel;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class Converters {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static List<MissionModel> stringToMissionList(String data) {
        if (data == null) {
            return Collections.emptyList();
        }
        Type listType = new TypeToken<List<MissionModel>>() {}.getType();
        return gson.fromJson(data, listType);
    }

    @TypeConverter
    public static String missionListToString(List<MissionModel> missions) {
        return gson.toJson(missions);
    }
}