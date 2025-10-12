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
    private String costType;
    private double upgradeCostMultiplier;
    private final Map<Integer, Integer> tierRadii = new HashMap<>();
    private final Map<String, Integer> upgradeCosts = new HashMap<>();
    private final Map<String, Double> effectCosts = new HashMap<>();
    private int maxTiers;

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
        costType = config.getString("economy.cost-type", "BOTH").toUpperCase();
        upgradeCostMultiplier = config.getDouble("economy.upgrade-cost-multiplier", 100.0);

        tierRadii.clear();
        upgradeCosts.clear();
        maxTiers = 0;
        if (config.isConfigurationSection("tiers")) {
            for (String key : config.getConfigurationSection("tiers").getKeys(false)) {
                maxTiers++;
                int tier = Integer.parseInt(key.split("-")[1]);
                int radius = config.getInt("tiers." + key + ".radius");
                tierRadii.put(tier, radius);
                if (tier > 1) {
                    int cost = config.getInt("tiers." + key + ".upgrade-cost");
                    upgradeCosts.put((tier - 1) + "-" + tier, cost);
                }
            }
        }


        effectCosts.clear();
        if (config.isConfigurationSection("effects")) {
            for (String effect : config.getConfigurationSection("effects").getKeys(false)) {
                effectCosts.put(effect, config.getDouble("effects." + effect + ".cost"));
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

    public String getCostType() {
        return costType;
    }

    public double getUpgradeCostMultiplier() {
        return upgradeCostMultiplier;
    }

    public int getRadiusForTier(int tier) {
        return tierRadii.getOrDefault(tier, 16);
    }

    public int getUpgradeCost(int fromTier, int toTier) {
        return upgradeCosts.getOrDefault(fromTier + "-" + toTier, 0);
    }

    public double getEffectCost(String effect) {
        return effectCosts.getOrDefault(effect, 0.0);
    }

    public int getMaxTiers() {
        return maxTiers;
    }
}