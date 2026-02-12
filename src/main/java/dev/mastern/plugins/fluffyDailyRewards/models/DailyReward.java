package dev.mastern.plugins.fluffyDailyRewards.models;

import org.bukkit.Material;

import java.util.List;

public class DailyReward {
    
    private final int day;
    private final String name;
    private final Material material;
    private final int customModelData;
    private final List<String> lore;
    private final List<String> commands;
    
    public DailyReward(int day, String name, Material material, int customModelData, List<String> lore, List<String> commands) {
        this.day = day;
        this.name = name;
        this.material = material;
        this.customModelData = customModelData;
        this.lore = lore;
        this.commands = commands;
    }
    
    public int getDay() {
        return day;
    }
    
    public String getName() {
        return name;
    }
    
    public Material getMaterial() {
        return material;
    }
    
    public int getCustomModelData() {
        return customModelData;
    }
    
    public List<String> getLore() {
        return lore;
    }
    
    public List<String> getCommands() {
        return commands;
    }
}
