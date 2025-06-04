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
                    player.sendMessage(ChatColor.RED + "Uso: /territory trust <player>");
                    break;
                }
                trustPlayer(player, args[1]);
                break;

            case "untrust":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /territory untrust <player>");
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
                    player.sendMessage(ChatColor.RED + "Non hai il permesso per ricaricare il plugin!");
                }
                break;

            case "help":
                showHelp(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Comando sconosciuto. Usa /territory help");
                break;
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== TerritoryBeacons Comandi ===");
        player.sendMessage(ChatColor.AQUA + "/territory info" + ChatColor.WHITE + " - Mostra info del territorio in cui ti trovi");
        player.sendMessage(ChatColor.AQUA + "/territory trust <player>" + ChatColor.WHITE + " - Aggiungi un giocatore fidato");
        player.sendMessage(ChatColor.AQUA + "/territory untrust <player>" + ChatColor.WHITE + " - Rimuovi un giocatore fidato");
        player.sendMessage(ChatColor.AQUA + "/territory trusted" + ChatColor.WHITE + " - Lista giocatori fidati");
        player.sendMessage(ChatColor.AQUA + "/territory list [player]" + ChatColor.WHITE + " - Lista territori");
        if (player.hasPermission("territory.admin")) {
            player.sendMessage(ChatColor.AQUA + "/territory reload" + ChatColor.WHITE + " - Ricarica la configurazione");
        }
        player.sendMessage(ChatColor.AQUA + "/territory help" + ChatColor.WHITE + " - Mostra questo messaggio");
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
            player.sendMessage(ChatColor.YELLOW + "Non sei in nessun territorio");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "=== Informazioni Territorio ===");
        player.sendMessage(ChatColor.AQUA + "Proprietario: " + ChatColor.WHITE + territory.getOwnerName());
        player.sendMessage(ChatColor.AQUA + "Raggio: " + ChatColor.WHITE + territory.getRadius() + " blocchi");
        player.sendMessage(ChatColor.AQUA + "Livello: " + ChatColor.WHITE + territory.getTier());
        player.sendMessage(ChatColor.AQUA + "Influenza: " + ChatColor.WHITE +
                String.format("%.1f%%", territory.getInfluence() * 100));

        if (territory.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.GOLD + "Questo è il tuo territorio!");
        } else if (territory.isTrusted(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "Sei un giocatore fidato in questo territorio");
        }
    }

    private void trustPlayer(Player player, String targetName) {
        Territory territory = plugin.getTerritoryByOwner(player.getUniqueId());

        if (territory == null) {
            player.sendMessage(ChatColor.RED + "Non possiedi nessun territorio!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Giocatore non trovato!");
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Non puoi aggiungere te stesso!");
            return;
        }

        if (territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " è già un giocatore fidato!");
            return;
        }

        territory.addTrustedPlayer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " è ora un giocatore fidato!");

        if (target.isOnline()) {
            target.sendMessage(ChatColor.GREEN + "Sei ora un giocatore fidato nel territorio di " +
                    player.getName() + "!");
        }
    }

    private void untrustPlayer(Player player, String targetName) {
        Territory territory = plugin.getTerritoryByOwner(player.getUniqueId());

        if (territory == null) {
            player.sendMessage(ChatColor.RED + "Non possiedi nessun territorio!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Giocatore non trovato!");
            return;
        }

        if (!territory.isTrusted(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " non è un giocatore fidato!");
            return;
        }

        territory.removeTrustedPlayer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " non è più un giocatore fidato!");

        if (target.isOnline()) {
            target.sendMessage(ChatColor.YELLOW + "Non sei più un giocatore fidato nel territorio di " +
                    player.getName() + "!");
        }
    }

    private void showTrustedPlayers(Player player) {
        Territory territory = plugin.getTerritoryByOwner(player.getUniqueId());

        if (territory == null) {
            player.sendMessage(ChatColor.RED + "Non possiedi nessun territorio!");
            return;
        }

        Set<UUID> trusted = territory.getTrustedPlayers();

        if (trusted.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Non hai giocatori fidati nel tuo territorio");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "=== Giocatori Fidati ===");
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
            player.sendMessage(ChatColor.YELLOW + "Non ci sono territori attivi");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "=== Territori Attivi ===");
        for (Territory territory : territories.values()) {
            Location loc = territory.getBeaconLocation();
            String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            String influence = String.format("%.0f%%", territory.getInfluence() * 100);

            player.sendMessage(ChatColor.AQUA + territory.getOwnerName() + ChatColor.WHITE +
                    " - " + coords + " - Influenza: " + influence);
        }
    }

    private void listPlayerTerritories(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUUID = target != null ? target.getUniqueId() :
                Bukkit.getOfflinePlayer(targetName).getUniqueId();

        Territory territory = plugin.getTerritoryByOwner(targetUUID);

        if (territory == null) {
            player.sendMessage(ChatColor.YELLOW + targetName + " non possiede territori");
            return;
        }

        Location loc = territory.getBeaconLocation();
        String coords = String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        player.sendMessage(ChatColor.GREEN + "=== Territorio di " + targetName + " ===");
        player.sendMessage(ChatColor.AQUA + "Posizione: " + ChatColor.WHITE + coords);
        player.sendMessage(ChatColor.AQUA + "Raggio: " + ChatColor.WHITE + territory.getRadius() + " blocchi");
        player.sendMessage(ChatColor.AQUA + "Livello: " + ChatColor.WHITE + territory.getTier());
        player.sendMessage(ChatColor.AQUA + "Influenza: " + ChatColor.WHITE +
                String.format("%.1f%%", territory.getInfluence() * 100));
    }

    private void reloadPlugin(Player player) {
        plugin.reloadConfig();
        plugin.loadConfigValues();
        player.sendMessage(ChatColor.GREEN + "Configurazione ricaricata con successo!");
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