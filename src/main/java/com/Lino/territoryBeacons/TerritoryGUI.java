package com.Lino.territoryBeacons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Beacon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

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

        int tier = 1;
        int radius = plugin.getRadiusForTier(tier);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to create your territory");
        lore.add("");
        lore.add(ChatColor.AQUA + "Starting Level: " + ChatColor.WHITE + tier);
        lore.add(ChatColor.AQUA + "Starting Radius: " + ChatColor.WHITE + radius + " blocks");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Territories Owned: " + ChatColor.WHITE +
                plugin.getPlayerTerritoryCount(player) + "/" + plugin.getMaxTerritoriesPerPlayer());

        createMeta.setLore(lore);
        createButton.setItemMeta(createMeta);

        ItemStack cancelButton = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancelButton.setItemMeta(cancelMeta);

        gui.setItem(13, createButton);
        gui.setItem(22, cancelButton);

        fillEmpty(gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
    }

    public void openTerritoryInfoGUI(Player player, Territory territory, Location beaconLocation) {
        Inventory gui = Bukkit.createInventory(null, 45, ChatColor.DARK_GREEN + "Territory Management");

        ItemStack infoItem = new ItemStack(Material.BEACON);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Territory Information");

        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.AQUA + "Owner: " + ChatColor.WHITE + territory.getOwnerName());
        infoLore.add(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + territory.getRadius() + " blocks");
        infoLore.add(ChatColor.AQUA + "Level: " + ChatColor.WHITE + territory.getTier());

        Player owner = Bukkit.getPlayer(territory.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            infoLore.add(ChatColor.GREEN + "Territory is active (Owner online)");
        } else {
            long lastSeen = plugin.getPlayerLastSeen(territory.getOwnerUUID());
            long currentTime = System.currentTimeMillis();
            long millisecondsOffline = currentTime - lastSeen;
            long decayStartMillis = plugin.getDecayTime() * 60L * 60L * 1000L;

            if (millisecondsOffline < decayStartMillis) {
                long remainingMillis = decayStartMillis - millisecondsOffline;
                long hours = remainingMillis / (1000 * 60 * 60);
                long minutes = (remainingMillis % (1000 * 60 * 60)) / (1000 * 60);
                infoLore.add(ChatColor.RED + "Decay in: " + ChatColor.WHITE +
                        hours + "h " + minutes + "m");
            } else if (territory.getInfluence() > 0) {
                long decayHours = (long) (territory.getInfluence() * 10);
                infoLore.add(ChatColor.RED + "Decaying! " + ChatColor.WHITE +
                        decayHours + "h remaining");
            } else {
                infoLore.add(ChatColor.DARK_RED + "Territory will be destroyed soon!");
            }
        }

        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);

        ItemStack upgradeButton = new ItemStack(Material.DIAMOND);
        ItemMeta upgradeMeta = upgradeButton.getItemMeta();
        upgradeMeta.setDisplayName(ChatColor.AQUA + "Upgrade Territory");

        List<String> upgradeLore = new ArrayList<>();
        if (territory.getOwnerUUID().equals(player.getUniqueId())) {
            upgradeLore.add(ChatColor.GRAY + "Click to upgrade your territory");
        } else {
            upgradeLore.add(ChatColor.RED + "Only the owner can upgrade");
        }
        upgradeMeta.setLore(upgradeLore);
        upgradeButton.setItemMeta(upgradeMeta);

        ItemStack trustedButton = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta trustedMeta = trustedButton.getItemMeta();
        trustedMeta.setDisplayName(ChatColor.GREEN + "Trusted Players");

        List<String> trustedLore = new ArrayList<>();
        trustedLore.add(ChatColor.GRAY + "Players: " + territory.getTrustedPlayers().size());
        if (territory.getOwnerUUID().equals(player.getUniqueId())) {
            trustedLore.add(ChatColor.YELLOW + "Use /territory trust <player>");
        }
        trustedMeta.setLore(trustedLore);
        trustedButton.setItemMeta(trustedMeta);

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        closeButton.setItemMeta(closeMeta);

        gui.setItem(13, infoItem);
        if (territory.getOwnerUUID().equals(player.getUniqueId())) {
            gui.setItem(29, upgradeButton);

            ItemStack deleteButton = new ItemStack(Material.TNT);
            ItemMeta deleteMeta = deleteButton.getItemMeta();
            deleteMeta.setDisplayName(ChatColor.DARK_RED + "Delete Territory");
            List<String> deleteLore = new ArrayList<>();
            deleteLore.add(ChatColor.RED + "WARNING: This action cannot be undone!");
            deleteLore.add(ChatColor.GRAY + "Click to destroy your territory");
            deleteMeta.setLore(deleteLore);
            deleteButton.setItemMeta(deleteMeta);
            gui.setItem(33, deleteButton);
        }
        gui.setItem(31, trustedButton);
        gui.setItem(40, closeButton);

        fillEmpty(gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
    }

    public void openUpgradeGUI(Player player, Territory territory, Location beaconLocation) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Upgrade Territory");

        int currentTier = territory.getTier();

        for (int i = 1; i <= 6; i++) {
            ItemStack tierItem;
            ItemMeta tierMeta;

            if (i <= currentTier) {
                tierItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                tierMeta = tierItem.getItemMeta();
                tierMeta.setDisplayName(ChatColor.GREEN + "Tier " + i + " (Owned)");
            } else if (i == currentTier + 1) {
                tierItem = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                tierMeta = tierItem.getItemMeta();
                tierMeta.setDisplayName(ChatColor.YELLOW + "Tier " + i + " (Available)");
            } else {
                tierItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                tierMeta = tierItem.getItemMeta();
                tierMeta.setDisplayName(ChatColor.RED + "Tier " + i + " (Locked)");
            }

            List<String> tierLore = new ArrayList<>();
            int radius = plugin.getRadiusForTier(i);
            tierLore.add(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + radius + " blocks");

            if (i > currentTier) {
                int cost = plugin.getUpgradeCost(currentTier, i);
                tierLore.add(ChatColor.GOLD + "Cost: " + ChatColor.WHITE + cost + " diamonds");
            }

            tierMeta.setLore(tierLore);
            tierItem.setItemMeta(tierMeta);

            int slot = 10 + (i - 1) * 2;
            if (i > 3) {
                slot = 28 + (i - 4) * 2;
            }
            gui.setItem(slot, tierItem);
        }

        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(ChatColor.GRAY + "Back");
        backButton.setItemMeta(backMeta);

        gui.setItem(49, backButton);

        fillEmpty(gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
    }

    public void openDeleteConfirmationGUI(Player player, Territory territory, Location beaconLocation) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm Territory Deletion");

        ItemStack warningItem = new ItemStack(Material.BARRIER);
        ItemMeta warningMeta = warningItem.getItemMeta();
        warningMeta.setDisplayName(ChatColor.DARK_RED + "⚠ WARNING ⚠");
        List<String> warningLore = new ArrayList<>();
        warningLore.add(ChatColor.RED + "You are about to delete your territory!");
        warningLore.add(ChatColor.RED + "This action CANNOT be undone!");
        warningLore.add("");
        warningLore.add(ChatColor.YELLOW + "Territory: " + ChatColor.WHITE + territory.getRadius() + " blocks radius");
        warningLore.add(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + territory.getTier());
        warningMeta.setLore(warningLore);
        warningItem.setItemMeta(warningMeta);

        ItemStack confirmButton = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.DARK_RED + "CONFIRM DELETION");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ChatColor.RED + "Click to permanently delete");
        confirmLore.add(ChatColor.RED + "your territory and beacon!");
        confirmMeta.setLore(confirmLore);
        confirmButton.setItemMeta(confirmMeta);

        ItemStack cancelButton = new ItemStack(Material.LIME_WOOL);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.GREEN + "CANCEL");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add(ChatColor.GRAY + "Keep your territory");
        cancelMeta.setLore(cancelLore);
        cancelButton.setItemMeta(cancelMeta);

        gui.setItem(4, warningItem);
        gui.setItem(11, confirmButton);
        gui.setItem(15, cancelButton);

        fillEmpty(gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    private void fillEmpty(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
}