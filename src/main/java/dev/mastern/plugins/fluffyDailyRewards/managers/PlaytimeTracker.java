package dev.mastern.plugins.fluffyDailyRewards.managers;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaytimeTracker {
    
    private final FluffyDailyRewards plugin;
    private final Map<UUID, Long> joinTimes;
    private final Map<UUID, Long> accumulatedPlaytime;
    private boolean enabled;
    private long requiredSeconds;
    private boolean resetAfterClaim;
    
    public PlaytimeTracker(FluffyDailyRewards plugin) {
        this.plugin = plugin;
        this.joinTimes = new ConcurrentHashMap<>();
        this.accumulatedPlaytime = new ConcurrentHashMap<>();
        loadConfig();
    }
    
    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("playtime.enabled", false);
        this.requiredSeconds = plugin.getConfig().getLong("playtime.required-seconds", 600);
        this.resetAfterClaim = plugin.getConfig().getBoolean("playtime.reset-after-claim", true);
    }
    
    public void reload() {
        loadConfig();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public long getRequiredSeconds() {
        return requiredSeconds;
    }
    
    public void onPlayerJoin(Player player) {
        if (!enabled) return;
        
        UUID uuid = player.getUniqueId();
        joinTimes.put(uuid, System.currentTimeMillis());
        
        if (!accumulatedPlaytime.containsKey(uuid)) {
            accumulatedPlaytime.put(uuid, 0L);
        }
    }
    
    public void onPlayerQuit(Player player) {
        if (!enabled) return;
        
        UUID uuid = player.getUniqueId();
        updatePlaytime(uuid);
        joinTimes.remove(uuid);
    }
    
    private void updatePlaytime(UUID uuid) {
        Long joinTime = joinTimes.get(uuid);
        if (joinTime != null) {
            long sessionTime = (System.currentTimeMillis() - joinTime) / 1000;
            long currentPlaytime = accumulatedPlaytime.getOrDefault(uuid, 0L);
            accumulatedPlaytime.put(uuid, currentPlaytime + sessionTime);
        }
    }
    
    public long getPlaytime(Player player) {
        if (!enabled) return requiredSeconds;
        
        UUID uuid = player.getUniqueId();
        
        long accumulated = accumulatedPlaytime.getOrDefault(uuid, 0L);
        
        Long joinTime = joinTimes.get(uuid);
        if (joinTime != null) {
            long currentSessionTime = (System.currentTimeMillis() - joinTime) / 1000;
            return accumulated + currentSessionTime;
        }
        
        return accumulated;
    }
    
    public boolean hasMetRequirement(Player player) {
        if (!enabled) return true;
        return getPlaytime(player) >= requiredSeconds;
    }
    
    public long getRemainingPlaytime(Player player) {
        if (!enabled) return 0;
        
        long current = getPlaytime(player);
        long remaining = requiredSeconds - current;
        return Math.max(0, remaining);
    }
    
    public void resetPlaytime(Player player) {
        if (!enabled || !resetAfterClaim) return;
        
        UUID uuid = player.getUniqueId();
        accumulatedPlaytime.put(uuid, 0L);
        
        if (joinTimes.containsKey(uuid)) {
            joinTimes.put(uuid, System.currentTimeMillis());
        }
    }
    
    public String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + "m " + remainingSeconds + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
    
    public String formatRemainingTime(Player player, LanguageManager lang) {
        long remaining = getRemainingPlaytime(player);
        
        if (remaining >= 3600) {
            long hours = remaining / 3600;
            Map<String, String> map = new HashMap<>();
            map.put("amount", String.valueOf(hours));
            return lang.getMessage("time.hours", map);
        } else if (remaining >= 60) {
            long minutes = remaining / 60;
            Map<String, String> map = new HashMap<>();
            map.put("amount", String.valueOf(minutes));
            return lang.getMessage("time.minutes", map);
        } else {
            Map<String, String> map = new HashMap<>();
            map.put("amount", String.valueOf(remaining));
            return lang.getMessage("time.seconds", map);
        }
    }
    
    public void clearAll() {
        joinTimes.clear();
        accumulatedPlaytime.clear();
    }
}
