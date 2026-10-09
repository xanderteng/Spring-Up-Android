package com.springup.app.data.remote;

import com.springup.app.data.remote.dto.AlarmApiModels.*;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface SpringUpApiService {

    @GET("/api/v1/alarms")
    Call<List<AlarmResponse>> getAllAlarms();

    @POST("/api/v1/alarms")
    Call<AlarmResponse> createAlarm(@Body AlarmRequest request);

    @PATCH("/api/v1/alarms/{id}/toggle")
    Call<AlarmResponse> toggleAlarm(@Path("id") Long id, @Query("enabled") boolean enabled);

    @DELETE("/api/v1/alarms/{id}")
    Call<Void> deleteAlarm(@Path("id") Long id);

    @GET("/api/v1/minigames/challenge")
    Call<ChallengeResponse> getChallenge(@Query("type") String type, @Query("difficulty") int difficulty);

    @POST("/api/v1/minigames/verify")
    Call<VerificationResponse> verifySolution(@Body VerificationRequest request);
}