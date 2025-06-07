package com.Lino.territoryBeacons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.*;
import java.util.stream.Collectors;

public class TerritoryCommand implements CommandExecutor, TabCompleter {

    private final TerritoryBeacons plugin;

    public TerritoryCommand(TerritoryBeacons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
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
                    break;
                }
                trustPlayer(player, args[1]);
                break;

            case "untrust":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /territory untrust <player>");
                    break;
                }
                untrustPlayer(player, args[1]);
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
                    reloadPlugin(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You do not have permission to reload the plugin!");
                }
                break;

            case "help":
                showHelp(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown command. Use /territory help");
                break;
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== TerritoryBeacons Commands ===");
        player.sendMessage(ChatColor.AQUA + "/territory info" + ChatColor.WHITE + " - Show information about the territory you are in");
        player.sendMessage(ChatColor.AQUA + "/territory trust <player>" + ChatColor.WHITE + " - Add a trusted player");
        player.sendMessage(ChatColor.AQUA + "/territory untrust <player>" + ChatColor.WHITE + " - Remove a trusted player");
        player.sendMessage(ChatColor.AQUA + "/territory trusted" + ChatColor.WHITE + " - List trusted players");
        player.sendMessage(ChatColor.AQUA + "/territory list [player]" + ChatColor.WHITE + " - List territories");
        if (player.hasPermission("territory.admin")) {
            player.sendMessage(ChatColor.AQUA + "/territory reload" + ChatColor.WHITE + " - Reload the configuration");
        }
        player.sendMessage(ChatColor.AQUA + "/territory help" + ChatColor.WHITE + " - Show this message");
    }

    private void showTerritoryInfo(Player player) {
        Location loc = player.getLocation();
        Territory territory = null;

        // Find territory at player location
        for (Territory t : plugin.getTerritories().values()) {
            if (t.contains(loc)) {
                territory = t;
                break;
            }
        }

        if (territory == null) {
            player.sendMessage(ChatColor.YELLOW + "You are not in any territory");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "=== Territory Information ===");
        player.sendMessage(ChatColor.AQUA + "Owner: " + ChatColor.WHITE + territory.getOwnerName());
        player.sendMessage(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + territory.getRadius() + " blocks");
        player.sendMessage(ChatColor.AQUA + "Level: " + ChatColor.WHITE + territory.getTier());
        player.sendMessage(ChatColor.AQUA + "Influence: " + ChatColor.WHITE +
                String.format("%.1f%%", territory.getInfluence() * 100));

        if (territory.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.GOLD + "This is your territory!");
        } else if (territory.isTrusted(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are a trusted player in this territory");
        }
    }

    private void trustPlayer(Player player, String targetName) {
        Territory territory = plugin.getTerritoryByOwner(player.getUniqueId());

        if (territory == null) {
            player.sendMessage(ChatColor.RED + "You do not own any territory!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot add yourself!");
            return;
        }

        if (territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " is already a trusted player!");
            return;
        }

        territory.addTrustedPlayer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " is now a trusted player!");

        if (target.isOnline()) {
            target.sendMessage(ChatColor.GREEN + "You are now a trusted player in " +
                    player.getName() + "'s territory!");
        }
    }

    private void untrustPlayer(Player player, String targetName) {
        Territory territory = plugin.getTerritoryByOwner(player.getUniqueId());

        if (territory == null) {
            player.sendMessage(ChatColor.RED + "You do not own any territory!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        if (!territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " is not a trusted player!");
            return;
        }

        territory.removeTrustedPlayer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " is no longer a trusted player!");

        if (target.isOnline()) {
            target.sendMessage(ChatColor.YELLOW + "You are no longer a trusted player in " +
                    player.getName() + "'s territory!");
        }
    }

    private void showTrustedPlayers(Player player) {
        Territory territory = plugin.getTerritoryByOwner(player.getUniqueId());

        if (territory == null) {
            player.sendMessage(ChatColor.RED + "You do not own any territory!");
            return;
        }

        Set<UUID> trusted = territory.getTrustedPlayers();

        if (trusted.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no trusted players in your territory");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "=== Trusted Players ===");
        for (UUID uuid : trusted) {
            Player trustedPlayer = Bukkit.getPlayer(uuid);
            String name = trustedPlayer != null ? trustedPlayer.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            String status = trustedPlayer != null && trustedPlayer.isOnline() ?
                    ChatColor.GREEN + " (Online)" : ChatColor.GRAY + " (Offline)";
            player.sendMessage(ChatColor.AQUA + "- " + name + status);
        }
    }

    private void listAllTerritories(Player player) {
        Map<Location, Territory> territories = plugin.getTerritories();

        if (territories.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "There are no active territories");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "=== Active Territories ===");
        for (Territory territory : territories.values()) {
            Location loc = territory.getBeaconLocation();
            String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            String influence = String.format("%.0f%%", territory.getInfluence() * 100);

            player.sendMessage(ChatColor.AQUA + territory.getOwnerName() + ChatColor.WHITE +
                    " - " + coords + " - Influence: " + influence);
        }
    }

    private void listPlayerTerritories(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUUID = target != null ? target.getUniqueId() :
                Bukkit.getOfflinePlayer(targetName).getUniqueId();

        Territory territory = plugin.getTerritoryByOwner(targetUUID);

        if (territory == null) {
            player.sendMessage(ChatColor.YELLOW + targetName + " does not own any territories");
            return;
        }

        Location loc = territory.getBeaconLocation();
        String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        player.sendMessage(ChatColor.GREEN + "=== " + targetName + "'s Territory ===");
        player.sendMessage(ChatColor.AQUA + "Location: " + ChatColor.WHITE + coords);
        player.sendMessage(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + territory.getRadius() + " blocks");
        player.sendMessage(ChatColor.AQUA + "Level: " + ChatColor.WHITE + territory.getTier());
        player.sendMessage(ChatColor.AQUA + "Influence: " + ChatColor.WHITE +
                String.format("%.1f%%", territory.getInfluence() * 100));
    }

    private void reloadPlugin(Player player) {
        plugin.reloadConfig();
        plugin.loadConfigValues();
        player.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("info", "trust", "untrust", "trusted", "list", "help"));
            if (sender.hasPermission("territory.admin")) {
                completions.add("reload");
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") ||
                    args[0].equalsIgnoreCase("list")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}