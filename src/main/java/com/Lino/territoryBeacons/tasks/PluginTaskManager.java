package com.Lino.territoryBeacons.tasks;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;

public class PluginTaskManager {

    private final TerritoryBeacons plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public PluginTaskManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
    }

    public void startAllTasks() {
        tasks.add(startDecayTask());
        tasks.add(startSaveTask());
        tasks.add(startTerritoryCheckTask());
    }

    public void cancelAllTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private BukkitTask startDecayTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long decayTimeMillis = plugin.getConfigManager().getDecayTime() * 3600000L;

                plugin.getTerritoryManager().getAllTerritories().forEach(territory -> {
                    Player owner = Bukkit.getPlayer(territory.getOwnerUUID());
                    if (owner == null || !owner.isOnline()) {
                        long lastSeen = plugin.getPlayerManager().getPlayerLastSeen(territory.getOwnerUUID());
                        long hoursOffline = (currentTime - lastSeen) / 3600000L;

                        if (hoursOffline >= plugin.getConfigManager().getDecayTime()) {
                            double decay = 0.1 * (hoursOffline - plugin.getConfigManager().getDecayTime() + 1);
                            territory.decayInfluence(decay);

                            if (territory.getInfluence() <= 0) {
                                Location beaconLoc = territory.getBeaconLocation();
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    plugin.getTerritoryManager().removeTerritory(beaconLoc);
                                    if (beaconLoc.getBlock().getType() == Material.BEACON) {
                                        beaconLoc.getBlock().setType(Material.AIR);
                                        beaconLoc.getWorld().dropItemNaturally(beaconLoc, new ItemStack(Material.BEACON));
                                    }
                                    Bukkit.broadcastMessage(ChatColor.RED + "The territory of " + territory.getOwnerName() + " has decayed due to inactivity!");
                                    Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f));
                                });
                            }
                        }
                    } else {
                        territory.restoreInfluence(0.05);
                    }
                });
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 60 * 60, 20 * 60 * 60);
    }

    private BukkitTask startSaveTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getTerritoryManager().getAllTerritories().forEach(t -> plugin.getDatabaseManager().updateTerritoryInDatabase(t));
                plugin.getPlayerManager().saveAndClearPlayerData();
                plugin.getPlayerManager().loadPlayerData();
                plugin.getPlayerManager().cleanupUnusedData();
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 60 * 5, 20 * 60 * 5);
    }

    private BukkitTask startTerritoryCheckTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getOnlinePlayers().forEach(plugin.getPlayerManager()::checkPlayerTerritory);
            }
        }.runTaskTimer(plugin, 0, 20);
    }
}