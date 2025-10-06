package com.Lino.territoryBeacons.listeners;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import com.Lino.territoryBeacons.managers.ConfigManager;
import com.Lino.territoryBeacons.managers.PlayerManager;
import com.Lino.territoryBeacons.managers.TerritoryManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Iterator;

public class TerritoryListener implements Listener {

    private final TerritoryBeacons plugin;
    private final TerritoryManager territoryManager;
    private final PlayerManager playerManager;
    private final ConfigManager configManager;

    public TerritoryListener(TerritoryBeacons plugin) {
        this.plugin = plugin;
        this.territoryManager = plugin.getTerritoryManager();
        this.playerManager = plugin.getPlayerManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (playerManager.getPlayerTerritoryCount(player.getUniqueId()) >= configManager.getMaxTerritoriesPerPlayer()) {
            player.sendMessage(ChatColor.RED + "You have reached the maximum number of territories (" + configManager.getMaxTerritoriesPerPlayer() + ")!");
            event.setCancelled(true);
            return;
        }

        if (territoryManager.isCloseToBeacon(loc)) {
            player.sendMessage(ChatColor.RED + "This location is too close to another beacon! Minimum distance: " + configManager.getMinimumBeaconDistance() + " blocks");
            event.setCancelled(true);
            return;
        }

        int tier1Radius = configManager.getRadiusForTier(1);
        if (territoryManager.isCloseToOtherTerritory(loc, tier1Radius)) {
            player.sendMessage(ChatColor.RED + "This location is too close to another territory!");
            event.setCancelled(true);
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Beacon placed! Right-click the beacon to create the territory.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Territory territory = territoryManager.getTerritoryByLocation(event.getBlock().getLocation());
        if (territory != null) {
            Player breaker = event.getPlayer();
            if (!territory.getOwnerUUID().equals(breaker.getUniqueId()) && !breaker.hasPermission("territory.admin")) {
                breaker.sendMessage(ChatColor.RED + "You cannot destroy this beacon! Only the owner can.");
                event.setCancelled(true);
                return;
            }
            territoryManager.deleteTerritory(breaker, territory, event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.BEACON) return;

        Territory territory = territoryManager.getTerritoryAt(event.getBlock().getLocation());
        if (territory != null) {
            if (territory.getBorderBlocks().contains(event.getBlock().getLocation())) {
                event.getPlayer().sendMessage(ChatColor.RED + "You cannot break territory boundaries!");
                event.setCancelled(true);
                return;
            }
            if (!territory.canBuild(event.getPlayer())) {
                event.getPlayer().sendMessage(ChatColor.RED + "You cannot destroy blocks in " + territory.getOwnerName() + "'s territory!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.BEACON) return;

        Territory territory = territoryManager.getTerritoryAt(event.getBlock().getLocation());
        if (territory != null && !territory.canBuild(event.getPlayer())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot build in " + territory.getOwnerName() + "'s territory!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.BEACON) {
            event.setCancelled(true);
            Location loc = block.getLocation();
            Territory territory = territoryManager.getTerritoryByLocation(loc);
            if (territory != null) {
                plugin.getTerritoryGUI().openTerritoryInfoGUI(player, territory);
            } else {
                plugin.getTerritoryGUI().openCreationGUI(player, loc);
            }
            return;
        }

        if (configManager.shouldProtectContainers() && block.getState() instanceof Container) {
            Territory territory = territoryManager.getTerritoryAt(block.getLocation());
            if (territory != null && !territory.canBuild(player)) {
                player.sendMessage(ChatColor.RED + "You cannot access containers in " + territory.getOwnerName() + "'s territory!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!configManager.shouldPreventExplosions()) return;
        event.blockList().removeIf(block -> territoryManager.getTerritoryAt(block.getLocation()) != null);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerManager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        plugin.getTerritoryGUI().handleInventoryClick(event);
    }
}