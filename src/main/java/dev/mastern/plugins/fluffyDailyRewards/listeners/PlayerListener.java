package dev.mastern.plugins.fluffyDailyRewards.listeners;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.managers.LanguageManager;
import dev.mastern.plugins.fluffyDailyRewards.models.PlayerData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class PlayerListener implements Listener {
    
    private final FluffyDailyRewards plugin;
    
    public PlayerListener(FluffyDailyRewards plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        plugin.getPlayerDataManager().getPlayerData(player, true);
        
        if (plugin.getPlaytimeTracker() != null) {
            plugin.getPlaytimeTracker().onPlayerJoin(player);
        }
        
        if (!plugin.getConfig().getBoolean("join-notification.enabled", true)) {
            return;
        }
        
        long delay = plugin.getConfig().getLong("join-notification.delay", 20);
        
        plugin.runSyncLater(player, () -> {
            plugin.getPlayerDataManager().getPlayerData(player).thenAccept(data -> {
                if (plugin.getPlayerDataManager().canClaim(data)) {
                    plugin.runSync(player, () -> {
                        sendJoinNotification(player);
                    });
                }
            });
        }, delay);
    }
    
    private void sendJoinNotification(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        List<String> messages = plugin.getConfig().getStringList("join-notification.messages");
        
        String clickPlaceholder = "{click}";
        String clickMessageText = plugin.getConfig().getString("join-notification.click-message", "&#FFFF00[Click here to claim]");
        
        for (String message : messages) {
            if (message.contains(clickPlaceholder)) {
                String[] parts = message.split("\\{click\\}", -1);
                TextComponent fullMessage = new TextComponent("");
                
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    fullMessage.addExtra(new TextComponent(lang.colorize(parts[0])));
                }
                
                TextComponent clickable = new TextComponent(lang.colorize(clickMessageText));
                clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/daily"));
                
                List<String> hoverTexts = plugin.getConfig().getStringList("join-notification.hover-text");
                if (!hoverTexts.isEmpty()) {
                    ComponentBuilder hoverBuilder = new ComponentBuilder("");
                    for (int i = 0; i < hoverTexts.size(); i++) {
                        if (i > 0) hoverBuilder.append("\n");
                        hoverBuilder.append(lang.colorize(hoverTexts.get(i)));
                    }
                    clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBuilder.create()));
                }
                
                fullMessage.addExtra(clickable);
                
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    fullMessage.addExtra(new TextComponent(lang.colorize(parts[1])));
                }
                
                player.spigot().sendMessage(fullMessage);
            } else {
                player.sendMessage(lang.colorize(message));
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getPlaytimeTracker() != null) {
            plugin.getPlaytimeTracker().onPlayerQuit(player);
        }
        
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());
        plugin.getMenuManager().removePlayer(player.getUniqueId());
    }
}
