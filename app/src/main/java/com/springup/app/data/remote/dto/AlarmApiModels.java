package com.springup.app.data.remote.dto;

import java.util.List;

public class AlarmApiModels {

    public static class MissionConfigDto {
        public int stepOrder;
        public String minigame;
        public int difficulty;
        public int requiredCompletions;
        public String targetBarcodeHash;

        public MissionConfigDto(int stepOrder, String minigame, int difficulty, int requiredCompletions, String targetBarcodeHash) {
            this.stepOrder = stepOrder;
            this.minigame = minigame;
            this.difficulty = difficulty;
            this.requiredCompletions = requiredCompletions;
            this.targetBarcodeHash = targetBarcodeHash;
        }
    }

    public static class AlarmRequest {
        public String title;
        public String alarmTime; // "HH:mm:ss"
        public boolean isEnabled;
        public int repeatDaysMask;
        public boolean wakeUpCheckEnabled;
        public int wakeUpCheckDelayMinutes;
        public List<MissionConfigDto> missions;
        public boolean vibrate;
        public int volumeLevel;

        public AlarmRequest(String title, String alarmTime, boolean isEnabled, int repeatDaysMask,
                            boolean wakeUpCheckEnabled, int wakeUpCheckDelayMinutes,
                            List<MissionConfigDto> missions, boolean vibrate, int volumeLevel) {
            this.title = title;
            this.alarmTime = alarmTime;
            this.isEnabled = isEnabled;
            this.repeatDaysMask = repeatDaysMask;
            this.wakeUpCheckEnabled = wakeUpCheckEnabled;
            this.wakeUpCheckDelayMinutes = wakeUpCheckDelayMinutes;
            this.missions = missions;
            this.vibrate = vibrate;
            this.volumeLevel = volumeLevel;
        }
    }

    public static class AlarmResponse {
        public Long id;
        public String title;
        public String alarmTime;
        public boolean isEnabled;
        public int repeatDaysMask;
        public boolean wakeUpCheckEnabled;
        public int wakeUpCheckDelayMinutes;
        public List<MissionConfigDto> missions;
        public boolean vibrate;
        public int volumeLevel;
    }

    public static class ChallengeResponse {
        public String type;
        public int difficulty;
        public String challengePayload;
        public List<Integer> memorySequence;
    }

    public static class VerificationRequest {
        public Long alarmId;
        public String minigameType;
        public String submittedSolution;
        public List<Integer> submittedSequence;
        public int secondsTaken;
        public int snoozeCount;

        public VerificationRequest(Long alarmId, String minigameType, String submittedSolution,
                                   List<Integer> submittedSequence, int secondsTaken, int snoozeCount) {
            this.alarmId = alarmId;
            this.minigameType = minigameType;
            this.submittedSolution = submittedSolution;
            this.submittedSequence = submittedSequence;
            this.secondsTaken = secondsTaken;
            this.snoozeCount = snoozeCount;
        }
    }

    public static class VerificationResponse {
        public boolean isDismissed;
        public int currentStreak;
        public String message;
    }
}