package dev.mastern.plugins.fluffyDailyRewards.utils;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ConfigUpdater {
    
    private final FluffyDailyRewards plugin;
    private static final int LATEST_VERSION = 3;
    
    public ConfigUpdater(FluffyDailyRewards plugin) {
        this.plugin = plugin;
    }
    
    public void updateAll() {
        updateConfig();
        updateLanguageFiles();
    }
    
    private void updateConfig() {
        FileConfiguration config = plugin.getConfig();
        int currentVersion = config.getInt("config-version", 1);
        
        if (currentVersion < LATEST_VERSION) {
            try {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
                List<String> newLines = new ArrayList<>();
                boolean leaderboardAdded = false;
                boolean playtimeAdded = false;
                boolean toastAdded = false;
                
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    newLines.add(line);
                    
                    if (currentVersion < 2 && !leaderboardAdded && line.trim().startsWith("count:")) {
                        if (i + 1 < lines.size() && !lines.get(i + 1).contains("leaderboard")) {
                            newLines.add("");
                            newLines.add("# Leaderboard settings");
                            newLines.add("leaderboard:");
                            newLines.add("  # Cache duration in seconds");
                            newLines.add("  cache-duration: 300");
                            newLines.add("  # Maximum number of players to show in leaderboards (10 = 1st to 10th place)");
                            newLines.add("  max-positions: 10");
                            leaderboardAdded = true;
                        }
                    }
                    
                    if (currentVersion < 3 && !playtimeAdded && line.trim().startsWith("max-positions:")) {
                        if (i + 1 < lines.size() && !lines.get(i + 1).contains("playtime")) {
                            newLines.add("");
                            newLines.add("# Playtime requirement");
                            newLines.add("playtime:");
                            newLines.add("  # Enable playtime requirement");
                            newLines.add("  enabled: false");
                            newLines.add("  # Required playtime in seconds before claiming daily reward");
                            newLines.add("  # Examples: 60 = 1 minute, 600 = 10 minutes, 3600 = 1 hour");
                            newLines.add("  required-seconds: 600");
                            newLines.add("  # Reset playtime after each claim");
                            newLines.add("  reset-after-claim: true");
                            newLines.add("");
                            newLines.add("# Toast notification");
                            newLines.add("# Frame type: TASK (normal), GOAL (rounded), CHALLENGE (fancy)");
                            newLines.add("toast:");
                            newLines.add("  enabled: true");
                            newLines.add("  success:");
                            newLines.add("    icon: \\\"DIAMOND\\\"");
                            newLines.add("    custom-model-data: 0");
                            newLines.add("    frame: \\\"GOAL\\\"");
                            newLines.add("  fail:");
                            newLines.add("    icon: \\\"BARRIER\\\"");
                            newLines.add("    custom-model-data: 0");
                            newLines.add("    frame: \\\"TASK\\\"");
                            playtimeAdded = true;
                            toastAdded = true;
                        }
                    }
                    
                    if (line.trim().startsWith("config-version:")) {
                        newLines.set(newLines.size() - 1, "config-version: " + LATEST_VERSION);
                    }
                }
                
                if (currentVersion == 1) {
                    newLines.add(0, "");
                    newLines.add(0, "config-version: " + LATEST_VERSION);
                    newLines.add(0, "# Config version (DO NOT CHANGE THIS)");
                }
                
                writeToFile(configFile, newLines);
                
                plugin.reloadConfig();
                
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to update config file: " + e.getMessage());
            }
        }
    }
    
    private void updateLanguageFiles() {
        String language = plugin.getConfig().getString("language", "en");
        String langFile = "lang/messages_" + language + ".yml";
        updateLanguageFile(langFile, "playtime-required", 
                "&#FF0000You need {time} more playtime before claiming!");
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (langFolder.exists() && langFolder.isDirectory()) {
            File[] langFiles = langFolder.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
            if (langFiles != null) {
                for (File file : langFiles) {
                    String fileName = "lang/" + file.getName();
                    
                    if (fileName.contains("_en.yml")) {
                        updateToastMessagesEn(fileName);
                    } else if (fileName.contains("_th.yml")) {
                        updateToastMessagesTh(fileName);
                    }
                }
            }
        }
    }
    
    private void updateToastMessagesEn(String filePath) {
        updateLanguageFile(filePath, "claim-success-toast", "Daily Reward Claimed!");
        updateLanguageFile(filePath, "already-claimed-toast", "Already Claimed Today");
        updateLanguageFile(filePath, "playtime-required-toast", "More Playtime Required");
    }
    
    private void updateToastMessagesTh(String filePath) {
        updateLanguageFile(filePath, "claim-success-toast", "รับรางวัลสำเร็จแล้ว!");
        updateLanguageFile(filePath, "already-claimed-toast", "รับรางวัลวันนี้ไปแล้ว");
        updateLanguageFile(filePath, "playtime-required-toast", "ต้องเล่นให้ครบเวลาก่อน");
    }
    
    private void updateLanguageFile(String filePath, String key, String value) {
        try {
            File langFile = new File(plugin.getDataFolder(), filePath);
            if (!langFile.exists()) {
                return;
            }
            
            List<String> lines = Files.readAllLines(langFile.toPath(), StandardCharsets.UTF_8);
            
            boolean keyExists = false;
            for (String line : lines) {
                if (line.trim().startsWith(key + ":")) {
                    keyExists = true;
                    break;
                }
            }
            
            if (!keyExists) {
                List<String> newLines = new ArrayList<>();
                boolean added = false;
                
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    newLines.add(line);
                    
                    if (!added && line.trim().startsWith("not-available:")) {
                        newLines.add("  " + key + ": \"" + value + "\"");
                        added = true;
                    }
                }
                
                if (added) {
                    writeToFile(langFile, newLines);
                }
            }
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update " + filePath + ": " + e.getMessage());
        }
    }
    
    private void writeToFile(File file, List<String> lines) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (int i = 0; i < lines.size(); i++) {
                writer.write(lines.get(i));
                if (i < lines.size() - 1) {
                    writer.newLine();
                }
            }
        }
    }
}
