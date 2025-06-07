package com.Lino.territoryBeacons;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class Territory {
    private final UUID ownerUUID;
    private final String ownerName;
    private final Location beaconLocation;
    private final int radius;
    private final int tier;
    private double influence = 1.0;
    private Set<UUID> trustedPlayers = new HashSet<>();
    private Set<Location> borderBlocks = new HashSet<>();

    public Territory(UUID ownerUUID, String ownerName, Location beaconLocation, int radius, int tier) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.beaconLocation = beaconLocation;
        this.radius = radius;
        this.tier = tier;
    }

    public boolean contains(Location location) {
        if (!location.getWorld().equals(beaconLocation.getWorld())) {
            return false;
        }
        double distance = location.distance(beaconLocation);
        return distance <= radius;
    }

    public boolean overlaps(Location otherBeacon) {
        if (!otherBeacon.getWorld().equals(beaconLocation.getWorld())) {
            return false;
        }
        double distance = otherBeacon.distance(beaconLocation);
        return distance < (radius + 16 + 8);
    }

    public boolean canBuild(Player player) {
        if (player.getUniqueId().equals(ownerUUID)) {
            return true;
        }
        if (player.hasPermission("territory.admin")) {
            return true;
        }
        return trustedPlayers.contains(player.getUniqueId());
    }

    public void decayInfluence(double amount) {
        influence = Math.max(0.0, influence - amount);
    }

    public void restoreInfluence(double amount) {
        influence = Math.min(1.0, influence + amount);
    }

    public void addTrustedPlayer(UUID playerUUID) {
        trustedPlayers.add(playerUUID);
    }

    public void removeTrustedPlayer(UUID playerUUID) {
        trustedPlayers.remove(playerUUID);
    }

    public boolean isTrusted(UUID playerUUID) {
        return trustedPlayers.contains(playerUUID);
    }

    public void addBorderBlock(Location location) {
        borderBlocks.add(location);
    }

    public void removeBorderBlock(Location location) {
        borderBlocks.remove(location);
    }

    public Set<Location> getBorderBlocks() {
        return new HashSet<>(borderBlocks);
    }

    public void clearBorderBlocks() {
        borderBlocks.clear();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Location getBeaconLocation() {
        return beaconLocation;
    }

    public int getRadius() {
        return radius;
    }

    public int getTier() {
        return tier;
    }

    public double getInfluence() {
        return influence;
    }

    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }

    public void setInfluence(double influence) {
        this.influence = Math.max(0.0, Math.min(1.0, influence));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Territory territory = (Territory) obj;
        return beaconLocation.equals(territory.beaconLocation) &&
                ownerUUID.equals(territory.ownerUUID);
    }

    @Override
    public int hashCode() {
        return beaconLocation.hashCode() + ownerUUID.hashCode();
    }
}