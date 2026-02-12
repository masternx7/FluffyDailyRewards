package dev.mastern.plugins.fluffyDailyRewards.managers;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.database.DatabaseManager;
import dev.mastern.plugins.fluffyDailyRewards.models.DailyReward;
import dev.mastern.plugins.fluffyDailyRewards.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerDataManager {
    
    private final FluffyDailyRewards plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    
    public PlayerDataManager(FluffyDailyRewards plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }
    
    public CompletableFuture<PlayerData> getPlayerData(Player player) {
        return getPlayerData(player, false);
    }
    
    public CompletableFuture<PlayerData> getPlayerData(Player player, boolean forceRefresh) {
        UUID uuid = player.getUniqueId();
        
        if (forceRefresh) {
            cache.remove(uuid);
        }
        
        if (cache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(cache.get(uuid));
        }
        
        return databaseManager.getPlayerData(uuid).thenApply(data -> {
            if (data.getPlayerName() == null) {
                data.setPlayerName(player.getName());
            }
            cache.put(uuid, data);
            return data;
        });
    }
    
    public CompletableFuture<Void> savePlayerData(PlayerData data) {
        return databaseManager.savePlayerData(data);
    }
    
    public void unloadPlayer(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            databaseManager.savePlayerData(data).join();
        }
    }
    
    public boolean canClaim(PlayerData data) {
        if (data.getLastClaimTime() == 0) {
            return true;
        }
        
        String resetType = plugin.getConfig().getString("reset-type", "midnight");
        long currentTime = System.currentTimeMillis();
        
        if (resetType.equalsIgnoreCase("midnight")) {
            ZoneId timeZone = getTimeZone();
            LocalDate lastClaimDate = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(data.getLastClaimTime()),
                    timeZone
            ).toLocalDate();
            
            LocalDate today = LocalDate.now(timeZone);
            return !lastClaimDate.equals(today);
        } else {
            // 24 hours mode
            long hoursPassed = (currentTime - data.getLastClaimTime()) / (1000 * 60 * 60);
            return hoursPassed >= 24;
        }
    }
    
    public long getNextClaimTime(PlayerData data) {
        if (data.getLastClaimTime() == 0) {
            return 0;
        }
        
        String resetType = plugin.getConfig().getString("reset-type", "midnight");
        
        if (resetType.equalsIgnoreCase("midnight")) {
            ZoneId timeZone = getTimeZone();
            LocalDateTime nextMidnight = LocalDate.now(timeZone).plusDays(1).atStartOfDay();
            return nextMidnight.atZone(timeZone).toInstant().toEpochMilli();
        } else {
            // 24 hours mode
            return data.getLastClaimTime() + (24 * 60 * 60 * 1000);
        }
    }
    
    public CompletableFuture<Boolean> claimReward(Player player, int day) {
        return getPlayerData(player).thenCompose(data -> {
            if (!canClaim(data)) {
                return CompletableFuture.completedFuture(false);
            }
            
            if (day != data.getCurrentDay()) {
                return CompletableFuture.completedFuture(false);
            }
            
            RewardsManager rewardsManager = plugin.getRewardsManager();
            DailyReward reward = rewardsManager.getReward(day);
            
            if (reward == null) {
                return CompletableFuture.completedFuture(false);
            }
            
            for (String command : reward.getCommands()) {
                String processedCommand = command.replace("{player}", player.getName());
                plugin.runGlobalSync(() -> 
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
                );
            }
            
            data.setLastClaimTime(System.currentTimeMillis());
            data.incrementTotalClaims();
            data.incrementStreak();
            
            int maxDay = rewardsManager.getMaxDay();
            if (day >= maxDay) {
                data.setCurrentDay(1);
            } else {
                data.setCurrentDay(day + 1);
            }
            
            return savePlayerData(data).thenApply(v -> true);
        });
    }
    
    public void checkAndResetStreak(PlayerData data) {
        if (data.getLastClaimTime() == 0) {
            return;
        }
        
        String resetType = plugin.getConfig().getString("reset-type", "midnight");
        long currentTime = System.currentTimeMillis();
        
        if (resetType.equalsIgnoreCase("midnight")) {
            ZoneId timeZone = getTimeZone();
            LocalDate lastClaimDate = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(data.getLastClaimTime()),
                    timeZone
            ).toLocalDate();
            
            LocalDate today = LocalDate.now(timeZone);
            long daysDifference = java.time.temporal.ChronoUnit.DAYS.between(lastClaimDate, today);
            
            if (daysDifference > 1) {
                data.resetStreak();
            }
        } else {
            long hoursPassed = (currentTime - data.getLastClaimTime()) / (1000 * 60 * 60);
            if (hoursPassed > 48) {
                data.resetStreak();
            }
        }
    }
    
    public void saveAll() {
        for (PlayerData data : cache.values()) {
            databaseManager.savePlayerData(data);
        }
    }
    
    private ZoneId getTimeZone() {
        String timezone = plugin.getConfig().getString("timezone", "Asia/Bangkok");
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid timezone: " + timezone + ", using Asia/Bangkok");
            return ZoneId.of("Asia/Bangkok");
        }
    }
}
