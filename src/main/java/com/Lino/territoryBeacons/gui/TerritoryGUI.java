package com.Lino.territoryBeacons.gui;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import com.Lino.territoryBeacons.managers.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TerritoryGUI {

    private final TerritoryBeacons plugin;
    private final MessageManager messageManager;

    public TerritoryGUI(TerritoryBeacons plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    public void openCreationGUI(Player player, Location beaconLocation) {
        Inventory gui = Bukkit.createInventory(null, 27, messageManager.get("gui-title-create"));

        List<String> lore = new ArrayList<>();
        lore.add(messageManager.get("gui-create-lore-1"));
        lore.add("");
        lore.add(messageManager.get("gui-create-lore-2", "%tier%", "1"));
        lore.add(messageManager.get("gui-create-lore-3", "%radius%", String.valueOf(plugin.getConfigManager().getRadiusForTier(1))));
        lore.add("");
        lore.add(messageManager.get("gui-create-lore-4",
                "%count%", String.valueOf(plugin.getPlayerManager().getPlayerTerritoryCount(player.getUniqueId())),
                "%max%", String.valueOf(plugin.getConfigManager().getMaxTerritoriesPerPlayer())));

        gui.setItem(13, createNamedItem(Material.EMERALD_BLOCK, messageManager.get("gui-create-button"), lore));
        addCloseButton(gui, 22);
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void openTerritoryInfoGUI(Player player, Territory territory) {
        Inventory gui = Bukkit.createInventory(null, 45, messageManager.get("gui-title-management"));

        List<String> infoLore = new ArrayList<>();
        infoLore.add(messageManager.get("gui-info-lore-owner", "%owner%", territory.getOwnerName()));
        infoLore.add(messageManager.get("gui-info-lore-radius", "%radius%", String.valueOf(territory.getRadius())));
        infoLore.add(messageManager.get("gui-info-lore-tier", "%tier%", String.valueOf(territory.getTier())));
        infoLore.add(messageManager.get("gui-info-lore-influence", "%influence%", String.format("%.1f", territory.getInfluence() * 100)));

        Player owner = Bukkit.getPlayer(territory.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            infoLore.add(messageManager.get("gui-info-lore-active"));
        } else {
            long decayStartMillis = TimeUnit.HOURS.toMillis(plugin.getConfigManager().getDecayTime());
            long offlineMillis = System.currentTimeMillis() - plugin.getPlayerManager().getPlayerLastSeen(territory.getOwnerUUID());
            if (offlineMillis < decayStartMillis) {
                long remainingMillis = decayStartMillis - offlineMillis;
                long hours = TimeUnit.MILLISECONDS.toHours(remainingMillis);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60;
                infoLore.add(messageManager.get("gui-info-lore-decay-starts", "%time%", hours + "h " + minutes + "m"));
            } else {
                infoLore.add(messageManager.get("gui-info-lore-decaying"));
            }
        }
        gui.setItem(13, createNamedItem(Material.BEACON, messageManager.get("gui-info-button"), infoLore));

        boolean isOwner = territory.getOwnerUUID().equals(player.getUniqueId());
        if (isOwner) {
            gui.setItem(29, createNamedItem(Material.DIAMOND, messageManager.get("gui-upgrade-button"), messageManager.get("gui-upgrade-lore")));
            gui.setItem(33, createNamedItem(Material.TNT, messageManager.get("gui-delete-button"), messageManager.get("gui-delete-lore-1"), messageManager.get("gui-delete-lore-2")));
        }

        List<String> trustedLore = new ArrayList<>();
        trustedLore.add(messageManager.get("gui-trusted-lore-count", "%count%", String.valueOf(territory.getTrustedPlayers().size())));
        if (isOwner) {
            trustedLore.add(messageManager.get("gui-trusted-lore-command"));
        }
        gui.setItem(31, createNamedItem(Material.PLAYER_HEAD, messageManager.get("gui-trusted-button"), trustedLore));
        addCloseButton(gui, 40);
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void openUpgradeGUI(Player player, Territory territory) {
        Inventory gui = Bukkit.createInventory(null, 54, messageManager.get("gui-title-upgrade"));
        int currentTier = territory.getTier();

        for (int i = 1; i <= 6; i++) {
            int radius = plugin.getConfigManager().getRadiusForTier(i);
            List<String> lore = new ArrayList<>();
            lore.add(messageManager.get("gui-info-lore-radius", "%radius%", String.valueOf(radius)));

            Material material;
            String name;

            if (i <= currentTier) {
                material = Material.LIME_STAINED_GLASS_PANE;
                name = messageManager.get("gui-tier-owned", "%tier%", String.valueOf(i));
            } else if (i == currentTier + 1) {
                material = Material.YELLOW_STAINED_GLASS_PANE;
                name = messageManager.get("gui-tier-available", "%tier%", String.valueOf(i));
                int cost = plugin.getConfigManager().getUpgradeCost(currentTier, i);
                lore.add(messageManager.get("gui-tier-cost", "%cost%", String.valueOf(cost)));
            } else {
                material = Material.RED_STAINED_GLASS_PANE;
                name = messageManager.get("gui-tier-locked", "%tier%", String.valueOf(i));
            }

            int slot = (i <= 3) ? (10 + (i - 1) * 2) : (28 + (i - 4) * 2);
            gui.setItem(slot, createNamedItem(material, name, lore));
        }

        gui.setItem(49, createNamedItem(Material.ARROW, messageManager.get("gui-back-button")));
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void openDeleteConfirmationGUI(Player player, Territory territory) {
        Inventory gui = Bukkit.createInventory(null, 27, messageManager.get("gui-title-delete-confirm"));
        gui.setItem(4, createNamedItem(Material.BARRIER, messageManager.get("gui-delete-confirm-warning"),
                messageManager.get("gui-delete-confirm-lore"),
                messageManager.get("gui-info-lore-tier", "%tier%", String.valueOf(territory.getTier())),
                messageManager.get("gui-info-lore-radius", "%radius%", String.valueOf(territory.getRadius()))
        ));
        gui.setItem(11, createNamedItem(Material.RED_WOOL, messageManager.get("gui-delete-confirm-button")));
        gui.setItem(15, createNamedItem(Material.LIME_WOOL, messageManager.get("gui-cancel-button")));
        fillEmpty(gui);
        player.openInventory(gui);
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();
        if (!title.equals(messageManager.get("gui-title-create")) &&
                !title.equals(messageManager.get("gui-title-management")) &&
                !title.equals(messageManager.get("gui-title-upgrade")) &&
                !title.equals(messageManager.get("gui-title-delete-confirm"))) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().isAir() || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE)
            return;

        Location beaconLoc = findNearbyBeacon(player);
        if (beaconLoc == null) {
            player.closeInventory();
            player.sendMessage(messageManager.get("beacon-action-failed"));
            return;
        }

        Territory territory = plugin.getTerritoryManager().getTerritoryByLocation(beaconLoc);
        boolean isOwner = territory != null && territory.getOwnerUUID().equals(player.getUniqueId());

        if (title.equals(messageManager.get("gui-title-create"))) {
            if (clickedItem.getType() == Material.EMERALD_BLOCK) {
                player.closeInventory();
                plugin.getTerritoryManager().createTerritory(player, beaconLoc);
            } else if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        } else if (title.equals(messageManager.get("gui-title-management"))) {
            if (!isOwner) return;
            if (clickedItem.getType() == Material.DIAMOND) openUpgradeGUI(player, territory);
            else if (clickedItem.getType() == Material.TNT) openDeleteConfirmationGUI(player, territory);
            else if (clickedItem.getType() == Material.BARRIER) player.closeInventory();
        } else if (title.equals(messageManager.get("gui-title-upgrade"))) {
            if (!isOwner) return;
            if (clickedItem.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
                String name = clickedItem.getItemMeta().getDisplayName();
                int targetTier = Character.getNumericValue(name.charAt(5));
                int cost = plugin.getConfigManager().getUpgradeCost(territory.getTier(), targetTier);
                if (player.getInventory().contains(Material.DIAMOND, cost)) {
                    player.getInventory().removeItem(new ItemStack(Material.DIAMOND, cost));
                    player.closeInventory();
                    plugin.getTerritoryManager().upgradeTerritory(player, territory, targetTier);
                } else {
                    player.sendMessage(messageManager.get("need-more-diamonds", "%cost%", String.valueOf(cost)));
                    player.closeInventory();
                }
            } else if (clickedItem.getType() == Material.ARROW) {
                openTerritoryInfoGUI(player, territory);
            }
        } else if (title.equals(messageManager.get("gui-title-delete-confirm"))) {
            if (!isOwner) return;
            if (clickedItem.getType() == Material.RED_WOOL) {
                player.closeInventory();
                plugin.getTerritoryManager().deleteTerritory(player, territory, beaconLoc);
            } else if (clickedItem.getType() == Material.LIME_WOOL) {
                openTerritoryInfoGUI(player, territory);
            }
        }
    }

    private void addCloseButton(Inventory gui, int slot) {
        gui.setItem(slot, createNamedItem(Material.BARRIER, messageManager.get("gui-close-button")));
    }

    private ItemStack createNamedItem(Material material, String name, String... lore) {
        return createNamedItem(material, name, List.of(lore));
    }

    private ItemStack createNamedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = lore.stream().filter(line -> line != null && !line.isEmpty()).collect(Collectors.toList());
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
        for (Territory territory : plugin.getTerritoryManager().getAllTerritories()) {
            Location beaconLoc = territory.getBeaconLocation();
            if (beaconLoc.getWorld().equals(player.getWorld()) && beaconLoc.distanceSquared(player.getLocation()) < 225) { // 15*15
                return beaconLoc;
            }
        }
        int radius = 8;
        Location playerLoc = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location blockLoc = playerLoc.getBlock().getRelative(x, y, z).getLocation();
                    if (blockLoc.getBlock().getType() == Material.BEACON) {
                        return blockLoc;
                    }
                }
            }
        }
        return null;
    }
}