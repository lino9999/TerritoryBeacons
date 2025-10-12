package com.Lino.territoryBeacons.commands;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import com.Lino.territoryBeacons.managers.MessageManager;
import com.Lino.territoryBeacons.managers.TerritoryManager;
import org.bukkit.Bukkit;
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
    private final MessageManager messageManager;

    public TerritoryCommand(TerritoryBeacons plugin) {
        this.plugin = plugin;
        this.territoryManager = plugin.getTerritoryManager();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.get("player-only-command"));
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
                    player.sendMessage(messageManager.get("usage-trust"));
                } else {
                    trustPlayer(player, args[1]);
                }
                break;
            case "untrust":
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("usage-untrust"));
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
            case "setname":
                if (!player.hasPermission("territory.setname")) {
                    player.sendMessage(messageManager.get("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("usage-setname"));
                } else {
                    setTerritoryName(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
                break;
            case "reload":
                if (player.hasPermission("territory.admin")) {
                    plugin.getConfigManager().loadConfigValues();
                    plugin.getMessageManager().loadMessages();
                    player.sendMessage(messageManager.get("reload"));
                } else {
                    player.sendMessage(messageManager.get("no-permission"));
                }
                break;
            case "help":
                showHelp(player);
                break;
            default:
                player.sendMessage(messageManager.get("unknown-command"));
                break;
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(messageManager.get("help-header"));
        player.sendMessage(messageManager.get("help-info"));
        player.sendMessage(messageManager.get("help-trust"));
        player.sendMessage(messageManager.get("help-untrust"));
        player.sendMessage(messageManager.get("help-trusted"));
        player.sendMessage(messageManager.get("help-list"));
        if (player.hasPermission("territory.setname")) {
            player.sendMessage(messageManager.get("help-setname"));
        }
        if (player.hasPermission("territory.admin")) {
            player.sendMessage(messageManager.get("help-reload"));
        }
        player.sendMessage(messageManager.get("help-footer"));
    }

    private void showTerritoryInfo(Player player) {
        Territory territory = territoryManager.getTerritoryAt(player.getLocation());
        if (territory == null) {
            player.sendMessage(messageManager.get("not-in-territory"));
            return;
        }
        player.sendMessage(messageManager.get("gui-info-button"));
        player.sendMessage(messageManager.get("gui-info-lore-owner", "%owner%", territory.getOwnerName()));
        player.sendMessage(messageManager.get("gui-info-lore-name", "%name%", territory.getTerritoryName()));
        player.sendMessage(messageManager.get("gui-info-lore-radius", "%radius%", String.valueOf(territory.getRadius())));
        player.sendMessage(messageManager.get("gui-info-lore-tier", "%tier%", String.valueOf(territory.getTier())));
        player.sendMessage(messageManager.get("gui-info-lore-influence", "%influence%", String.format("%.1f", territory.getInfluence() * 100)));
    }

    private void trustPlayer(Player player, String targetName) {
        Territory territory = territoryManager.getTerritoryByOwner(player.getUniqueId());
        if (territory == null) {
            player.sendMessage(messageManager.get("not-owner-of-territory"));
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(messageManager.get("player-not-found"));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(messageManager.get("you-cannot-trust-yourself"));
            return;
        }
        if (territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(messageManager.get("player-already-trusted", "%trusted_player%", target.getName()));
            return;
        }
        territory.addTrustedPlayer(target.getUniqueId());
        player.sendMessage(messageManager.get("player-trusted", "%trusted_player%", target.getName()));
        target.sendMessage(messageManager.get("player-is-now-trusted", "%owner%", player.getName()));
    }

    private void untrustPlayer(Player player, String targetName) {
        Territory territory = territoryManager.getTerritoryByOwner(player.getUniqueId());
        if (territory == null) {
            player.sendMessage(messageManager.get("not-owner-of-territory"));
            return;
        }
        OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers()).filter(p -> p.getName().equalsIgnoreCase(targetName)).findFirst().orElse(null);
        if (target == null) {
            player.sendMessage(messageManager.get("player-not-found"));
            return;
        }
        if (!territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(messageManager.get("player-not-trusted", "%trusted_player%", target.getName()));
            return;
        }
        territory.removeTrustedPlayer(target.getUniqueId());
        player.sendMessage(messageManager.get("player-untrusted", "%trusted_player%", target.getName()));
        if (target.isOnline()) {
            target.getPlayer().sendMessage(messageManager.get("player-is-no-longer-trusted", "%owner%", player.getName()));
        }
    }

    private void showTrustedPlayers(Player player) {
        Territory territory = territoryManager.getTerritoryByOwner(player.getUniqueId());
        if (territory == null) {
            player.sendMessage(messageManager.get("not-owner-of-territory"));
            return;
        }
        Set<UUID> trusted = territory.getTrustedPlayers();
        if (trusted.isEmpty()) {
            player.sendMessage(messageManager.get("no-trusted-players"));
            return;
        }
        player.sendMessage(messageManager.get("gui-trusted-button"));
        for (UUID uuid : trusted) {
            OfflinePlayer trustedPlayer = Bukkit.getOfflinePlayer(uuid);
            String status = trustedPlayer.isOnline() ? " (Online)" : " (Offline)";
            player.sendMessage("- " + trustedPlayer.getName() + status);
        }
    }

    private void setTerritoryName(Player player, String name) {
        Territory territory = territoryManager.getTerritoryByOwner(player.getUniqueId());
        if (territory == null) {
            player.sendMessage(messageManager.get("not-owner-of-territory"));
            return;
        }
        territory.setTerritoryName(name);
        if (plugin.getPl3xMapManager() != null) {
            plugin.getPl3xMapManager().addOrUpdateTerritoryMarker(territory);
        }
        player.sendMessage(messageManager.get("territory-name-set", "%name%", name));
    }

    private void listAllTerritories(Player player) {
        if (territoryManager.getAllTerritories().isEmpty()) {
            player.sendMessage(messageManager.get("no-active-territories"));
            return;
        }
        player.sendMessage(messageManager.get("list-header"));
        for (Territory territory : territoryManager.getAllTerritories()) {
            Location loc = territory.getBeaconLocation();
            String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            String influence = String.format("%.0f%%", territory.getInfluence() * 100);
            player.sendMessage(territory.getTerritoryName() + " - " + coords + " - Influence: " + influence);
        }
    }

    private void listPlayerTerritories(Player player, String targetName) {
        OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers()).filter(p -> p.getName().equalsIgnoreCase(targetName)).findFirst().orElse(null);
        if (target == null) {
            player.sendMessage(messageManager.get("player-not-found"));
            return;
        }
        Territory territory = territoryManager.getTerritoryByOwner(target.getUniqueId());
        if (territory == null) {
            player.sendMessage(messageManager.get("target-has-no-territory", "%player%", target.getName()));
            return;
        }
        Location loc = territory.getBeaconLocation();
        String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        player.sendMessage(territory.getTerritoryName());
        player.sendMessage("Location: " + coords);
        player.sendMessage(messageManager.get("gui-info-lore-radius", "%radius%", String.valueOf(territory.getRadius())));
        player.sendMessage(messageManager.get("gui-info-lore-tier", "%tier%", String.valueOf(territory.getTier())));
        player.sendMessage(messageManager.get("gui-info-lore-influence", "%influence%", String.format("%.1f", territory.getInfluence() * 100)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(Arrays.asList("info", "trust", "untrust", "trusted", "list", "help"));
            if (sender.hasPermission("territory.admin")) {
                subcommands.add("reload");
            }
            if (sender.hasPermission("territory.setname")) {
                subcommands.add("setname");
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