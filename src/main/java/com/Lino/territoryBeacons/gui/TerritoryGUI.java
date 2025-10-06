package com.Lino.territoryBeacons.gui;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TerritoryGUI {

    private final TerritoryBeacons plugin;

    public TerritoryGUI(TerritoryBeacons plugin) {
        this.plugin = plugin;
    }

    public void openCreationGUI(Player player, Location beaconLocation) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Create Territory");

        ItemStack createButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta createMeta = createButton.getItemMeta();
        createMeta.setDisplayName(ChatColor.GREEN + "Create Territory");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to claim this beacon as your territory.");
        lore.add("");
        lore.add(ChatColor.AQUA + "Starting Tier: " + ChatColor.WHITE + 1);
        lore.add(ChatColor.AQUA + "Starting Radius: " + ChatColor.WHITE + plugin.getConfigManager().getRadiusForTier(1) + " blocks");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Territories Owned: " + ChatColor.WHITE + plugin.getPlayerManager().getPlayerTerritoryCount(player.getUniqueId()) + "/" + plugin.getConfigManager().getMaxTerritoriesPerPlayer());
        createMeta.setLore(lore);
        createButton.setItemMeta(createMeta);

        gui.setItem(13, createButton);
        addCancelButton(gui, 22);
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void openTerritoryInfoGUI(Player player, Territory territory) {
        Inventory gui = Bukkit.createInventory(null, 45, ChatColor.DARK_GREEN + "Territory Management");

        ItemStack infoItem = new ItemStack(Material.BEACON);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Territory Information");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.AQUA + "Owner: " + ChatColor.WHITE + territory.getOwnerName());
        infoLore.add(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + territory.getRadius() + " blocks");
        infoLore.add(ChatColor.AQUA + "Tier: " + ChatColor.WHITE + territory.getTier());
        infoLore.add(ChatColor.AQUA + "Influence: " + ChatColor.WHITE + String.format("%.1f%%", territory.getInfluence() * 100));

        Player owner = Bukkit.getPlayer(territory.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            infoLore.add(ChatColor.GREEN + "Territory is active (Owner online)");
        } else {
            long decayStartMillis = TimeUnit.HOURS.toMillis(plugin.getConfigManager().getDecayTime());
            long offlineMillis = System.currentTimeMillis() - plugin.getPlayerManager().getPlayerLastSeen(territory.getOwnerUUID());
            if (offlineMillis < decayStartMillis) {
                long remainingMillis = decayStartMillis - offlineMillis;
                long hours = TimeUnit.MILLISECONDS.toHours(remainingMillis);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60;
                infoLore.add(ChatColor.YELLOW + "Decay starts in: " + ChatColor.WHITE + hours + "h " + minutes + "m");
            } else {
                infoLore.add(ChatColor.RED + "Territory is decaying due to inactivity.");
            }
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(13, infoItem);

        boolean isOwner = territory.getOwnerUUID().equals(player.getUniqueId());
        if (isOwner) {
            gui.setItem(29, createNamedItem(Material.DIAMOND, ChatColor.AQUA + "Upgrade Territory", ChatColor.GRAY + "Click to upgrade your territory."));
            gui.setItem(33, createNamedItem(Material.TNT, ChatColor.DARK_RED + "Delete Territory", ChatColor.RED + "WARNING: This is permanent!", ChatColor.GRAY + "Click to destroy your territory."));
        }

        gui.setItem(31, createNamedItem(Material.PLAYER_HEAD, ChatColor.GREEN + "Trusted Players", ChatColor.GRAY + "Players: " + territory.getTrustedPlayers().size(), (isOwner ? ChatColor.YELLOW + "Use /territory trust <player>" : "")));
        addCancelButton(gui, 40);
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void openUpgradeGUI(Player player, Territory territory) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Upgrade Territory");
        int currentTier = territory.getTier();

        for (int i = 1; i <= 6; i++) {
            ItemStack tierItem;
            ItemMeta tierMeta;
            int radius = plugin.getConfigManager().getRadiusForTier(i);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + radius + " blocks");

            if (i <= currentTier) {
                tierItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                tierMeta = tierItem.getItemMeta();
                tierMeta.setDisplayName(ChatColor.GREEN + "Tier " + i + " (Owned)");
            } else if (i == currentTier + 1) {
                tierItem = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                tierMeta = tierItem.getItemMeta();
                tierMeta.setDisplayName(ChatColor.YELLOW + "Tier " + i + " (Available)");
                int cost = plugin.getConfigManager().getUpgradeCost(currentTier, i);
                lore.add(ChatColor.GOLD + "Cost: " + ChatColor.WHITE + cost + " diamonds");
            } else {
                tierItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                tierMeta = tierItem.getItemMeta();
                tierMeta.setDisplayName(ChatColor.RED + "Tier " + i + " (Locked)");
            }
            tierMeta.setLore(lore);
            tierItem.setItemMeta(tierMeta);
            int slot = (i <= 3) ? (10 + (i - 1) * 2) : (28 + (i - 4) * 2);
            gui.setItem(slot, tierItem);
        }

        gui.setItem(49, createNamedItem(Material.ARROW, ChatColor.GRAY + "Back"));
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void openDeleteConfirmationGUI(Player player, Territory territory) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm Deletion");
        gui.setItem(4, createNamedItem(Material.BARRIER, ChatColor.DARK_RED + "⚠ WARNING ⚠", ChatColor.RED + "This action CANNOT be undone!", ChatColor.YELLOW + "Tier: " + territory.getTier(), ChatColor.YELLOW + "Radius: " + territory.getRadius()));
        gui.setItem(11, createNamedItem(Material.RED_WOOL, ChatColor.DARK_RED + "CONFIRM DELETION"));
        gui.setItem(15, createNamedItem(Material.LIME_WOOL, ChatColor.GREEN + "CANCEL"));
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        event.setCancelled(true);
        Location beaconLoc = findNearbyBeacon(player);
        if (beaconLoc == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Could not find the beacon associated with this action.");
            return;
        }

        Territory territory = plugin.getTerritoryManager().getTerritoryByLocation(beaconLoc);
        boolean isOwner = territory != null && territory.getOwnerUUID().equals(player.getUniqueId());

        if (title.equals(ChatColor.DARK_GREEN + "Create Territory")) {
            if (clickedItem.getType() == Material.EMERALD_BLOCK) {
                player.closeInventory();
                plugin.getTerritoryManager().createTerritory(player, beaconLoc);
            } else if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        } else if (title.equals(ChatColor.DARK_GREEN + "Territory Management") && isOwner) {
            if (clickedItem.getType() == Material.DIAMOND) {
                openUpgradeGUI(player, territory);
            } else if (clickedItem.getType() == Material.TNT) {
                openDeleteConfirmationGUI(player, territory);
            } else if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        } else if (title.equals(ChatColor.DARK_PURPLE + "Upgrade Territory") && isOwner) {
            if (clickedItem.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
                int targetTier = Character.getNumericValue(ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).charAt(5));
                int cost = plugin.getConfigManager().getUpgradeCost(territory.getTier(), targetTier);
                if (player.getInventory().contains(Material.DIAMOND, cost)) {
                    player.getInventory().removeItem(new ItemStack(Material.DIAMOND, cost));
                    player.closeInventory();
                    plugin.getTerritoryManager().upgradeTerritory(player, territory, targetTier);
                } else {
                    player.sendMessage(ChatColor.RED + "You need " + cost + " diamonds to upgrade.");
                    player.closeInventory();
                }
            } else if (clickedItem.getType() == Material.ARROW) {
                openTerritoryInfoGUI(player, territory);
            }
        } else if (title.equals(ChatColor.DARK_RED + "Confirm Deletion") && isOwner) {
            if (clickedItem.getType() == Material.RED_WOOL) {
                player.closeInventory();
                plugin.getTerritoryManager().deleteTerritory(player, territory, beaconLoc);
            } else if (clickedItem.getType() == Material.LIME_WOOL) {
                openTerritoryInfoGUI(player, territory);
            }
        }
    }

    private void addCancelButton(Inventory gui, int slot) {
        gui.setItem(slot, createNamedItem(Material.BARRIER, ChatColor.RED + "Close"));
    }

    private ItemStack createNamedItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            if (!line.isEmpty()) loreList.add(line);
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private void fillEmpty(Inventory inventory) {
        ItemStack filler = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private Location findNearbyBeacon(Player player) {
        for (Location beaconLoc : plugin.getTerritoryManager().getAllTerritories().stream().map(Territory::getBeaconLocation).toList()) {
            if (beaconLoc.getWorld().equals(player.getWorld()) && beaconLoc.distance(player.getLocation()) < 15) {
                return beaconLoc;
            }
        }
        for (int x = -8; x <= 8; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -8; z <= 8; z++) {
                    Location blockLoc = player.getLocation().getBlock().getRelative(x, y, z).getLocation();
                    if (blockLoc.getBlock().getType() == Material.BEACON) {
                        return blockLoc;
                    }
                }
            }
        }
        return null;
    }
}