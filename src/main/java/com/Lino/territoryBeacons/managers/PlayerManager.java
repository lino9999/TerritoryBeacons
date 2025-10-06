package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final TerritoryBeacons plugin;
    private final Map<UUID, Territory> playerCurrentTerritory = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerTerritoryCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastSeen = new ConcurrentHashMap<>();

    public PlayerManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
    }

    public void loadPlayerData() {
        plugin.getDatabaseManager().loadPlayerData(this);
    }

    public void saveAndClearPlayerData() {
        plugin.getDatabaseManager().saveAllPlayerData(playerLastSeen);
        playerCurrentTerritory.clear();
        playerTerritoryCount.clear();
        playerLastSeen.clear();
    }

    public void updatePlayerLastSeen(UUID playerUUID, long lastSeen) {
        playerLastSeen.put(playerUUID, lastSeen);
    }

    public long getPlayerLastSeen(UUID playerUUID) {
        return playerLastSeen.getOrDefault(playerUUID, System.currentTimeMillis());
    }

    public void updatePlayerTerritoryCount(UUID playerUUID) {
        int count = plugin.getTerritoryManager().getPlayerTerritoryCount(playerUUID);
        playerTerritoryCount.put(playerUUID, count);
    }

    public int getPlayerTerritoryCount(UUID playerUUID) {
        return playerTerritoryCount.getOrDefault(playerUUID, plugin.getTerritoryManager().getPlayerTerritoryCount(playerUUID));
    }

    public void onPlayerJoin(Player player) {
        playerLastSeen.put(player.getUniqueId(), System.currentTimeMillis());
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayerTerritory(player), 20L);
    }

    public void onPlayerQuit(Player player) {
        playerCurrentTerritory.remove(player.getUniqueId());
        playerLastSeen.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void checkPlayerTerritory(Player player) {
        UUID playerUUID = player.getUniqueId();
        Territory currentTerritory = plugin.getTerritoryManager().getTerritoryAt(player.getLocation());
        Territory previousTerritory = playerCurrentTerritory.get(playerUUID);

        if (currentTerritory != null && !currentTerritory.equals(previousTerritory)) {
            playerCurrentTerritory.put(playerUUID, currentTerritory);
            String title;
            String subtitle;
            if (currentTerritory.getOwnerUUID().equals(playerUUID)) {
                title = ChatColor.GREEN + "Your Territory";
                subtitle = ChatColor.GRAY + "You are safe here";
            } else {
                title = ChatColor.AQUA + "Territory of";
                subtitle = currentTerritory.isTrusted(playerUUID)
                        ? ChatColor.GREEN + currentTerritory.getOwnerName() + ChatColor.GRAY + " (Trusted)"
                        : ChatColor.GOLD + currentTerritory.getOwnerName();
            }
            player.sendTitle(title, subtitle, 10, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
        } else if (currentTerritory == null && previousTerritory != null) {
            playerCurrentTerritory.remove(playerUUID);
            player.sendTitle(ChatColor.GRAY + "Wilderness", ChatColor.DARK_GRAY + "You have left the protected area", 10, 30, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        }
    }

    public void cleanupUnusedData() {
        Iterator<Map.Entry<UUID, Integer>> countIterator = playerTerritoryCount.entrySet().iterator();
        while (countIterator.hasNext()) {
            if (countIterator.next().getValue() == 0) {
                countIterator.remove();
            }
        }

        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        playerLastSeen.entrySet().removeIf(entry ->
                entry.getValue() < thirtyDaysAgo &&
                        plugin.getTerritoryManager().getPlayerTerritoryCount(entry.getKey()) == 0
        );
        plugin.getDatabaseManager().cleanOldPlayerData(thirtyDaysAgo);
    }
}