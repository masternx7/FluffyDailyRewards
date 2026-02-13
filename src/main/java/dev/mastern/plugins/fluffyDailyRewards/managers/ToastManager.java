package dev.mastern.plugins.fluffyDailyRewards.managers;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

public class ToastManager {
    
    private final FluffyDailyRewards plugin;
    private boolean enabled;
    private String successTitle;
    private String successIcon;
    private int successCustomModelData;
    private String successFrame;
    private String failTitle;
    private String failIcon;
    private int failCustomModelData;
    private String failFrame;
    
    public ToastManager(FluffyDailyRewards plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("toast.enabled", true);
        this.successIcon = plugin.getConfig().getString("toast.success.icon", "DIAMOND");
        this.successCustomModelData = plugin.getConfig().getInt("toast.success.custom-model-data", 0);
        this.successFrame = plugin.getConfig().getString("toast.success.frame", "GOAL");
        this.failIcon = plugin.getConfig().getString("toast.fail.icon", "BARRIER");
        this.failCustomModelData = plugin.getConfig().getInt("toast.fail.custom-model-data", 0);
        this.failFrame = plugin.getConfig().getString("toast.fail.frame", "TASK");
    }
    
    public void reload() {
        loadConfig();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void showSuccessToast(Player player, String message) {
        if (!enabled) return;
        showToast(player, successTitle, message, successIcon, successCustomModelData, successFrame);
    }
    
    public void showFailToast(Player player, String message) {
        if (!enabled) return;
        showToast(player, failTitle, message, failIcon, failCustomModelData, failFrame);
    }
    
    private void showToast(Player player, String title, String description, String iconStr, int customModelData, String frameStr) {
        plugin.runSync(player, () -> {
            try {
                Material icon = Material.matchMaterial(iconStr);
                if (icon == null) {
                    icon = Material.DIAMOND;
                }
                
                String frameType = frameStr.toUpperCase();
                if (!frameType.equals("TASK") && !frameType.equals("GOAL") && !frameType.equals("CHALLENGE")) {
                    frameType = "TASK";
                }
                
                NamespacedKey key = new NamespacedKey(plugin, "toast_" + System.currentTimeMillis() + "_" + player.getUniqueId().hashCode());
                
                try {
                    String advancementJson = createAdvancementJson(title, description, icon.name(), customModelData, frameType);
                    
                    Advancement advancement = loadAdvancement(key, advancementJson);
                    
                    if (advancement != null) {
                        AdvancementProgress progress = player.getAdvancementProgress(advancement);
                        if (!progress.isDone()) {
                            progress.getRemainingCriteria().forEach(progress::awardCriteria);
                        }
                        
                        plugin.runSyncLater(player, () -> {
                            try {
                                Bukkit.getUnsafe().removeAdvancement(key);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }, 60L);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to show toast notification: " + e.getMessage());
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error creating toast: " + e.getMessage());
            }
        });
    }
    
    private String createAdvancementJson(String title, String description, String icon, int customModelData, String frame) {
        String titleComponent = parseHexToJsonComponent(description);
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"display\":{");
        json.append("\"icon\":{\"id\":\"minecraft:").append(icon.toLowerCase()).append("\"");
        if (customModelData > 0) {
            json.append(",\"components\":{\"minecraft:custom_model_data\":").append(customModelData).append("}");
        }
        json.append("},");
        json.append("\"title\":").append(titleComponent).append(",");
        json.append("\"description\":{\"text\":\"\"},");
        json.append("\"background\":\"minecraft:textures/gui/advancements/backgrounds/stone.png\",");
        json.append("\"frame\":\"").append(frame.toLowerCase()).append("\",");
        json.append("\"show_toast\":true,");
        json.append("\"announce_to_chat\":false,");
        json.append("\"hidden\":true");
        json.append("},");
        json.append("\"criteria\":{");
        json.append("\"trigger\":{\"trigger\":\"minecraft:impossible\"}");
        json.append("}");
        json.append("}");
        
        return json.toString();
    }
    
    private String parseHexToJsonComponent(String text) {
        if (!text.contains("&#")) {
            String escaped = text
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            return "{\"text\":\"" + escaped + "\"}";
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        StringBuilder component = new StringBuilder();
        component.append("[");
        
        int lastEnd = 0;
        boolean first = true;
        String currentColor = null;
        
        while (matcher.find()) {
            String beforeColor = text.substring(lastEnd, matcher.start());
            if (!beforeColor.isEmpty()) {
                if (!first) component.append(",");
                String escaped = beforeColor
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
                component.append("{\"text\":\"").append(escaped).append("\"");
                if (currentColor != null) {
                    component.append(",\"color\":\"#").append(currentColor).append("\"");
                }
                component.append("}");
                first = false;
            }
            currentColor = matcher.group(1);
            lastEnd = matcher.end();
        }
        
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            if (!remaining.isEmpty()) {
                if (!first) component.append(",");
                String escaped = remaining
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
                component.append("{\"text\":\"").append(escaped).append("\"");
                if (currentColor != null) {
                    component.append(",\"color\":\"#").append(currentColor).append("\"");
                }
                component.append("}");
            }
        }
        
        component.append("]");
        return component.toString();
    }
    
    private Advancement loadAdvancement(NamespacedKey key, String json) {
        try {
            return Bukkit.getUnsafe().loadAdvancement(key, json);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load advancement: " + e.getMessage());
            return null;
        }
    }
}
