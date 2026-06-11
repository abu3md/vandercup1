package com.worldcup.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Match {
    private String id;
    private String team1;
    private String team1Flag;
    private String team2;
    private String team2Flag;
    private String startTime; // ISO-8601 UTC string (e.g. 2026-06-15T18:00:00Z)
    private String status;    // SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED, REPLAYED
    private String stage;     // GROUP_STAGE, ROUND_OF_16, QUARTER_FINALS, SEMI_FINALS, FINAL
    private Integer score1;
    private Integer score2;
    private String winner;    // TEAM_1, TEAM_2, DRAW
    private boolean processed;
    private String matchMessageId;
    private String resultMessageId;

    // Default constructor for Jackson deserialization
    public Match() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTeam1() { return team1; }
    public void setTeam1(String team1) { this.team1 = team1; }

    public String getTeam1Flag() { return team1Flag; }
    public void setTeam1Flag(String team1Flag) { this.team1Flag = team1Flag; }

    public String getTeam2() { return team2; }
    public void setTeam2(String team2) { this.team2 = team2; }

    public String getTeam2Flag() { return team2Flag; }
    public void setTeam2Flag(String team2Flag) { this.team2Flag = team2Flag; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public Integer getScore1() { return score1; }
    public void setScore1(Integer score1) { this.score1 = score1; }

    public Integer getScore2() { return score2; }
    public void setScore2(Integer score2) { this.score2 = score2; }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public String getMatchMessageId() { return matchMessageId; }
    public void setMatchMessageId(String matchMessageId) { this.matchMessageId = matchMessageId; }

    public String getResultMessageId() { return resultMessageId; }
    public void setResultMessageId(String resultMessageId) { this.resultMessageId = resultMessageId; }
}
