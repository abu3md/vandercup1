package com.worldcup.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserStats {
    private String userId;
    private double points;
    private int correctPredictions;
    private int incorrectPredictions;
    private int gamblingUsed;
    private int gamblingWins;
    private int sabotageUsed;
    private int sabotageWins;
    private int scoutingUsed;

    public UserStats() {}

    public UserStats(String userId) {
        this.userId = userId;
        this.points = 0.0;
        this.correctPredictions = 0;
        this.incorrectPredictions = 0;
        this.gamblingUsed = 0;
        this.gamblingWins = 0;
        this.sabotageUsed = 0;
        this.sabotageWins = 0;
        this.scoutingUsed = 0;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getPoints() { return points; }
    public void setPoints(double points) { this.points = points; }

    public int getCorrectPredictions() { return correctPredictions; }
    public void setCorrectPredictions(int correctPredictions) { this.correctPredictions = correctPredictions; }

    public int getIncorrectPredictions() { return incorrectPredictions; }
    public void setIncorrectPredictions(int incorrectPredictions) { this.incorrectPredictions = incorrectPredictions; }

    public int getGamblingUsed() { return gamblingUsed; }
    public void setGamblingUsed(int gamblingUsed) { this.gamblingUsed = gamblingUsed; }

    public int getGamblingWins() { return gamblingWins; }
    public void setGamblingWins(int gamblingWins) { this.gamblingWins = gamblingWins; }

    public int getSabotageUsed() { return sabotageUsed; }
    public void setSabotageUsed(int sabotageUsed) { this.sabotageUsed = sabotageUsed; }

    public int getSabotageWins() { return sabotageWins; }
    public void setSabotageWins(int sabotageWins) { this.sabotageWins = sabotageWins; }

    public int getScoutingUsed() { return scoutingUsed; }
    public void setScoutingUsed(int scoutingUsed) { this.scoutingUsed = scoutingUsed; }

    // Helper methods to increment usage count
    public void incrementGamblingUsed() { this.gamblingUsed++; }
    public void incrementGamblingWins() { this.gamblingWins++; }
    public void incrementSabotageUsed() { this.sabotageUsed++; }
    public void incrementSabotageWins() { this.sabotageWins++; }
    public void incrementScoutingUsed() { this.scoutingUsed++; }
}
