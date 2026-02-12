package dev.mastern.plugins.fluffyDailyRewards.managers;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.models.DailyReward;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class RewardsManager {
    
    private final FluffyDailyRewards plugin;
    private final Map<Integer, DailyReward> rewards = new HashMap<>();
    private FileConfiguration rewardsConfig;
    
    public RewardsManager(FluffyDailyRewards plugin) {
        this.plugin = plugin;
        loadRewards();
    }
    
    public void loadRewards() {
        rewards.clear();
        File rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
        
        ConfigurationSection rewardsSection = rewardsConfig.getConfigurationSection("rewards");
        if (rewardsSection == null) {
            plugin.getLogger().warning("No rewards configured!");
            return;
        }
        
        for (String key : rewardsSection.getKeys(false)) {
            try {
                int day = Integer.parseInt(key);
                ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(key);
                
                if (rewardSection != null) {
                    String name = rewardSection.getString("name", "&6Day " + day);
                    String materialName = rewardSection.getString("material", "DIAMOND");
                    Material material = Material.getMaterial(materialName);
                    if (material == null) {
                        material = Material.DIAMOND;
                    }
                    
                    int customModelData = rewardSection.getInt("custom-model-data", 0);
                    List<String> lore = rewardSection.getStringList("lore");
                    List<String> commands = rewardSection.getStringList("commands");
                    
                    DailyReward reward = new DailyReward(day, name, material, customModelData, lore, commands);
                    rewards.put(day, reward);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid day number in rewards.yml: " + key);
            }
        }
        
        plugin.getLogger().info("Loaded " + rewards.size() + " daily rewards");
    }
    
    public DailyReward getReward(int day) {
        return rewards.get(day);
    }
    
    public Map<Integer, DailyReward> getAllRewards() {
        return new HashMap<>(rewards);
    }
    
    public int getTotalRewards() {
        return rewards.size();
    }
    
    public int getMaxDay() {
        return rewards.keySet().stream().max(Integer::compare).orElse(7);
    }
    
    public void reload() {
        loadRewards();
    }
}
