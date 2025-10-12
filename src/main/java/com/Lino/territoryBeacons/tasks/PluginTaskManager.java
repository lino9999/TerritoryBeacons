package com.Lino.territoryBeacons.tasks;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import com.Lino.territoryBeacons.managers.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class PluginTaskManager {

    private final TerritoryBeacons plugin;
    private final MessageManager messageManager;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<String, PotionEffectType> effectMap = new HashMap<>();

    public PluginTaskManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
        initializeEffectMap();
    }

    private void initializeEffectMap() {
        effectMap.put("regeneration", PotionEffectType.REGENERATION);
        effectMap.put("speed", PotionEffectType.SPEED);
        effectMap.put("jump", PotionEffectType.JUMP_BOOST);
        effectMap.put("haste", PotionEffectType.HASTE);
        effectMap.put("fire_resistance", PotionEffectType.FIRE_RESISTANCE);
        effectMap.put("strength", PotionEffectType.STRENGTH);
        effectMap.put("resistance", PotionEffectType.RESISTANCE);
        effectMap.put("luck", PotionEffectType.LUCK);
        effectMap.put("night_vision", PotionEffectType.NIGHT_VISION);
        effectMap.put("water_breathing", PotionEffectType.WATER_BREATHING);
    }

    public void startAllTasks() {
        tasks.add(startDecayTask());
        tasks.add(startSaveTask());
        tasks.add(startTerritoryCheckTask());
        tasks.add(startEffectsTask());
        if (plugin.getPl3xMapManager() != null) {
            tasks.add(startMapUpdateTask());
        }
    }

    public void cancelAllTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private BukkitTask startMapUpdateTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getTerritoryManager().getAllTerritories().forEach(
                        territory -> plugin.getPl3xMapManager().addOrUpdateTerritoryMarker(territory)
                );
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60, 20L * 60 * 5);
    }

    private BukkitTask startDecayTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

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
                                    Bukkit.broadcastMessage(messageManager.get("territory-decay-message", "%owner%", territory.getOwnerName()));
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
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 60 * 5, 20 * 60 * 5);
    }

    private BukkitTask startTerritoryCheckTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.getPlayerManager().checkPlayerTerritory(player);
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private BukkitTask startEffectsTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Territory territory = plugin.getTerritoryManager().getTerritoryAt(player.getLocation());
                    if (territory != null && territory.canBuild(player)) { // Apply to owner and trusted
                        for (String effectName : territory.getActiveEffects()) {
                            PotionEffectType effectType = effectMap.get(effectName);
                            if (effectType != null) {
                                player.addPotionEffect(new PotionEffect(effectType, 100, 0, true, false));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 80);
    }
}