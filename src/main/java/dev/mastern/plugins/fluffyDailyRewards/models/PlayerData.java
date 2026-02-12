package dev.mastern.plugins.fluffyDailyRewards.models;

import java.util.UUID;

public class PlayerData {
    
    private final UUID uuid;
    private String playerName;
    private int currentDay;
    private int totalClaims;
    private long lastClaimTime;
    private int streak;
    
    public PlayerData(UUID uuid, String playerName, int currentDay, int totalClaims, long lastClaimTime, int streak) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.currentDay = currentDay;
        this.totalClaims = totalClaims;
        this.lastClaimTime = lastClaimTime;
        this.streak = streak;
    }
    
    public UUID getUuid() {
        return uuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public int getCurrentDay() {
        return currentDay;
    }
    
    public void setCurrentDay(int currentDay) {
        this.currentDay = currentDay;
    }
    
    public int getTotalClaims() {
        return totalClaims;
    }
    
    public void setTotalClaims(int totalClaims) {
        this.totalClaims = totalClaims;
    }
    
    public void incrementTotalClaims() {
        this.totalClaims++;
    }
    
    public long getLastClaimTime() {
        return lastClaimTime;
    }
    
    public void setLastClaimTime(long lastClaimTime) {
        this.lastClaimTime = lastClaimTime;
    }
    
    public int getStreak() {
        return streak;
    }
    
    public void setStreak(int streak) {
        this.streak = streak;
    }
    
    public void incrementStreak() {
        this.streak++;
    }
    
    public void resetStreak() {
        this.streak = 0;
        this.currentDay = 1;
    }
}
