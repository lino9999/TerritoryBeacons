package com.Lino.territoryBeacons.commands;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import com.Lino.territoryBeacons.managers.PlayerManager;
import com.Lino.territoryBeacons.managers.TerritoryManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TerritoryCommand implements CommandExecutor, TabCompleter {

    private final TerritoryBeacons plugin;
    private final TerritoryManager territoryManager;

    public TerritoryCommand(TerritoryBeacons plugin) {
        this.plugin = plugin;
        this.territoryManager = plugin.getTerritoryManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "info":
                showTerritoryInfo(player);
                break;
            case "trust":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /territory trust <player>");
                } else {
                    trustPlayer(player, args[1]);
                }
                break;
            case "untrust":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /territory untrust <player>");
                } else {
                    untrustPlayer(player, args[1]);
                }
                break;
            case "list":
                if (args.length > 1) {
                    listPlayerTerritories(player, args[1]);
                } else {
                    listAllTerritories(player);
                }
                break;
            case "trusted":
                showTrustedPlayers(player);
                break;
            case "reload":
                if (player.hasPermission("territory.admin")) {
                    plugin.getConfigManager().loadConfigValues();
                    player.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
                } else {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                }
                break;
            case "help":
                showHelp(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown command. Use /territory help for assistance.");
                break;
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== TerritoryBeacons Commands ===");
        player.sendMessage(ChatColor.AQUA + "/territory info" + ChatColor.WHITE + " - Show information about your current territory.");
        player.sendMessage(ChatColor.AQUA + "/territory trust <player>" + ChatColor.WHITE + " - Add a player to your territory.");
        player.sendMessage(ChatColor.AQUA + "/territory untrust <player>" + ChatColor.WHITE + " - Remove a player from your territory.");
        player.sendMessage(ChatColor.AQUA + "/territory trusted" + ChatColor.WHITE + " - List players trusted in your territory.");
        player.sendMessage(ChatColor.AQUA + "/territory list [player]" + ChatColor.WHITE + " - List all territories or a specific player's.");
        if (player.hasPermission("territory.admin")) {
            player.sendMessage(ChatColor.AQUA + "/territory reload" + ChatColor.WHITE + " - Reload the plugin's configuration.");
        }
        player.sendMessage(ChatColor.AQUA + "/territory help" + ChatColor.WHITE + " - Displays this help message.");
    }

    private void showTerritoryInfo(Player player) {
        Territory territory = territoryManager.getTerritoryAt(player.getLocation());
        if (territory == null) {
            player.sendMessage(ChatColor.YELLOW + "You are not inside any territory.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "=== Territory Information ===");
        player.sendMessage(ChatColor.AQUA + "Owner: " + ChatColor.WHITE + territory.getOwnerName());
        player.sendMessage(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + territory.getRadius() + " blocks");
        player.sendMessage(ChatColor.AQUA + "Tier: " + ChatColor.WHITE + territory.getTier());
        player.sendMessage(ChatColor.AQUA + "Influence: " + ChatColor.WHITE + String.format("%.1f%%", territory.getInfluence() * 100));
        if (territory.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.GOLD + "This is your territory.");
        } else if (territory.isTrusted(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are trusted in this territory.");
        }
    }

    private void trustPlayer(Player player, String targetName) {
        Territory territory = territoryManager.getTerritoryByOwner(player.getUniqueId());
        if (territory == null) {
            player.sendMessage(ChatColor.RED + "You do not own a territory.");
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot trust yourself.");
            return;
        }
        if (territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " is already trusted.");
            return;
        }
        territory.addTrustedPlayer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " has been added to your territory.");
        target.sendMessage(ChatColor.GREEN + "You are now trusted in " + player.getName() + "'s territory.");
    }

    private void untrustPlayer(Player player, String targetName) {
        Territory territory = territoryManager.getTerritoryByOwner(player.getUniqueId());
        if (territory == null) {
            player.sendMessage(ChatColor.RED + "You do not own a territory.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " is not trusted in your territory.");
            return;
        }
        territory.removeTrustedPlayer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " has been removed from your territory.");
        if (target.isOnline()) {
            ((Player) target).sendMessage(ChatColor.YELLOW + "You are no longer trusted in " + player.getName() + "'s territory.");
        }
    }

    private void showTrustedPlayers(Player player) {
        Territory territory = territoryManager.getTerritoryByOwner(player.getUniqueId());
        if (territory == null) {
            player.sendMessage(ChatColor.RED + "You do not own a territory.");
            return;
        }
        Set<UUID> trusted = territory.getTrustedPlayers();
        if (trusted.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have not trusted any players.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "=== Trusted Players ===");
        for (UUID uuid : trusted) {
            OfflinePlayer trustedPlayer = Bukkit.getOfflinePlayer(uuid);
            String status = trustedPlayer.isOnline() ? ChatColor.GREEN + " (Online)" : ChatColor.GRAY + " (Offline)";
            player.sendMessage(ChatColor.AQUA + "- " + trustedPlayer.getName() + status);
        }
    }

    private void listAllTerritories(Player player) {
        if (territoryManager.getAllTerritories().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "There are no active territories on this server.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "=== Active Territories ===");
        for (Territory territory : territoryManager.getAllTerritories()) {
            Location loc = territory.getBeaconLocation();
            String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            String influence = String.format("%.0f%%", territory.getInfluence() * 100);
            player.sendMessage(ChatColor.AQUA + territory.getOwnerName() + ChatColor.WHITE + " - " + coords + " - Influence: " + influence);
        }
    }

    private void listPlayerTerritories(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Territory territory = territoryManager.getTerritoryByOwner(target.getUniqueId());
        if (territory == null) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " does not own a territory.");
            return;
        }
        Location loc = territory.getBeaconLocation();
        String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        player.sendMessage(ChatColor.GREEN + "=== " + target.getName() + "'s Territory ===");
        player.sendMessage(ChatColor.AQUA + "Location: " + ChatColor.WHITE + coords);
        player.sendMessage(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + territory.getRadius() + " blocks");
        player.sendMessage(ChatColor.AQUA + "Tier: " + ChatColor.WHITE + territory.getTier());
        player.sendMessage(ChatColor.AQUA + "Influence: " + ChatColor.WHITE + String.format("%.1f%%", territory.getInfluence() * 100));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(Arrays.asList("info", "trust", "untrust", "trusted", "list", "help"));
            if (sender.hasPermission("territory.admin")) {
                subcommands.add("reload");
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") || args[0].equalsIgnoreCase("list"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}