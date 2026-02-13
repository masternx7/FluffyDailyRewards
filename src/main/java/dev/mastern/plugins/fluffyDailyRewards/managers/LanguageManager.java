package dev.mastern.plugins.fluffyDailyRewards.managers;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    
    private final FluffyDailyRewards plugin;
    private FileConfiguration langConfig;
    private final Map<String, String> messages = new HashMap<>();
    
    public LanguageManager(FluffyDailyRewards plugin) {
        this.plugin = plugin;
        loadLanguage();
    }
    
    public void loadLanguage() {
        String lang = plugin.getConfig().getString("language", "en");
        File langFile = new File(plugin.getDataFolder() + File.separator + "lang", "messages_" + lang + ".yml");
        
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file not found: " + langFile.getName() + ", using English");
            langFile = new File(plugin.getDataFolder() + File.separator + "lang", "messages_en.yml");
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        loadMessages();
        plugin.getLogger().info("Loaded language: " + lang);
    }
    
    private void loadMessages() {
        messages.clear();
        for (String key : langConfig.getKeys(true)) {
            if (langConfig.isString(key)) {
                messages.put(key, langConfig.getString(key));
            }
        }
    }
    
    public String getMessage(String key) {
        String message = messages.getOrDefault(key, key);
        
        if (!key.endsWith("-toast") && (key.startsWith("general.") || key.startsWith("rewards."))) {
            String prefix = messages.getOrDefault("prefix", "");
            if (!message.contains("{prefix}")) {
                message = prefix + " " + message;
            }
        }
        
        message = message.replace("{prefix}", messages.getOrDefault("prefix", ""));
        return colorize(message);
    }
    
    public String getMessage(String key, Map<String, String> replacements) {
        String message = messages.getOrDefault(key, key);
        
        if (!key.endsWith("-toast") && (key.startsWith("general.") || key.startsWith("rewards."))) {
            String prefix = messages.getOrDefault("prefix", "");
            if (!message.contains("{prefix}")) {
                message = prefix + " " + message;
            }
        }
        
        message = message.replace("{prefix}", messages.getOrDefault("prefix", ""));
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return colorize(message);
    }
    
    public java.util.List<String> getMessageList(String key) {
        if (langConfig.isList(key)) {
            return langConfig.getStringList(key);
        }
        return new java.util.ArrayList<>();
    }
    
    public String getPrefix() {
        return getMessage("prefix");
    }
    
    public String colorize(String text) {
        text = translateHexColorCodes(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    private String translateHexColorCodes(String message) {
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
        
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + group).toString());
        }
        
        return matcher.appendTail(buffer).toString();
    }
    
    public void reload() {
        loadLanguage();
    }
    
    public String getRawMessage(String key) {
        return messages.getOrDefault(key, key);
    }
    
    public String getRawMessage(String key, Map<String, String> replacements) {
        String message = messages.getOrDefault(key, key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
}
