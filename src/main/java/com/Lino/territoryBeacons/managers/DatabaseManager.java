package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final TerritoryBeacons plugin;
    private Connection database;

    public DatabaseManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
    }

    public void initDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "territories.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            database = DriverManager.getConnection(url);

            try (Statement stmt = database.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS territories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT NOT NULL, owner_name TEXT NOT NULL, " +
                        "territory_name TEXT, world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, " +
                        "radius INTEGER NOT NULL, tier INTEGER NOT NULL, influence REAL NOT NULL, " +
                        "created_at INTEGER NOT NULL, UNIQUE(world, x, y, z))");

                stmt.execute("CREATE TABLE IF NOT EXISTS trusted_players (" +
                        "territory_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, " +
                        "FOREIGN KEY(territory_id) REFERENCES territories(id) ON DELETE CASCADE, " +
                        "PRIMARY KEY(territory_id, player_uuid))");

                stmt.execute("CREATE TABLE IF NOT EXISTS territory_effects (" +
                        "territory_id INTEGER NOT NULL, effect_name TEXT NOT NULL, is_active INTEGER NOT NULL, " +
                        "FOREIGN KEY(territory_id) REFERENCES territories(id) ON DELETE CASCADE, " +
                        "PRIMARY KEY(territory_id, effect_name))");

                stmt.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                        "player_uuid TEXT PRIMARY KEY, last_seen INTEGER NOT NULL)");

                stmt.execute("PRAGMA foreign_keys = ON;");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    public void closeConnection() {
        try {
            if (database != null && !database.isClosed()) {
                database.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        }
    }

    public void loadTerritories(TerritoryManager territoryManager) {
        String sql = "SELECT * FROM territories";
        try (Statement stmt = database.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
                String ownerName = rs.getString("owner_name");
                String territoryName = rs.getString("territory_name");
                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World " + worldName + " not found, skipping territory.");
                    continue;
                }
                Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                int radius = rs.getInt("radius");
                int tier = rs.getInt("tier");
                double influence = rs.getDouble("influence");
                int id = rs.getInt("id");

                Territory territory = new Territory(ownerUUID, ownerName, loc, radius, tier);
                if (territoryName != null) {
                    territory.setTerritoryName(territoryName);
                }
                territory.setInfluence(influence);
                loadTrustedPlayers(territory, id);
                loadTerritoryEffects(territory, id);

                territoryManager.addTerritory(loc, territory);
                territoryManager.createTerritoryBorder(loc, territory);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading territories", e);
        }
    }

    private void loadTrustedPlayers(Territory territory, int territoryId) throws SQLException {
        String sql = "SELECT player_uuid FROM trusted_players WHERE territory_id = ?";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            stmt.setInt(1, territoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    territory.addTrustedPlayer(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        }
    }

    private void loadTerritoryEffects(Territory territory, int territoryId) throws SQLException {
        String sql = "SELECT effect_name, is_active FROM territory_effects WHERE territory_id = ?";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            stmt.setInt(1, territoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String effectName = rs.getString("effect_name");
                    boolean isActive = rs.getInt("is_active") == 1;
                    territory.unlockEffect(effectName);
                    if (isActive) {
                        territory.toggleEffect(effectName);
                    }
                }
            }
        }
    }

    public void loadPlayerData(PlayerManager playerManager) {
        String sql = "SELECT * FROM player_data";
        try (Statement stmt = database.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                long lastSeen = rs.getLong("last_seen");
                playerManager.updatePlayerLastSeen(playerUUID, lastSeen);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading player data", e);
        }
    }

    public void saveTerritoryToDatabase(Territory territory) {
        String sql = "INSERT INTO territories (owner_uuid, owner_name, territory_name, world, x, y, z, radius, tier, influence, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            Location loc = territory.getBeaconLocation();
            stmt.setString(1, territory.getOwnerUUID().toString());
            stmt.setString(2, territory.getOwnerName());
            stmt.setString(3, territory.getTerritoryName());
            stmt.setString(4, loc.getWorld().getName());
            stmt.setInt(5, loc.getBlockX());
            stmt.setInt(6, loc.getBlockY());
            stmt.setInt(7, loc.getBlockZ());
            stmt.setInt(8, territory.getRadius());
            stmt.setInt(9, territory.getTier());
            stmt.setDouble(10, territory.getInfluence());
            stmt.setLong(11, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving territory", e);
        }
    }

    public void updateTerritoryInDatabase(Territory territory) {
        String sql = "UPDATE territories SET influence = ?, radius = ?, tier = ?, territory_name = ? WHERE owner_uuid = ? AND world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            Location loc = territory.getBeaconLocation();
            stmt.setDouble(1, territory.getInfluence());
            stmt.setInt(2, territory.getRadius());
            stmt.setInt(3, territory.getTier());
            stmt.setString(4, territory.getTerritoryName());
            stmt.setString(5, territory.getOwnerUUID().toString());
            stmt.setString(6, loc.getWorld().getName());
            stmt.setInt(7, loc.getBlockX());
            stmt.setInt(8, loc.getBlockY());
            stmt.setInt(9, loc.getBlockZ());
            stmt.executeUpdate();

            int territoryId = getTerritoryId(territory);
            if (territoryId != -1) {
                updateTrustedPlayers(territory, territoryId);
                updateTerritoryEffects(territory, territoryId);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating territory", e);
        }
    }

    private int getTerritoryId(Territory territory) throws SQLException {
        String sql = "SELECT id FROM territories WHERE owner_uuid = ? AND world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            Location loc = territory.getBeaconLocation();
            stmt.setString(1, territory.getOwnerUUID().toString());
            stmt.setString(2, loc.getWorld().getName());
            stmt.setInt(3, loc.getBlockX());
            stmt.setInt(4, loc.getBlockY());
            stmt.setInt(5, loc.getBlockZ());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    private void updateTrustedPlayers(Territory territory, int territoryId) throws SQLException {
        String deleteSql = "DELETE FROM trusted_players WHERE territory_id = ?";
        try (PreparedStatement stmt = database.prepareStatement(deleteSql)) {
            stmt.setInt(1, territoryId);
            stmt.executeUpdate();
        }

        String insertSql = "INSERT INTO trusted_players (territory_id, player_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = database.prepareStatement(insertSql)) {
            for (UUID trustedUUID : territory.getTrustedPlayers()) {
                stmt.setInt(1, territoryId);
                stmt.setString(2, trustedUUID.toString());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void updateTerritoryEffects(Territory territory, int territoryId) throws SQLException {
        String deleteSql = "DELETE FROM territory_effects WHERE territory_id = ?";
        try (PreparedStatement stmt = database.prepareStatement(deleteSql)) {
            stmt.setInt(1, territoryId);
            stmt.executeUpdate();
        }

        String insertSql = "INSERT OR REPLACE INTO territory_effects (territory_id, effect_name, is_active) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = database.prepareStatement(insertSql)) {
            for (String effect : territory.getUnlockedEffects()) {
                stmt.setInt(1, territoryId);
                stmt.setString(2, effect);
                stmt.setInt(3, territory.hasEffect(effect) ? 1 : 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public void removeTerritoryFromDatabase(Territory territory) {
        String sql = "DELETE FROM territories WHERE owner_uuid = ? AND world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            Location loc = territory.getBeaconLocation();
            stmt.setString(1, territory.getOwnerUUID().toString());
            stmt.setString(2, loc.getWorld().getName());
            stmt.setInt(3, loc.getBlockX());
            stmt.setInt(4, loc.getBlockY());
            stmt.setInt(5, loc.getBlockZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing territory", e);
        }
    }

    public void saveAllPlayerData(Map<UUID, Long> playerLastSeenMap) {
        String sql = "INSERT OR REPLACE INTO player_data (player_uuid, last_seen) VALUES (?, ?)";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            for (Map.Entry<UUID, Long> entry : playerLastSeenMap.entrySet()) {
                stmt.setString(1, entry.getKey().toString());
                stmt.setLong(2, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving player data", e);
        }
    }

    public void cleanOldPlayerData(long timeThreshold) {
        String sql = "DELETE FROM player_data WHERE last_seen < ?";
        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            stmt.setLong(1, timeThreshold);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                plugin.getLogger().info("Cleaned up " + rows + " old player data entries.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cleaning old player data", e);
        }
    }
}