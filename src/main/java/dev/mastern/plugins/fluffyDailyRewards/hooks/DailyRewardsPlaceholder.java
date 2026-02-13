package dev.mastern.plugins.fluffyDailyRewards.hooks;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.managers.LanguageManager;
import dev.mastern.plugins.fluffyDailyRewards.models.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DailyRewardsPlaceholder extends PlaceholderExpansion {

    private final FluffyDailyRewards plugin;
    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 1000; // 1 second cache
    
    private final long leaderboardCacheDuration;
    private final int maxLeaderboardPositions;
    
    private CachedLeaderboard cachedStreak;
    private CachedLeaderboard cachedTotalClaims;

    public DailyRewardsPlaceholder(FluffyDailyRewards plugin) {
        this.plugin = plugin;
        this.leaderboardCacheDuration = plugin.getConfig().getLong("leaderboard.cache-duration", 300) * 1000;
        this.maxLeaderboardPositions = plugin.getConfig().getInt("leaderboard.max-positions", 10);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "daily";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "MasterN";
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        String cacheKey = player.getUniqueId() + ":" + identifier;
        CachedValue cached = cache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            return cached.getValue();
        }

        CompletableFuture<PlayerData> dataFuture = plugin.getPlayerDataManager().getPlayerData(player);
        
        try {
            PlayerData data = dataFuture.get(100, TimeUnit.MILLISECONDS);
            String result = processPlaceholder(player, data, identifier);
            cache.put(cacheKey, new CachedValue(result));
            return result;
        } catch (Exception e) {
            if (cached != null) {
                return cached.getValue();
            }
            return "";
        }
    }

    private String processPlaceholder(Player player, PlayerData data, String identifier) {
        LanguageManager lang = plugin.getLanguageManager();
        
        if (identifier.startsWith("top_streak_name_")) {
            try {
                int position = Integer.parseInt(identifier.substring("top_streak_name_".length()));
                return getTopStreakName(position);
            } catch (NumberFormatException e) {
                return "";
            }
        }
        
        if (identifier.startsWith("top_streak_value_")) {
            try {
                int position = Integer.parseInt(identifier.substring("top_streak_value_".length()));
                return getTopStreakValue(position);
            } catch (NumberFormatException e) {
                return "";
            }
        }
        
        if (identifier.startsWith("top_total_claims_name_")) {
            try {
                int position = Integer.parseInt(identifier.substring("top_total_claims_name_".length()));
                return getTopTotalClaimsName(position);
            } catch (NumberFormatException e) {
                return "";
            }
        }
        
        if (identifier.startsWith("top_total_claims_value_")) {
            try {
                int position = Integer.parseInt(identifier.substring("top_total_claims_value_".length()));
                return getTopTotalClaimsValue(position);
            } catch (NumberFormatException e) {
                return "";
            }
        }
        
        switch (identifier.toLowerCase()) {
            case "streak":
                return String.valueOf(data.getStreak());
                
            case "total_claims":
                return String.valueOf(data.getTotalClaims());
                
            case "top_streak_rank":
                return getPlayerRankByStreak(player);
                
            case "top_total_claims_rank":
                return getPlayerRankByTotalClaims(player);
                
            case "next_day":
            case "current_day":
                return String.valueOf(data.getCurrentDay());
                
            case "max_day":
                return String.valueOf(plugin.getRewardsManager().getMaxDay());
                
            case "can_claim":
                return plugin.getPlayerDataManager().canClaim(data) ? "true" : "false";
                
            case "can_claim_formatted":
                if (plugin.getPlayerDataManager().canClaim(data)) {
                    String yes = lang.getRawMessage("placeholder.yes");
                    return yes.equals("placeholder.yes") ? "Yes" : yes;
                } else {
                    String no = lang.getRawMessage("placeholder.no");
                    return no.equals("placeholder.no") ? "No" : no;
                }
                
            case "last_claim":
                if (data.getLastClaimTime() > 0) {
                    return formatTime(data.getLastClaimTime());
                }
                return lang.getRawMessage("placeholder.never");
                
            case "last_claim_date":
                if (data.getLastClaimTime() > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                    return sdf.format(new Date(data.getLastClaimTime()));
                }
                return lang.getRawMessage("placeholder.never");
                
            case "next_claim":
                if (plugin.getPlayerDataManager().canClaim(data)) {
                    return lang.getRawMessage("placeholder.now");
                }
                long nextClaim = plugin.getPlayerDataManager().getNextClaimTime(data);
                long timeLeft = nextClaim - System.currentTimeMillis();
                if (timeLeft > 0) {
                    return formatTimeLeft(timeLeft, lang);
                }
                return lang.getRawMessage("placeholder.now");
                
            case "next_claim_timestamp":
                if (plugin.getPlayerDataManager().canClaim(data)) {
                    return String.valueOf(System.currentTimeMillis());
                }
                return String.valueOf(plugin.getPlayerDataManager().getNextClaimTime(data));
                
            case "status":
                if (plugin.getPlayerDataManager().canClaim(data)) {
                    return lang.getMessage("menu.status.can-claim");
                } else if (data.getLastClaimTime() > 0) {
                    long nextClaimTime = plugin.getPlayerDataManager().getNextClaimTime(data);
                    long left = nextClaimTime - System.currentTimeMillis();
                    String formatted = formatTimeLeft(left, lang);
                    
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("time", formatted);
                    return lang.getMessage("menu.status.next-available", replacements);
                } else {
                    return lang.getMessage("menu.status.can-claim");
                }
                
            case "progress":
                int currentDay = data.getCurrentDay();
                int maxDay = plugin.getRewardsManager().getMaxDay();
                return currentDay + "/" + maxDay;
                
            default:
                return null;
        }
    }

    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        
        if (days > 0) {
            return days + "d " + hours + "h ago";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return "just now";
        }
    }

    private String formatTimeLeft(long millis, LanguageManager lang) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        
        if (days > 0) {
            Map<String, String> map = new HashMap<>();
            map.put("amount", String.valueOf(days));
            return lang.getMessage("time.days", map);
        } else if (hours > 0) {
            Map<String, String> map = new HashMap<>();
            map.put("amount", String.valueOf(hours));
            return lang.getMessage("time.hours", map);
        } else if (minutes > 0) {
            Map<String, String> map = new HashMap<>();
            map.put("amount", String.valueOf(minutes));
            return lang.getMessage("time.minutes", map);
        } else {
            Map<String, String> map = new HashMap<>();
            map.put("amount", String.valueOf(seconds));
            return lang.getMessage("time.seconds", map);
        }
    }

    private String getTopStreakName(int position) {
        List<PlayerData> topList = getOrUpdateStreakLeaderboard();
        if (position > 0 && position <= topList.size()) {
            PlayerData data = topList.get(position - 1);
            return data.getPlayerName() != null ? data.getPlayerName() : "Unknown";
        }
        return "-";
    }
    
    private String getTopStreakValue(int position) {
        List<PlayerData> topList = getOrUpdateStreakLeaderboard();
        if (position > 0 && position <= topList.size()) {
            return String.valueOf(topList.get(position - 1).getStreak());
        }
        return "0";
    }
    
    private String getTopTotalClaimsName(int position) {
        List<PlayerData> topList = getOrUpdateTotalClaimsLeaderboard();
        if (position > 0 && position <= topList.size()) {
            PlayerData data = topList.get(position - 1);
            return data.getPlayerName() != null ? data.getPlayerName() : "Unknown";
        }
        return "-";
    }
    
    private String getTopTotalClaimsValue(int position) {
        List<PlayerData> topList = getOrUpdateTotalClaimsLeaderboard();
        if (position > 0 && position <= topList.size()) {
            return String.valueOf(topList.get(position - 1).getTotalClaims());
        }
        return "0";
    }
    
    private String getPlayerRankByStreak(Player player) {
        try {
            CompletableFuture<Integer> future = plugin.getDatabaseManager().getPlayerRankByStreak(player.getUniqueId());
            Integer rank = future.get(100, TimeUnit.MILLISECONDS);
            return rank != null ? String.valueOf(rank) : "0";
        } catch (Exception e) {
            return "0";
        }
    }
    
    private String getPlayerRankByTotalClaims(Player player) {
        try {
            CompletableFuture<Integer> future = plugin.getDatabaseManager().getPlayerRankByTotalClaims(player.getUniqueId());
            Integer rank = future.get(100, TimeUnit.MILLISECONDS);
            return rank != null ? String.valueOf(rank) : "0";
        } catch (Exception e) {
            return "0";
        }
    }
    
    private List<PlayerData> getOrUpdateStreakLeaderboard() {
        if (cachedStreak == null || cachedStreak.isExpired()) {
            try {
                CompletableFuture<List<PlayerData>> future = plugin.getDatabaseManager().getTopStreak(maxLeaderboardPositions);
                List<PlayerData> data = future.get(200, TimeUnit.MILLISECONDS);
                cachedStreak = new CachedLeaderboard(data);
            } catch (Exception e) {
                if (cachedStreak != null) {
                    return cachedStreak.getData();
                }
                return new java.util.ArrayList<>();
            }
        }
        return cachedStreak.getData();
    }
    
    private List<PlayerData> getOrUpdateTotalClaimsLeaderboard() {
        if (cachedTotalClaims == null || cachedTotalClaims.isExpired()) {
            try {
                CompletableFuture<List<PlayerData>> future = plugin.getDatabaseManager().getTopTotalClaims(maxLeaderboardPositions);
                List<PlayerData> data = future.get(200, TimeUnit.MILLISECONDS);
                cachedTotalClaims = new CachedLeaderboard(data);
            } catch (Exception e) {
                if (cachedTotalClaims != null) {
                    return cachedTotalClaims.getData();
                }
                return new java.util.ArrayList<>();
            }
        }
        return cachedTotalClaims.getData();
    }

    private static class CachedValue {
        private final String value;
        private final long timestamp;

        public CachedValue(String value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        public String getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }
    
    private class CachedLeaderboard {
        private final List<PlayerData> data;
        private final long timestamp;
        
        public CachedLeaderboard(List<PlayerData> data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public List<PlayerData> getData() {
            return data;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > leaderboardCacheDuration;
        }
    }
}
