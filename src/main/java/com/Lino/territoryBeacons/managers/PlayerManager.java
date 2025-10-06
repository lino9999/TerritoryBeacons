package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final TerritoryBeacons plugin;
    private final MessageManager messageManager;
    private final Map<UUID, Territory> playerCurrentTerritory = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerTerritoryCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastSeen = new ConcurrentHashMap<>();

    public PlayerManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    public void loadPlayerData() {
        plugin.getDatabaseManager().loadPlayerData(this);
    }

    public void saveAndClearPlayerData() {
        plugin.getDatabaseManager().saveAllPlayerData(playerLastSeen);
        playerCurrentTerritory.clear();
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
        return playerTerritoryCount.computeIfAbsent(playerUUID, u -> plugin.getTerritoryManager().getPlayerTerritoryCount(u));
    }

    public void onPlayerJoin(Player player) {
        playerLastSeen.put(player.getUniqueId(), System.currentTimeMillis());
        updatePlayerTerritoryCount(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayerTerritory(player), 20L);
    }

    public void onPlayerQuit(Player player) {
        playerCurrentTerritory.remove(player.getUniqueId());
        playerLastSeen.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void checkPlayerTerritory(Player player) {
        if (!player.isOnline()) return;

        UUID playerUUID = player.getUniqueId();
        Territory currentTerritory = plugin.getTerritoryManager().getTerritoryAt(player.getLocation());
        Territory previousTerritory = playerCurrentTerritory.get(playerUUID);

        if (currentTerritory != null && !currentTerritory.equals(previousTerritory)) {
            playerCurrentTerritory.put(playerUUID, currentTerritory);
            String title;
            String subtitle;
            if (currentTerritory.getOwnerUUID().equals(playerUUID)) {
                title = messageManager.get("title-enter-own-territory");
                subtitle = messageManager.get("subtitle-enter-own-territory");
            } else {
                title = messageManager.get("title-enter-other-territory");
                subtitle = currentTerritory.isTrusted(playerUUID)
                        ? messageManager.get("subtitle-enter-other-territory-trusted", "%owner%", currentTerritory.getOwnerName())
                        : messageManager.get("subtitle-enter-other-territory-untrusted", "%owner%", currentTerritory.getOwnerName());
            }
            player.sendTitle(title, subtitle, 10, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
        } else if (currentTerritory == null && previousTerritory != null) {
            playerCurrentTerritory.remove(playerUUID);
            player.sendTitle(messageManager.get("title-wilderness"), messageManager.get("subtitle-wilderness"), 10, 30, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        }
    }

    public void cleanupUnusedData() {
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        playerLastSeen.entrySet().removeIf(entry ->
                entry.getValue() < thirtyDaysAgo &&
                        plugin.getTerritoryManager().getPlayerTerritoryCount(entry.getKey()) == 0
        );
        plugin.getDatabaseManager().cleanOldPlayerData(thirtyDaysAgo);
    }
}