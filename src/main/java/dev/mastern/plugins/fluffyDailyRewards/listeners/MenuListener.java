package dev.mastern.plugins.fluffyDailyRewards.listeners;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.managers.LanguageManager;
import dev.mastern.plugins.fluffyDailyRewards.managers.MenuManager;
import dev.mastern.plugins.fluffyDailyRewards.managers.PlayerDataManager;
import dev.mastern.plugins.fluffyDailyRewards.managers.RewardsManager;
import dev.mastern.plugins.fluffyDailyRewards.models.DailyReward;
import dev.mastern.plugins.fluffyDailyRewards.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuListener implements Listener {
    
    private final FluffyDailyRewards plugin;
    
    public MenuListener(FluffyDailyRewards plugin) {
        this.plugin = plugin;
    }
    
    private List<Integer> parseSlots(String slotsString) {
        List<Integer> slots = new java.util.ArrayList<>();
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
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        String viewTitle = event.getView().getTitle();
        String menuTitleColorized = plugin.getLanguageManager().colorize(
                plugin.getMenuManager().getMenuTitle()
        );
        
        boolean isMenu = viewTitle.equals(menuTitleColorized) || 
                ChatColor.stripColor(viewTitle).equals(ChatColor.stripColor(menuTitleColorized));
        
        if (!isMenu) {
            return;
        }
        
        event.setCancelled(true);
        

        if (event.getClickedInventory() == null || 
            event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }
        
        int slot = event.getSlot();
        MenuManager menuManager = plugin.getMenuManager();
        
        if (menuManager.isRewardSlot(slot)) {
            handleRewardClick(player, slot);
            return;
        }
        
        List<Integer> prevSlots = menuManager.getPreviousPageSlots();
        List<Integer> nextSlots = menuManager.getNextPageSlots();
        
        if (prevSlots.contains(slot)) {
            int currentPage = menuManager.getPlayerPage(player.getUniqueId());
            if (currentPage > 0) {
                menuManager.openMenu(player, currentPage - 1);
            }
        } else if (nextSlots.contains(slot)) {
            int currentPage = menuManager.getPlayerPage(player.getUniqueId());
            org.bukkit.configuration.file.FileConfiguration menuConfig = plugin.getMenuManager().getMenuConfig();
            int itemsPerPage = menuConfig.isString("menu.reward-slots") ? 
                parseSlots(menuConfig.getString("menu.reward-slots")).size() : 
                menuConfig.getIntegerList("menu.reward-slots").size();
            int totalRewards = plugin.getRewardsManager().getTotalRewards();
            int totalPages = (int) Math.ceil((double) totalRewards / itemsPerPage);
            
            if (currentPage < totalPages - 1) {
                menuManager.openMenu(player, currentPage + 1);
            }
        }
    }
    
    private void handleRewardClick(Player player, int slot) {
        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        RewardsManager rewardsManager = plugin.getRewardsManager();
        LanguageManager lang = plugin.getLanguageManager();
        MenuManager menuManager = plugin.getMenuManager();
        
        dataManager.getPlayerData(player, true).thenAccept(data -> {
            int currentPage = menuManager.getPlayerPage(player.getUniqueId());
            
            org.bukkit.configuration.file.FileConfiguration menuConfig = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.File(plugin.getDataFolder(), "menu.yml")
                );
            
            List<Integer> rewardSlots;
            if (menuConfig.isString("menu.reward-slots")) {
                rewardSlots = parseSlots(menuConfig.getString("menu.reward-slots"));
            } else {
                rewardSlots = menuConfig.getIntegerList("menu.reward-slots");
            }
            int slotIndex = rewardSlots.indexOf(slot);
            
            if (slotIndex == -1) {
                return;
            }
            
            int itemsPerPage = rewardSlots.size();
            int dayIndex = (currentPage * itemsPerPage) + slotIndex;
            
            Map<Integer, DailyReward> allRewards = rewardsManager.getAllRewards();
            List<Integer> sortedDays = new java.util.ArrayList<>(allRewards.keySet());
            java.util.Collections.sort(sortedDays);
            
            if (dayIndex >= sortedDays.size()) {
                return;
            }
            
            int day = sortedDays.get(dayIndex);
            
            if (day != data.getCurrentDay()) {
                menuManager.playSound(player, "sounds.claim-already", Sound.ENTITY_VILLAGER_NO);
                player.sendMessage(lang.getMessage("rewards.not-available"));
                
                if (plugin.getToastManager() != null && plugin.getToastManager().isEnabled()) {
                    String toastMessage = lang.colorize(lang.getMessage("rewards.already-claimed-toast"));
                    plugin.getToastManager().showFailToast(player, toastMessage);
                }
                return;
            }
            
            if (!dataManager.canClaim(data)) {
                menuManager.playSound(player, "sounds.claim-already", Sound.ENTITY_VILLAGER_NO);
                player.sendMessage(lang.getMessage("rewards.already-claimed"));
                
                if (plugin.getToastManager() != null && plugin.getToastManager().isEnabled()) {
                    String toastMessage = lang.colorize(lang.getMessage("rewards.already-claimed-toast"));
                    plugin.getToastManager().showFailToast(player, toastMessage);
                }
                return;
            }
            
            if (plugin.getPlaytimeTracker() != null && plugin.getPlaytimeTracker().isEnabled()) {
                if (!plugin.getPlaytimeTracker().hasMetRequirement(player)) {
                    String remainingTime = plugin.getPlaytimeTracker().formatRemainingTime(player, lang);
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("time", remainingTime);
                    player.sendMessage(lang.getMessage("rewards.playtime-required", replacements));
                    menuManager.playSound(player, "sounds.claim-already", Sound.ENTITY_VILLAGER_NO);
                    
                    if (plugin.getToastManager() != null && plugin.getToastManager().isEnabled()) {
                        String toastMessage = lang.colorize(lang.getMessage("rewards.playtime-required-toast"));
                        plugin.getToastManager().showFailToast(player, toastMessage);
                    }
                    return;
                }
            }
            
            dataManager.claimReward(player, day).thenAccept(success -> {
                plugin.runSync(player, () -> {
                    if (success) {
                        Map<String, String> replacements = new HashMap<>();
                        replacements.put("day", String.valueOf(day));
                        
                        player.sendMessage(lang.getMessage("rewards.claimed", replacements));
                        menuManager.playSound(player, "sounds.claim-success", Sound.ENTITY_PLAYER_LEVELUP);
                        
                        if (plugin.getConfig().getBoolean("particles.enabled", true)) {
                            String particleName = plugin.getConfig().getString("particles.type", "VILLAGER_HAPPY");
                            int particleCount = plugin.getConfig().getInt("particles.count", 10);
                            
                            try {
                                Particle particle = Particle.valueOf(particleName);
                                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), particleCount);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        
                        menuManager.openMenu(player, currentPage);
                    } else {
                        player.sendMessage(lang.getMessage("rewards.not-available"));
                    }
                });
            });
        });
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        String viewTitle = event.getView().getTitle();
        String menuTitleColorized = plugin.getLanguageManager().colorize(
                plugin.getMenuManager().getMenuTitle()
        );
        
        boolean isMenu = viewTitle.equals(menuTitleColorized) || 
                ChatColor.stripColor(viewTitle).equals(ChatColor.stripColor(menuTitleColorized));
        
        if (isMenu) {
            event.setCancelled(true);
        }
    }
}
