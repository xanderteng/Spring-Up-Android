package com.springup.app.data.local.model;

public class MissionModel {
    private int stepOrder;
    private String minigame; // "MATH", "POWER_SHAKE", "BARCODE_SCAN", "GRID_MEMORY"
    private int difficulty;
    private int requiredCompletions;
    private String targetBarcodeHash;

    public MissionModel() {}

    public MissionModel(int stepOrder, String minigame, int difficulty, int requiredCompletions, String targetBarcodeHash) {
        this.stepOrder = stepOrder;
        this.minigame = minigame;
        this.difficulty = difficulty;
        this.requiredCompletions = requiredCompletions;
        this.targetBarcodeHash = targetBarcodeHash;
    }

    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }
    public String getMinigame() { return minigame; }
    public void setMinigame(String minigame) { this.minigame = minigame; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    public int getRequiredCompletions() { return requiredCompletions; }
    public void setRequiredCompletions(int requiredCompletions) { this.requiredCompletions = requiredCompletions; }
    public String getTargetBarcodeHash() { return targetBarcodeHash; }
    public void setTargetBarcodeHash(String targetBarcodeHash) { this.targetBarcodeHash = targetBarcodeHash; }
}