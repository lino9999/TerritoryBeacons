package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TerritoryManager {

    private final TerritoryBeacons plugin;
    private final Map<Location, Territory> territories = new ConcurrentHashMap<>();
    private final Map<Location, BukkitTask> activeEffects = new ConcurrentHashMap<>();

    public TerritoryManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
    }

    public void loadTerritories() {
        plugin.getDatabaseManager().loadTerritories(this);
        plugin.getLogger().info("Loaded " + territories.size() + " territories from database.");
    }

    public void saveAndClearTerritories() {
        for (Territory territory : territories.values()) {
            removeTerritoryBorder(territory);
            plugin.getDatabaseManager().updateTerritoryInDatabase(territory);
            if (plugin.getPl3xMapManager() != null) {
                plugin.getPl3xMapManager().removeTerritoryMarker(territory);
            }
        }
        activeEffects.values().forEach(BukkitTask::cancel);
        activeEffects.clear();
        territories.clear();
    }

    public void addTerritory(Location location, Territory territory) {
        territories.put(location, territory);
        plugin.getPlayerManager().updatePlayerTerritoryCount(territory.getOwnerUUID());
        if (plugin.getPl3xMapManager() != null) {
            plugin.getPl3xMapManager().addTerritoryMarker(territory);
        }
    }

    public void removeTerritory(Location location) {
        Territory territory = territories.remove(location);
        if (territory != null) {
            removeTerritoryBorder(territory);
            if (activeEffects.containsKey(location)) {
                activeEffects.get(location).cancel();
                activeEffects.remove(location);
            }
            plugin.getDatabaseManager().removeTerritoryFromDatabase(territory);
            plugin.getPlayerManager().updatePlayerTerritoryCount(territory.getOwnerUUID());
            if (plugin.getPl3xMapManager() != null) {
                plugin.getPl3xMapManager().removeTerritoryMarker(territory);
            }
        }
    }

    public Territory getTerritoryAt(Location location) {
        for (Territory territory : territories.values()) {
            if (territory.contains(location)) {
                return territory;
            }
        }
        return null;
    }

    public Territory getTerritoryByLocation(Location location) {
        return territories.get(location);
    }

    public Territory getTerritoryByOwner(UUID ownerUUID) {
        for (Territory territory : territories.values()) {
            if (territory.getOwnerUUID().equals(ownerUUID)) {
                return territory;
            }
        }
        return null;
    }

    public Collection<Territory> getAllTerritories() {
        return territories.values();
    }

    public int getPlayerTerritoryCount(UUID playerUUID) {
        return (int) territories.values().stream()
                .filter(t -> t.getOwnerUUID().equals(playerUUID))
                .count();
    }

    public boolean isCloseToOtherTerritory(Location loc, int newRadius) {
        for (Territory territory : territories.values()) {
            if (!loc.getWorld().equals(territory.getBeaconLocation().getWorld())) {
                continue;
            }
            double distance = loc.distance(territory.getBeaconLocation());
            if (distance < (territory.getRadius() + newRadius)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCloseToBeacon(Location loc) {
        int minDistance = plugin.getConfigManager().getMinimumBeaconDistance();
        for (Location beaconLoc : territories.keySet()) {
            if (beaconLoc.getWorld().equals(loc.getWorld())) {
                if (beaconLoc.distance(loc) < minDistance) {
                    return true;
                }
            }
        }
        return false;
    }

    public void createTerritory(Player owner, Location loc) {
        int tier = 1;
        int radius = plugin.getConfigManager().getRadiusForTier(tier);
        Territory territory = new Territory(owner.getUniqueId(), owner.getName(), loc, radius, tier);

        addTerritory(loc, territory);
        plugin.getDatabaseManager().saveTerritoryToDatabase(territory);

        createTerritoryBorder(loc, territory);
        spawnCreationEffect(loc);

        owner.sendMessage(ChatColor.GREEN + "Territory created successfully!");
        owner.sendMessage(ChatColor.AQUA + "Radius: " + radius + " blocks");
        owner.sendMessage(ChatColor.AQUA + "Tier: " + tier);
        Bukkit.broadcastMessage(ChatColor.YELLOW + owner.getName() + " has created a new territory!");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    public void deleteTerritory(Player owner, Territory territory, Location beaconLoc) {
        removeTerritory(beaconLoc);

        if (beaconLoc.getBlock().getType() == Material.BEACON) {
            beaconLoc.getBlock().setType(Material.AIR);
            beaconLoc.getWorld().dropItemNaturally(beaconLoc, new ItemStack(Material.BEACON));
        }

        owner.sendMessage(ChatColor.GREEN + "Your territory has been successfully deleted!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + owner.getName() + " has deleted their territory!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
        }
    }

    public void upgradeTerritory(Player player, Territory territory, int targetTier) {
        int newRadius = plugin.getConfigManager().getRadiusForTier(targetTier);
        Location beaconLoc = territory.getBeaconLocation();

        Territory newTerritory = new Territory(territory.getOwnerUUID(), territory.getOwnerName(), beaconLoc, newRadius, targetTier);
        newTerritory.setInfluence(territory.getInfluence());
        territory.getTrustedPlayers().forEach(newTerritory::addTrustedPlayer);

        removeTerritoryBorder(territory);
        territories.put(beaconLoc, newTerritory);
        createTerritoryBorder(beaconLoc, newTerritory);

        if (plugin.getPl3xMapManager() != null) {
            plugin.getPl3xMapManager().addTerritoryMarker(newTerritory);
        }

        plugin.getDatabaseManager().updateTerritoryInDatabase(newTerritory);

        player.sendMessage(ChatColor.GREEN + "Territory upgraded to tier " + targetTier + "!");
        player.sendMessage(ChatColor.AQUA + "New radius: " + newRadius + " blocks");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public void createTerritoryBorder(Location center, Territory territory) {
        removeTerritoryBorder(territory);
        territory.clearBorderBlocks();
        World world = center.getWorld();
        int radius = territory.getRadius();

        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            double x = center.getX() + radius * Math.cos(rad);
            double z = center.getZ() + radius * Math.sin(rad);
            Location borderLoc = new Location(world, x, world.getMaxHeight() - 1, z);
            Location placeLoc = world.getHighestBlockAt(borderLoc).getLocation().add(0, 1, 0);

            if (placeLoc.getBlock().getType() == Material.AIR && isOnBorder(placeLoc, center, radius)) {
                if (placeLoc.getBlock().getRelative(0, -1, 0).getType().isSolid()) {
                    placeLoc.getBlock().setType(Material.TORCH);
                    territory.addBorderBlock(placeLoc);
                }
            }
        }
    }

    public void removeTerritoryBorder(Territory territory) {
        Set<Location> borderBlocksCopy = new HashSet<>(territory.getBorderBlocks());
        for (Location loc : borderBlocksCopy) {
            if (loc.getBlock().getType() == Material.TORCH) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        territory.clearBorderBlocks();
    }

    private boolean isOnBorder(Location loc, Location center, int radius) {
        double distance = Math.sqrt(
                Math.pow(loc.getX() - center.getX(), 2) +
                        Math.pow(loc.getZ() - center.getZ(), 2)
        );
        return Math.abs(distance - radius) < 1.5;
    }

    public void spawnCreationEffect(Location loc) {
        if (activeEffects.containsKey(loc)) {
            activeEffects.get(loc).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            double radius = 0;
            @Override
            public void run() {
                if (radius > 10) {
                    activeEffects.remove(loc);
                    cancel();
                    return;
                }
                for (int angle = 0; angle < 360; angle += 10) {
                    double rad = Math.toRadians(angle);
                    double x = loc.getX() + 0.5 + radius * Math.cos(rad);
                    double z = loc.getZ() + 0.5 + radius * Math.sin(rad);
                    loc.getWorld().spawnParticle(Particle.END_ROD, x, loc.getY() + 1, z, 1, 0, 0.1, 0, 0.05);
                }
                radius += 0.5;
            }
        }.runTaskTimer(plugin, 0, 2);
        activeEffects.put(loc, task);
    }
}