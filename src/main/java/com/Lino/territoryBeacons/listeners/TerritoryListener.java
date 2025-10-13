package com.Lino.territoryBeacons.listeners;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import com.Lino.territoryBeacons.managers.ConfigManager;
import com.Lino.territoryBeacons.managers.MessageManager;
import com.Lino.territoryBeacons.managers.PlayerManager;
import com.Lino.territoryBeacons.managers.TerritoryManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TerritoryListener implements Listener {

    private final TerritoryBeacons plugin;
    private final TerritoryManager territoryManager;
    private final PlayerManager playerManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public TerritoryListener(TerritoryBeacons plugin) {
        this.plugin = plugin;
        this.territoryManager = plugin.getTerritoryManager();
        this.playerManager = plugin.getPlayerManager();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (playerManager.getPlayerTerritoryCount(player.getUniqueId()) >= configManager.getMaxTerritoriesPerPlayer()) {
            player.sendMessage(messageManager.get("max-territories-reached", "%max%", String.valueOf(configManager.getMaxTerritoriesPerPlayer())));
            event.setCancelled(true);
            return;
        }

        if (territoryManager.isCloseToBeacon(loc)) {
            player.sendMessage(messageManager.get("too-close-to-beacon", "%distance%", String.valueOf(configManager.getMinimumBeaconDistance())));
            event.setCancelled(true);
            return;
        }

        int tier1Radius = configManager.getRadiusForTier(1);
        if (territoryManager.isCloseToOtherTerritory(loc, tier1Radius)) {
            player.sendMessage(messageManager.get("too-close-to-territory"));
            event.setCancelled(true);
            return;
        }
        player.sendMessage(messageManager.get("beacon-placed-reminder"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Territory territory = territoryManager.getTerritoryByLocation(event.getBlock().getLocation());
        if (territory != null) {
            Player breaker = event.getPlayer();
            if (!territory.getOwnerUUID().equals(breaker.getUniqueId()) && !breaker.hasPermission("territory.admin")) {
                breaker.sendMessage(messageManager.get("cannot-break-beacon"));
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
                event.getPlayer().sendMessage(messageManager.get("cannot-break-boundaries"));
                event.setCancelled(true);
                return;
            }
            if (!territory.canBuild(event.getPlayer())) {
                event.getPlayer().sendMessage(messageManager.get("cannot-destroy-here", "%owner%", territory.getOwnerName()));
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.BEACON) return;

        Territory territory = territoryManager.getTerritoryAt(event.getBlock().getLocation());
        if (territory != null && !territory.canBuild(event.getPlayer())) {
            event.getPlayer().sendMessage(messageManager.get("cannot-build-here", "%owner%", territory.getOwnerName()));
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
                if (playerManager.getPlayerTerritoryCount(player.getUniqueId()) >= configManager.getMaxTerritoriesPerPlayer()) {
                    player.sendMessage(messageManager.get("max-territories-reached", "%max%", String.valueOf(configManager.getMaxTerritoriesPerPlayer())));
                    return;
                }
                plugin.getTerritoryGUI().openCreationGUI(player, loc);
            }
            return;
        }

        if (configManager.shouldProtectContainers() && block.getState() instanceof Container) {
            Territory territory = territoryManager.getTerritoryAt(block.getLocation());
            if (territory != null && !territory.canBuild(player)) {
                player.sendMessage(messageManager.get("cannot-access-containers", "%owner%", territory.getOwnerName()));
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Territory territory = territoryManager.getTerritoryAt(victim.getLocation());

        if (territory != null && !territory.isPvpEnabled()) {
            event.setCancelled(true);
            Player attacker = (Player) event.getDamager();
            attacker.sendMessage(messageManager.get("pvp-disabled"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }

        Territory territory = territoryManager.getTerritoryAt(event.getLocation());
        if (territory != null && !territory.isMobSpawningEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!configManager.shouldPreventExplosions()) return;
        event.blockList().removeIf(b -> territoryManager.getTerritoryAt(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        Territory territory = territoryManager.getTerritoryAt(player.getLocation());
        if (territory != null && territory.hasEffect("saturation")) {
            if (territory.canBuild(player)) { // Apply to owner and trusted
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(10f);
            }
        }
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