package dev.mastern.plugins.fluffyDailyRewards.commands;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.managers.LanguageManager;
import dev.mastern.plugins.fluffyDailyRewards.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DailyCommand implements CommandExecutor, TabCompleter {
    
    private final FluffyDailyRewards plugin;
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList("reload", "give", "reset", "set", "check");
    
    public DailyCommand(FluffyDailyRewards plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            if (sender.hasPermission("fluffydailyrewards.admin")) {
                if ("admin".startsWith(args[0].toLowerCase())) {
                    completions.add("admin");
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("fluffydailyrewards.admin")) {
                String input = args[1].toLowerCase();
                completions = ADMIN_SUBCOMMANDS.stream()
                        .filter(cmd -> cmd.startsWith(input))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String subCmd = args[1].toLowerCase();
            if (subCmd.equals("give") || subCmd.equals("reset") || subCmd.equals("set") || subCmd.equals("check")) {
                if (sender.hasPermission("fluffydailyrewards.admin")) {
                    String input = args[2].toLowerCase();
                    completions = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            String subCmd = args[1].toLowerCase();
            if (subCmd.equals("give") || subCmd.equals("set")) {
                if (sender.hasPermission("fluffydailyrewards.admin")) {
                    String input = args[3].toLowerCase();
                    int maxDay = plugin.getRewardsManager().getMaxDay();
                    for (int i = 1; i <= maxDay; i++) {
                        String dayStr = String.valueOf(i);
                        if (dayStr.startsWith(input)) {
                            completions.add(dayStr);
                        }
                    }
                }
            }
        }
        
        return completions;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();
        
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("fluffydailyrewards.admin")) {
                sender.sendMessage(lang.getMessage("general.no-permission"));
                return true;
            }
            
            if (args.length == 1) {
                return true;
            }
            
            String[] adminArgs = Arrays.copyOfRange(args, 1, args.length);
            return handleAdminCommand(sender, adminArgs, lang);
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getMessage("general.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("fluffydailyrewards.use")) {
            sender.sendMessage(lang.getMessage("general.no-permission"));
            return true;
        }
        
        plugin.getMenuManager().openMenu(player, 0);
        return true;
    }
    
    private boolean handleAdminCommand(CommandSender sender, String[] args, LanguageManager lang) {
        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender, lang);
                break;
                
            case "give":
                if (args.length < 3) {
                    return true;
                }
                handleGive(sender, args[1], args[2], lang);
                break;
                
            case "reset":
                if (args.length < 2) {
                    return true;
                }
                handleReset(sender, args[1], lang);
                break;
                
            case "set":
                if (args.length < 3) {
                    return true;
                }
                handleSet(sender, args[1], args[2], lang);
                break;
                
            case "check":
                if (args.length < 2) {
                    return true;
                }
                handleCheck(sender, args[1], lang);
                break;
                
            default:
                return true;
        }
        
        return true;
    }
    
    private void handleReload(CommandSender sender, LanguageManager lang) {
        plugin.reloadPlugin();
        sender.sendMessage(lang.getMessage("general.reload"));
    }
    
    private void handleGive(CommandSender sender, String targetName, String dayStr, LanguageManager lang) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(lang.getMessage("general.player-not-found"));
            return;
        }
        
        int day;
        try {
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.getMessage("general.invalid-number"));
            return;
        }
        
        if (plugin.getRewardsManager().getReward(day) == null) {
            sender.sendMessage(lang.getMessage("general.invalid-day"));
            return;
        }
        
        plugin.getPlayerDataManager().claimReward(target, day).thenAccept(success -> {
            plugin.runSync(target, () -> {
                if (success) {
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("player", target.getName());
                    replacements.put("day", String.valueOf(day));
                    sender.sendMessage(lang.getMessage("general.give.success", replacements));
                } else {
                    sender.sendMessage(lang.getMessage("general.give.failed"));
                }
            });
        });
    }
    
    private void handleReset(CommandSender sender, String targetName, LanguageManager lang) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(lang.getMessage("general.player-not-found"));
            return;
        }
        
        plugin.getPlayerDataManager().getPlayerData(target).thenAccept(data -> {
            data.resetStreak();
            data.setLastClaimTime(0);
            plugin.getPlayerDataManager().savePlayerData(data).thenRun(() -> {
                plugin.runSync(target, () -> {
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("player", target.getName());
                    sender.sendMessage(lang.getMessage("general.reset", replacements));
                });
            });
        });
    }
    
    private void handleSet(CommandSender sender, String targetName, String dayStr, LanguageManager lang) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(lang.getMessage("general.player-not-found"));
            return;
        }
        
        int day;
        try {
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.getMessage("general.invalid-number"));
            return;
        }
        
        if (day < 1 || day > plugin.getRewardsManager().getMaxDay()) {
            sender.sendMessage(lang.getMessage("general.invalid-day"));
            return;
        }
        
        plugin.getPlayerDataManager().getPlayerData(target).thenAccept(data -> {
            data.setCurrentDay(day);
            plugin.getPlayerDataManager().savePlayerData(data).thenRun(() -> {
                plugin.runSync(target, () -> {
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("player", target.getName());
                    replacements.put("day", String.valueOf(day));
                    sender.sendMessage(lang.getMessage("general.set", replacements));
                });
            });
        });
    }
    
    private void handleCheck(CommandSender sender, String targetName, LanguageManager lang) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(lang.getMessage("general.player-not-found"));
            return;
        }
        
        plugin.getPlayerDataManager().getPlayerData(target).thenAccept(data -> {
            plugin.runSync(target, () -> {
                FileConfiguration menuConfig = plugin.getMenuManager().getMenuConfig();
                ConfigurationSection infoConfig = menuConfig.getConfigurationSection("decorations.info");
                
                if (infoConfig != null) {
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("player", target.getName());
                    replacements.put("streak", String.valueOf(data.getStreak()));
                    replacements.put("total_claims", String.valueOf(data.getTotalClaims()));
                    replacements.put("next_day", String.valueOf(data.getCurrentDay()));
                    
                    String status;
                    if (plugin.getPlayerDataManager().canClaim(data)) {
                        status = lang.getMessage("menu.status.can-claim");
                    } else if (data.getLastClaimTime() > 0) {
                        long nextClaim = plugin.getPlayerDataManager().getNextClaimTime(data);
                        long timeLeft = nextClaim - System.currentTimeMillis();
                        String timeFormatted = formatTime(timeLeft, lang);
                        
                        Map<String, String> timeReplacements = new HashMap<>();
                        timeReplacements.put("time", timeFormatted);
                        status = lang.getMessage("menu.status.next-available", timeReplacements);
                    } else {
                        status = lang.getMessage("menu.status.can-claim");
                    }
                    replacements.put("status", status);
                    
                    Map<String, String> titleReplacements = new HashMap<>();
                    titleReplacements.put("player", target.getName());
                    sender.sendMessage(lang.colorize(lang.getMessage("check.header")));
                    sender.sendMessage(lang.getMessage("check.title", titleReplacements));
                    sender.sendMessage(lang.colorize(lang.getMessage("check.header")));
                    
                    List<String> lore = infoConfig.getStringList("lore");
                    for (String line : lore) {
                        for (Map.Entry<String, String> entry : replacements.entrySet()) {
                            line = line.replace("{" + entry.getKey() + "}", entry.getValue());
                        }
                        sender.sendMessage(" " + lang.colorize(line));
                    }
                    
                    sender.sendMessage(lang.colorize(lang.getMessage("check.footer")));
                } else {
                    sender.sendMessage(lang.colorize(lang.getMessage("general.no-permission")));
                }
            });
        });
    }
    
    private String formatTime(long millis, LanguageManager lang) {
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
}
