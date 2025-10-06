package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.TerritoryBeacons;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final TerritoryBeacons plugin;
    private int decayTime;
    private int minimumBeaconDistance;
    private int maxTerritoriesPerPlayer;
    private boolean protectContainers;
    private boolean preventExplosions;
    private final Map<Integer, Integer> tierRadii = new HashMap<>();
    private final Map<String, Integer> upgradeCosts = new HashMap<>();

    public ConfigManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    public void loadConfigValues() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        decayTime = config.getInt("decay-time-hours", 160);
        minimumBeaconDistance = config.getInt("advanced.minimum-beacon-distance", 260);
        maxTerritoriesPerPlayer = config.getInt("advanced.max-territories-per-player", 1);
        protectContainers = config.getBoolean("advanced.protect-containers", true);
        preventExplosions = config.getBoolean("advanced.prevent-explosions", true);

        tierRadii.clear();
        upgradeCosts.clear();

        for (int i = 1; i <= 6; i++) {
            int radius = config.getInt("tiers.tier-" + i + ".radius", 16 + (i * 8));
            tierRadii.put(i, radius);
            if (i > 1) {
                int cost = config.getInt("tiers.tier-" + i + ".upgrade-cost", i * 10);
                upgradeCosts.put((i - 1) + "-" + i, cost);
            }
        }
    }

    public int getDecayTime() {
        return decayTime;
    }

    public int getMinimumBeaconDistance() {
        return minimumBeaconDistance;
    }

    public int getMaxTerritoriesPerPlayer() {
        return maxTerritoriesPerPlayer;
    }

    public boolean shouldProtectContainers() {
        return protectContainers;
    }

    public boolean shouldPreventExplosions() {
        return preventExplosions;
    }

    public int getRadiusForTier(int tier) {
        return tierRadii.getOrDefault(tier, 16 + (tier * 8));
    }

    public int getUpgradeCost(int fromTier, int toTier) {
        return upgradeCosts.getOrDefault(fromTier + "-" + toTier, toTier * 10);
    }
}