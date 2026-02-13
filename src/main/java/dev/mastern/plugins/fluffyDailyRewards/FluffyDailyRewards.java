package dev.mastern.plugins.fluffyDailyRewards;

import dev.mastern.plugins.fluffyDailyRewards.commands.DailyCommand;
import dev.mastern.plugins.fluffyDailyRewards.database.DatabaseManager;
import dev.mastern.plugins.fluffyDailyRewards.listeners.MenuListener;
import dev.mastern.plugins.fluffyDailyRewards.listeners.PlayerListener;
import dev.mastern.plugins.fluffyDailyRewards.managers.LanguageManager;
import dev.mastern.plugins.fluffyDailyRewards.managers.MenuManager;
import dev.mastern.plugins.fluffyDailyRewards.managers.PlayerDataManager;
import dev.mastern.plugins.fluffyDailyRewards.managers.RewardsManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public final class FluffyDailyRewards extends JavaPlugin {
    
    private boolean isFolia = false;

    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private RewardsManager rewardsManager;
    private LanguageManager languageManager;
    private MenuManager menuManager;

    @Override
    public void onEnable() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
        
        getLogger().info("  _____  _       __  __          ");
        getLogger().info(" |  ___|| | _   _ / _|/ _|_   _  ");
        getLogger().info(" | |_   | || | | | |_| |_| | | | ");
        getLogger().info(" |  _|  | || |_| |  _|  _| |_| | ");
        getLogger().info(" |_|    |_| \\__,_|_| |_|  \\__, | ");
        getLogger().info("                          |___/  ");
        getLogger().info("   Daily Rewards v" + getDescription().getVersion());
        
        saveDefaultConfigs();
        
        try {
            getLogger().info("Initializing database...");
            databaseManager = new DatabaseManager(this);
            databaseManager.connect();
            
            getLogger().info("Loading managers...");
            rewardsManager = new RewardsManager(this);
            languageManager = new LanguageManager(this);
            playerDataManager = new PlayerDataManager(this, databaseManager);
            menuManager = new MenuManager(this);
            
            getLogger().info("Registering commands...");
            DailyCommand dailyCommand = new DailyCommand(this);
            getCommand("daily").setExecutor(dailyCommand);
            getCommand("daily").setTabCompleter(dailyCommand);
            
            getLogger().info("Registering listeners...");
            Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
            Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
            
            getLogger().info("FluffyDailyRewards has been enabled successfully!");
            getLogger().info("Loaded " + rewardsManager.getTotalRewards() + " rewards");
            getLogger().info("Database: " + getConfig().getString("database.type", "sqlite"));
            getLogger().info("Language: " + getConfig().getString("language", "en"));
            getLogger().info("Reset Type: " + getConfig().getString("reset-type", "midnight"));
            
            int pluginId = 29504;
            Metrics metrics = new Metrics(this, pluginId);
            
            Runnable registerPAPI = () -> {
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    try {
                        boolean registered = new dev.mastern.plugins.fluffyDailyRewards.hooks.DailyRewardsPlaceholder(this).register();
                        if (registered) {
                            getLogger().info("PlaceholderAPI integration enabled!");
                        } else {
                            getLogger().warning("Failed to register PlaceholderAPI expansion - already registered or error occurred");
                        }
                    } catch (Exception e) {
                        getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    getLogger().info("PlaceholderAPI not found, placeholders will not be available.");
                }
            };
            
            if (isFolia) {
                Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> registerPAPI.run(), 20L);
            } else {
                Bukkit.getScheduler().runTaskLater(this, registerPAPI, 20L);
            }
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize plugin!");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("  _____  _       __  __          ");
        getLogger().info(" |  ___|| | _   _ / _|/ _|_   _  ");
        getLogger().info(" | |_   | || | | | |_| |_| | | | ");
        getLogger().info(" |  _|  | || |_| |  _|  _| |_| | ");
        getLogger().info(" |_|    |_| \\__,_|_| |_|  \\__, | ");
        getLogger().info("                          |___/  ");
        getLogger().info("   Disabled Daily Rewards v" + getDescription().getVersion());
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        
        getLogger().info("Closing database connection...");
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        
        getLogger().info("FluffyDailyRewards has been disabled!");
    }
    
    private void saveDefaultConfigs() {
        saveDefaultConfig();
        
        saveResourceIfNotExists("rewards.yml");
        
        saveResourceIfNotExists("menu.yml");
        
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        saveResourceIfNotExists("lang/messages_en.yml");
        saveResourceIfNotExists("lang/messages_th.yml");
    }
    
    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                saveResource(resourcePath, false);
                getLogger().info("Created default file: " + resourcePath);
            } catch (Exception e) {
                getLogger().severe("Could not save " + resourcePath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    public void reloadPlugin() {
        reloadConfig();
        rewardsManager.reload();
        languageManager.reload();
        menuManager.reload();
        getLogger().info("Plugin configuration reloaded!");
    }
    
    // Getters
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public RewardsManager getRewardsManager() {
        return rewardsManager;
    }
    
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    public MenuManager getMenuManager() {
        return menuManager;
    }
    
    public void runSync(Player player, Runnable task) {
        if (isFolia) {
            try {
                player.getScheduler().run(this, scheduledTask -> task.run(), null);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, task);
            }
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
    
    public void runSyncLater(Player player, Runnable task, long delay) {
        if (isFolia) {
            try {
                player.getScheduler().runDelayed(this, scheduledTask -> task.run(), null, delay);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskLater(this, task, delay);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(this, task, delay);
        }
    }
    
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(this, task);
    }
    
    public void runGlobalSync(Runnable task) {
        if (isFolia) {
            try {
                Bukkit.getGlobalRegionScheduler().run(this, scheduledTask -> task.run());
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, task);
            }
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
    
    public boolean isFolia() {
        return isFolia;
    }
}
