package dev.mastern.plugins.fluffyDailyRewards.managers;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.models.DailyReward;
import dev.mastern.plugins.fluffyDailyRewards.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MenuManager {
    
    private final FluffyDailyRewards plugin;
    private FileConfiguration menuConfig;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    
    public MenuManager(FluffyDailyRewards plugin) {
        this.plugin = plugin;
        loadMenu();
    }
    
    public void loadMenu() {
        File menuFile = new File(plugin.getDataFolder(), "menu.yml");
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
    }
    
    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }
    
    public String getMenuTitle() {
        return menuConfig.getString("menu.title", "&6&lDaily Rewards");
    }
    
    private List<Integer> parseSlots(String slotsString) {
        List<Integer> slots = new ArrayList<>();
        if (slotsString == null || slotsString.isEmpty()) {
            return slots;
        }
        
        String[] parts = slotsString.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                for (int i = start; i <= end; i++) {
                    slots.add(i);
                }
            } else {
                slots.add(Integer.parseInt(part));
            }
        }
        
        return slots;
    }
    
    public void openMenu(Player player, int page) {
        plugin.getPlayerDataManager().getPlayerData(player, true).thenAccept(data -> {
            plugin.getPlayerDataManager().checkAndResetStreak(data);
            
            plugin.runSync(player, () -> {
                String title = colorize(menuConfig.getString("menu.title", "&6&lDaily Rewards"));
                int size = menuConfig.getInt("menu.size", 54);
                Inventory inventory = Bukkit.createInventory(null, size, title);
                
                List<Integer> rewardSlots;
                if (menuConfig.isString("menu.reward-slots")) {
                    rewardSlots = parseSlots(menuConfig.getString("menu.reward-slots"));
                } else {
                    rewardSlots = menuConfig.getIntegerList("menu.reward-slots");
                }
                int itemsPerPage = rewardSlots.size();
                
                Map<Integer, DailyReward> allRewards = plugin.getRewardsManager().getAllRewards();
                List<Integer> sortedDays = new ArrayList<>(allRewards.keySet());
                Collections.sort(sortedDays);
                
                int totalPages = (int) Math.ceil((double) sortedDays.size() / itemsPerPage);
                int startIndex = page * itemsPerPage;
                int endIndex = Math.min(startIndex + itemsPerPage, sortedDays.size());
                
                for (int i = startIndex; i < endIndex; i++) {
                    int day = sortedDays.get(i);
                    DailyReward reward = allRewards.get(day);
                    int slotIndex = i - startIndex;
                    if (slotIndex < rewardSlots.size()) {
                        int slot = rewardSlots.get(slotIndex);
                        inventory.setItem(slot, createRewardItem(reward, data));
                    }
                }
                
                addDecorations(inventory);
                
                addInfoItem(inventory, player, data);
                
                addNavigationButtons(inventory, page, totalPages);
                
                playerPages.put(player.getUniqueId(), page);
                player.openInventory(inventory);
                
                playSound(player, "sounds.open-menu", Sound.BLOCK_CHEST_OPEN);
            });
        });
    }
    
    private ItemStack createRewardItem(DailyReward reward, PlayerData data) {
        int day = reward.getDay();
        int currentDay = data.getCurrentDay();
        boolean claimed = day < currentDay;
        boolean canClaim = plugin.getPlayerDataManager().canClaim(data);
        boolean available = day == currentDay && canClaim;
        boolean waiting = day == currentDay && !canClaim;
        boolean locked = day > currentDay;
        
        Material material = reward.getMaterial();
        String name = reward.getName();
        List<String> lore = new ArrayList<>();
        lore.addAll(reward.getLore());
        int customModelData = reward.getCustomModelData();
        
        ConfigurationSection statesConfig = menuConfig.getConfigurationSection("reward-states");
        
        if (claimed && statesConfig != null) {
            ConfigurationSection claimedConfig = statesConfig.getConfigurationSection("claimed");
            if (claimedConfig != null) {
                String materialOverride = claimedConfig.getString("material");
                if (materialOverride != null) {
                    Material mat = Material.getMaterial(materialOverride);
                    if (mat != null) material = mat;
                }
                if (claimedConfig.contains("custom-model-data")) {
                    customModelData = claimedConfig.getInt("custom-model-data", 0);
                }
                String stateName = claimedConfig.getString("display_name");
                if (stateName != null) {
                    name = stateName.replace("{day}", String.valueOf(day))
                                    .replace("{reward_name}", reward.getName());
                }
                lore.addAll(colorizeList(claimedConfig.getStringList("lore")));
            }
        } else if (available && statesConfig != null) {
            ConfigurationSection availableConfig = statesConfig.getConfigurationSection("available");
            if (availableConfig != null) {
                String stateName = availableConfig.getString("display_name");
                if (stateName != null) {
                    name = stateName.replace("{day}", String.valueOf(day))
                                    .replace("{reward_name}", reward.getName());
                }
                lore.addAll(colorizeList(availableConfig.getStringList("lore")));
            }
        } else if (waiting && statesConfig != null) {
            ConfigurationSection waitingConfig = statesConfig.getConfigurationSection("waiting");
            if (waitingConfig != null) {
                String stateName = waitingConfig.getString("display_name");
                if (stateName != null) {
                    name = stateName.replace("{day}", String.valueOf(day))
                                    .replace("{reward_name}", reward.getName());
                }
                lore.addAll(colorizeList(waitingConfig.getStringList("lore")));
            } else {
                ConfigurationSection claimedConfig = statesConfig.getConfigurationSection("claimed");
                if (claimedConfig != null) {
                    String materialOverride = claimedConfig.getString("material");
                    if (materialOverride != null) {
                        Material mat = Material.getMaterial(materialOverride);
                        if (mat != null) material = mat;
                    }
                    if (claimedConfig.contains("custom-model-data")) {
                        customModelData = claimedConfig.getInt("custom-model-data", 0);
                    }
                    String stateName = claimedConfig.getString("display_name");
                    if (stateName != null) {
                        name = stateName.replace("{day}", String.valueOf(day))
                                        .replace("{reward_name}", reward.getName());
                    }
                    lore.addAll(colorizeList(claimedConfig.getStringList("lore")));
                }
            }
        } else if (locked && statesConfig != null) {
            ConfigurationSection lockedConfig = statesConfig.getConfigurationSection("locked");
            if (lockedConfig != null) {
                String stateName = lockedConfig.getString("display_name");
                if (stateName != null) {
                    name = stateName.replace("{day}", String.valueOf(day))
                                    .replace("{reward_name}", reward.getName());
                }
                lore.addAll(colorizeList(lockedConfig.getStringList("lore")));
            }
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(name));
            meta.setLore(colorizeList(lore));
            
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            
            if (statesConfig != null) {
                String section = null;
                if (claimed) section = "claimed";
                else if (available) section = "available";
                else if (waiting) section = "waiting";
                else if (locked) section = "locked";
                
                if (section != null) {
                    boolean glow = statesConfig.getBoolean(section + ".glow", false);
                    if (!glow) {
                        glow = statesConfig.getBoolean(section + ".enchanted", false);
                    }
                    if (glow) {
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }
                }
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private void addDecorations(Inventory inventory) {
        ConfigurationSection decorations = menuConfig.getConfigurationSection("decorations");
        if (decorations == null) return;
        
        for (String key : decorations.getKeys(false)) {
            ConfigurationSection decoration = decorations.getConfigurationSection(key);
            if (decoration != null) {
                String materialName = decoration.getString("material", "BLACK_STAINED_GLASS_PANE");
                Material material = Material.getMaterial(materialName);
                if (material == null) material = Material.BLACK_STAINED_GLASS_PANE;
                
                String name = decoration.getString("display_name", decoration.getString("name", " "));
                int customModelData = decoration.getInt("custom-model-data", 0);
                List<Integer> slots;
                if (decoration.isString("slots")) {
                    slots = parseSlots(decoration.getString("slots"));
                } else {
                    slots = decoration.getIntegerList("slots");
                }
                
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(colorize(name));
                    if (customModelData > 0) {
                        meta.setCustomModelData(customModelData);
                    }
                    item.setItemMeta(meta);
                }
                
                for (int slot : slots) {
                    inventory.setItem(slot, item);
                }
            }
        }
    }
    
    private void addInfoItem(Inventory inventory, Player player, PlayerData data) {
        ConfigurationSection infoConfig = menuConfig.getConfigurationSection("decorations.info");
        if (infoConfig == null) return;
        
        int slot = infoConfig.getInt("slot", 4);
        String materialName = infoConfig.getString("material", "PLAYER_HEAD");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.PLAYER_HEAD;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("player", player.getName());
            replacements.put("streak", String.valueOf(data.getStreak()));
            replacements.put("total_claims", String.valueOf(data.getTotalClaims()));
            replacements.put("next_day", String.valueOf(data.getCurrentDay()));
            
            String status = getStatusMessage(data);
            replacements.put("status", status);
            
            String name = infoConfig.getString("display_name", infoConfig.getString("name", "&e{player}"));
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                name = name.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            meta.setDisplayName(colorize(name));
            
            List<String> lore = infoConfig.getStringList("lore");
            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    line = line.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                processedLore.add(colorize(line));
            }
            meta.setLore(processedLore);
            
            if (meta instanceof SkullMeta) {
                ((SkullMeta) meta).setOwningPlayer(player);
            }
            
            int customModelData = infoConfig.getInt("custom-model-data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            
            item.setItemMeta(meta);
        }
        
        inventory.setItem(slot, item);
    }
    
    private String getStatusMessage(PlayerData data) {
        LanguageManager lang = plugin.getLanguageManager();
        if (plugin.getPlayerDataManager().canClaim(data)) {
            return lang.getMessage("menu.status.can-claim");
        } else if (data.getLastClaimTime() > 0) {
            long nextClaim = plugin.getPlayerDataManager().getNextClaimTime(data);
            long timeLeft = nextClaim - System.currentTimeMillis();
            String timeFormatted = formatTime(timeLeft);
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("time", timeFormatted);
            return lang.getMessage("menu.status.next-available", replacements);
        } else {
            return lang.getMessage("menu.status.can-claim");
        }
    }
    
    private String formatTime(long millis) {
        LanguageManager lang = plugin.getLanguageManager();
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
    
    private void addNavigationButtons(Inventory inventory, int currentPage, int totalPages) {
        ConfigurationSection navConfig = menuConfig.getConfigurationSection("navigation");
        if (navConfig == null) return;
        
        ConfigurationSection prevConfig = navConfig.getConfigurationSection("previous-page");
        if (prevConfig != null) {
            boolean hasPrevPage = currentPage > 0;
            if (hasPrevPage) {
                List<Integer> slots = getNavigationSlots(prevConfig);
                ItemStack item = createNavigationButton(prevConfig);
                for (int slot : slots) {
                    inventory.setItem(slot, item);
                }
            }
        }
        
        ConfigurationSection nextConfig = navConfig.getConfigurationSection("next-page");
        if (nextConfig != null) {
            boolean hasNextPage = currentPage < totalPages - 1;
            if (hasNextPage) {
                List<Integer> slots = getNavigationSlots(nextConfig);
                ItemStack item = createNavigationButton(nextConfig);
                for (int slot : slots) {
                    inventory.setItem(slot, item);
                }
            }
        }
    }
    
    private List<Integer> getNavigationSlots(ConfigurationSection config) {
        if (config.isString("slots")) {
            return parseSlots(config.getString("slots"));
        } else if (config.isList("slots")) {
            return config.getIntegerList("slots");
        } else if (config.contains("slot")) {
            List<Integer> slots = new ArrayList<>();
            slots.add(config.getInt("slot"));
            return slots;
        }
        return new ArrayList<>();
    }
    
    private ItemStack createNavigationButton(ConfigurationSection config) {
        String materialName = config.getString("material", "ARROW");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.ARROW;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = config.getString("display_name", config.getString("name", ""));
            meta.setDisplayName(colorize(displayName));
            meta.setLore(colorizeList(config.getStringList("lore")));
            
            int customModelData = config.getInt("custom-model-data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    public int getPlayerPage(UUID uuid) {
        return playerPages.getOrDefault(uuid, 0);
    }
    
    public void removePlayer(UUID uuid) {
        playerPages.remove(uuid);
    }
    
    public boolean isRewardSlot(int slot) {
        List<Integer> rewardSlots;
        if (menuConfig.isString("menu.reward-slots")) {
            rewardSlots = parseSlots(menuConfig.getString("menu.reward-slots"));
        } else {
            rewardSlots = menuConfig.getIntegerList("menu.reward-slots");
        }
        return rewardSlots.contains(slot);
    }
    
    public boolean isNavigationSlot(int slot) {
        ConfigurationSection navConfig = menuConfig.getConfigurationSection("navigation");
        if (navConfig == null) return false;
        
        ConfigurationSection prevConfig = navConfig.getConfigurationSection("previous-page");
        if (prevConfig != null) {
            List<Integer> prevSlots = getNavigationSlots(prevConfig);
            if (prevSlots.contains(slot)) return true;
        }
        
        ConfigurationSection nextConfig = navConfig.getConfigurationSection("next-page");
        if (nextConfig != null) {
            List<Integer> nextSlots = getNavigationSlots(nextConfig);
            if (nextSlots.contains(slot)) return true;
        }
        
        return false;
    }
    
    public List<Integer> getPreviousPageSlots() {
        ConfigurationSection navConfig = menuConfig.getConfigurationSection("navigation");
        if (navConfig == null) return new ArrayList<>();
        ConfigurationSection prevConfig = navConfig.getConfigurationSection("previous-page");
        return prevConfig != null ? getNavigationSlots(prevConfig) : new ArrayList<>();
    }
    
    public List<Integer> getNextPageSlots() {
        ConfigurationSection navConfig = menuConfig.getConfigurationSection("navigation");
        if (navConfig == null) return new ArrayList<>();
        ConfigurationSection nextConfig = navConfig.getConfigurationSection("next-page");
        return nextConfig != null ? getNavigationSlots(nextConfig) : new ArrayList<>();
    }
    
    @Deprecated
    public int getPreviousPageSlot() {
        List<Integer> slots = getPreviousPageSlots();
        return slots.isEmpty() ? 48 : slots.get(0);
    }
    
    @Deprecated
    public int getNextPageSlot() {
        List<Integer> slots = getNextPageSlots();
        return slots.isEmpty() ? 50 : slots.get(0);
    }
    
    public void playSound(Player player, String configPath, Sound defaultSound) {
        try {
            String soundName = plugin.getConfig().getString(configPath, defaultSound.name());
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), defaultSound, 1.0f, 1.0f);
        }
    }
    
    private String colorize(String text) {
        return plugin.getLanguageManager().colorize(text);
    }
    
    private List<String> colorizeList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String line : list) {
            result.add(colorize(line));
        }
        return result;
    }
    
    public void reload() {
        loadMenu();
    }
}
