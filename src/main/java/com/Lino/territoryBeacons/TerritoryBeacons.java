package com.Lino.territoryBeacons;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Beacon;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.ChatColor;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TerritoryBeacons extends JavaPlugin implements Listener {

    private Map<Location, Territory> territories = new ConcurrentHashMap<>();
    private Map<UUID, Territory> playerCurrentTerritory = new HashMap<>();
    private Map<UUID, Integer> playerTerritoryCount = new HashMap<>();
    private Connection database;

    private int baseRadius = 16;
    private int radiusPerTier = 8;
    private int decayTime = 160;
    private int minimumBeaconDistance = 120;
    private int maxTerritoriesPerPlayer = 1;
    private boolean protectContainers = true;
    private boolean preventExplosions = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        initDatabase();
        getServer().getPluginManager().registerEvents(this, this);
        loadTerritories();
        startDecayTask();
        startSaveTask();
        startTerritoryCheckTask();
        getCommand("territory").setExecutor(new TerritoryCommand(this));
        getLogger().info("TerritoryBeacons enabled!");
    }

    @Override
    public void onDisable() {
        saveTerritories();
        try {
            if (database != null && !database.isClosed()) {
                database.close();
            }
        } catch (SQLException e) {
            getLogger().severe("Error closing database: " + e.getMessage());
        }
        getLogger().info("TerritoryBeacons disabled!");
    }

    private void initDatabase() {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "territories.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            database = DriverManager.getConnection(url);

            Statement stmt = database.createStatement();

            stmt.execute("CREATE TABLE IF NOT EXISTS territories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_uuid TEXT NOT NULL," +
                    "owner_name TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "y INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "radius INTEGER NOT NULL," +
                    "tier INTEGER NOT NULL," +
                    "influence REAL NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "UNIQUE(world, x, y, z)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS trusted_players (" +
                    "territory_id INTEGER NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "FOREIGN KEY(territory_id) REFERENCES territories(id) ON DELETE CASCADE," +
                    "PRIMARY KEY(territory_id, player_uuid)" +
                    ")");

            stmt.execute("PRAGMA foreign_keys = ON");

            stmt.close();

        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void loadConfigValues() {
        FileConfiguration config = getConfig();
        baseRadius = config.getInt("base-radius", 16);
        radiusPerTier = config.getInt("radius-per-tier", 8);
        decayTime = config.getInt("decay-time-hours", 72);
        minimumBeaconDistance = config.getInt("advanced.minimum-beacon-distance", 32);
        maxTerritoriesPerPlayer = config.getInt("advanced.max-territories-per-player", 1);
        protectContainers = config.getBoolean("advanced.protect-containers", true);
        preventExplosions = config.getBoolean("advanced.prevent-explosions", true);
    }

    @EventHandler
    public void onBeaconPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        int currentTerritories = getPlayerTerritoryCount(player.getUniqueId());
        if (currentTerritories >= maxTerritoriesPerPlayer) {
            player.sendMessage(ChatColor.RED + "You have reached the maximum number of territories (" +
                    maxTerritoriesPerPlayer + ")!");
            event.setCancelled(true);
            return;
        }

        for (Territory territory : territories.values()) {
            Location beaconLoc = territory.getBeaconLocation();
            if (beaconLoc.getWorld().equals(loc.getWorld())) {
                double distance = beaconLoc.distance(loc);
                if (distance < minimumBeaconDistance) {
                    player.sendMessage(ChatColor.RED + "This location is too close to another beacon! " +
                            "Minimum distance: " + minimumBeaconDistance + " blocks");
                    event.setCancelled(true);
                    return;
                }
            }
        }

        for (Territory territory : territories.values()) {
            if (territory.overlaps(loc)) {
                player.sendMessage(ChatColor.RED + "This location is too close to another territory!");
                event.setCancelled(true);
                return;
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Beacon placed! Activate the beacon with an effect to create the territory.");

        new BukkitRunnable() {
            int attempts = 0;

            @Override
            public void run() {
                if (attempts >= 60) {
                    cancel();
                    return;
                }

                if (loc.getBlock().getType() != Material.BEACON) {
                    cancel();
                    return;
                }

                Block block = loc.getBlock();
                if (block.getState() instanceof Beacon) {
                    Beacon beacon = (Beacon) block.getState();

                    if (beacon.getTier() > 0 && beacon.getPrimaryEffect() != null) {
                        checkAndCreateTerritory(loc, player);
                        cancel();
                        return;
                    }
                }

                attempts++;
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @EventHandler
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Location loc = event.getBlock().getLocation();
        Territory territory = territories.get(loc);

        if (territory != null) {
            Player breaker = event.getPlayer();

            if (!territory.getOwnerUUID().equals(breaker.getUniqueId()) &&
                    !breaker.hasPermission("territory.admin")) {

                breaker.sendMessage(ChatColor.RED + "You cannot destroy this beacon! Only the owner can do it.");
                event.setCancelled(true);
                return;
            }

            removeTerritoryBorder(territory);
            removeTerritoryFromDatabase(territory);
            territories.remove(loc);
            updatePlayerTerritoryCount(territory.getOwnerUUID());

            Bukkit.broadcastMessage(ChatColor.YELLOW + "The territory of " +
                    territory.getOwnerName() + " has been destroyed!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location blockLoc = block.getLocation();

        if (block.getType() == Material.TORCH) {
            for (Territory territory : territories.values()) {
                if (territory.getBorderBlocks().contains(blockLoc)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot break the territory boundaries!");
                    return;
                }
            }
        }

        if (block.getType() == Material.BEACON) {
            return;
        }

        Territory territory = getTerritoryAt(blockLoc);
        if (territory != null && !territory.canBuild(player)) {
            player.sendMessage(ChatColor.RED + "You cannot destroy in the territory of " +
                    territory.getOwnerName() + "!");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (block.getType() == Material.BEACON) {
            return;
        }

        Territory territory = getTerritoryAt(block.getLocation());
        if (territory != null && !territory.canBuild(player)) {
            player.sendMessage(ChatColor.RED + "You cannot build in the territory of " +
                    territory.getOwnerName() + "!");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.BEACON) {
            onBeaconInteract(event);
            return;
        }

        if (protectContainers && block.getState() instanceof Container) {
            Territory territory = getTerritoryAt(block.getLocation());

            if (territory != null && !territory.canBuild(player)) {
                player.sendMessage(ChatColor.RED + "You cannot access containers in " +
                        territory.getOwnerName() + "'s territory!");
                event.setCancelled(true);
            }
        }
    }

    public void onBeaconInteract(PlayerInteractEvent event) {
        Location loc = event.getClickedBlock().getLocation();
        Territory territory = territories.get(loc);

        if (territory != null) {
            Player player = event.getPlayer();
            player.sendMessage(ChatColor.GREEN + "=== Territory Information ===");
            player.sendMessage(ChatColor.AQUA + "Owner: " + ChatColor.WHITE + territory.getOwnerName());
            player.sendMessage(ChatColor.AQUA + "Radius: " + ChatColor.WHITE + territory.getRadius() + " blocks");
            player.sendMessage(ChatColor.AQUA + "Level: " + ChatColor.WHITE + territory.getTier());
            player.sendMessage(ChatColor.AQUA + "Influence: " + ChatColor.WHITE +
                    String.format("%.1f%%", territory.getInfluence() * 100));
        } else {
            Player player = event.getPlayer();

            Bukkit.getScheduler().runTaskLater(this, () -> {
                Block block = loc.getBlock();
                if (block.getType() == Material.BEACON && block.getState() instanceof Beacon) {
                    Beacon beacon = (Beacon) block.getState();

                    if (beacon.getTier() > 0 && beacon.getPrimaryEffect() != null && !territories.containsKey(loc)) {
                        player.sendMessage(ChatColor.GREEN + "Beacon activated! Creating territory...");
                        checkAndCreateTerritory(loc, player);
                    }
                }
            }, 60L);
        }
    }

    @EventHandler
    public void onExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (!preventExplosions) return;

        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();
            Territory territory = getTerritoryAt(block.getLocation());

            if (territory != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onTNTPrime(org.bukkit.event.block.BlockPlaceEvent event) {
        if (!preventExplosions) return;
        if (event.getBlock().getType() != Material.TNT) return;

        Territory territory = getTerritoryAt(event.getBlock().getLocation());
        if (territory != null && !territory.canBuild(event.getPlayer())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place TNT in this territory!");
            event.setCancelled(true);
        }
    }

    private void checkAndCreateTerritory(Location loc, Player owner) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Beacon)) return;

        Beacon beacon = (Beacon) block.getState();
        int tier = beacon.getTier();

        if (tier == 0) {
            owner.sendMessage(ChatColor.RED + "The beacon must be activated with a pyramid!");
            return;
        }

        if (beacon.getPrimaryEffect() == null) {
            owner.sendMessage(ChatColor.RED + "The beacon must have an active effect! Insert an ingot and select a power.");
            return;
        }

        if (territories.containsKey(loc)) {
            owner.sendMessage(ChatColor.YELLOW + "This beacon is already a territory!");
            return;
        }

        int currentTerritories = getPlayerTerritoryCount(owner.getUniqueId());
        if (currentTerritories >= maxTerritoriesPerPlayer) {
            owner.sendMessage(ChatColor.RED + "You have reached the maximum number of territories (" +
                    maxTerritoriesPerPlayer + ")!");
            return;
        }

        for (Territory territory : territories.values()) {
            Location beaconLoc = territory.getBeaconLocation();
            if (beaconLoc.getWorld().equals(loc.getWorld())) {
                double distance = beaconLoc.distance(loc);
                if (distance < minimumBeaconDistance) {
                    owner.sendMessage(ChatColor.RED + "This location is too close to another beacon! " +
                            "Minimum distance: " + minimumBeaconDistance + " blocks");
                    return;
                }
            }
        }

        int totalRadius = baseRadius + (tier * radiusPerTier);

        Territory territory = new Territory(owner.getUniqueId(), owner.getName(),
                loc, totalRadius, tier);
        territories.put(loc, territory);
        updatePlayerTerritoryCount(owner.getUniqueId());
        saveTerritoryToDatabase(territory);

        owner.sendMessage(ChatColor.GREEN + "Territory created successfully!");
        owner.sendMessage(ChatColor.AQUA + "Radius: " + totalRadius + " blocks");
        owner.sendMessage(ChatColor.AQUA + "Beacon level: " + tier);

        for (Player p : loc.getWorld().getPlayers()) {
            if (p != owner && p.getLocation().distance(loc) < 100) {
                p.sendMessage(ChatColor.YELLOW + owner.getName() + " has created a new territory!");
            }
        }

        spawnCreationEffect(loc);
        createTerritoryBorder(loc, territory);
    }

    private int getPlayerTerritoryCount(UUID playerUUID) {
        int count = 0;
        for (Territory territory : territories.values()) {
            if (territory.getOwnerUUID().equals(playerUUID)) {
                count++;
            }
        }
        return count;
    }

    private void updatePlayerTerritoryCount(UUID playerUUID) {
        playerTerritoryCount.put(playerUUID, getPlayerTerritoryCount(playerUUID));
    }

    private Territory getTerritoryAt(Location loc) {
        for (Territory territory : territories.values()) {
            if (territory.contains(loc)) {
                return territory;
            }
        }
        return null;
    }

    private void createTerritoryBorder(Location center, Territory territory) {
        World world = center.getWorld();
        int radius = territory.getRadius();

        removeTerritoryBorder(territory);

        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            double x = center.getX() + radius * Math.cos(rad);
            double z = center.getZ() + radius * Math.sin(rad);

            Location borderLoc = new Location(world, x, world.getMaxHeight() - 1, z);
            Block highestBlock = world.getHighestBlockAt(borderLoc);
            Location placeLoc = highestBlock.getLocation().add(0, 1, 0);

            Block targetBlock = placeLoc.getBlock();

            if (targetBlock.getType() == Material.AIR && isOnBorder(placeLoc, center, radius)) {
                Block blockBelow = targetBlock.getRelative(0, -1, 0);
                if (blockBelow.getType().isSolid()) {
                    targetBlock.setType(Material.TORCH);
                    territory.addBorderBlock(placeLoc);
                }
            }
        }
    }

    private boolean isOnBorder(Location loc, Location center, int radius) {
        double distance = Math.sqrt(
                Math.pow(loc.getX() - center.getX(), 2) +
                        Math.pow(loc.getZ() - center.getZ(), 2)
        );
        return Math.abs(distance - radius) < 1.5;
    }

    private void removeTerritoryBorder(Territory territory) {
        for (Location loc : territory.getBorderBlocks()) {
            Block block = loc.getBlock();
            if (block.getType() == Material.TORCH) {
                block.setType(Material.AIR);
            }
        }
    }

    private void spawnCreationEffect(Location loc) {
        World world = loc.getWorld();

        new BukkitRunnable() {
            double radius = 0;

            @Override
            public void run() {
                if (radius > 10) {
                    cancel();
                    return;
                }

                for (int angle = 0; angle < 360; angle += 10) {
                    double rad = Math.toRadians(angle);
                    double x = loc.getX() + 0.5 + radius * Math.cos(rad);
                    double z = loc.getZ() + 0.5 + radius * Math.sin(rad);

                    world.spawnParticle(Particle.END_ROD, x, loc.getY() + 1, z,
                            1, 0, 0.1, 0, 0.05);
                }

                radius += 0.5;
            }
        }.runTaskTimer(this, 0, 2);
    }

    private void startDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<Location, Territory>> iterator = territories.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<Location, Territory> entry = iterator.next();
                    Territory territory = entry.getValue();

                    Player owner = Bukkit.getPlayer(territory.getOwnerUUID());
                    if (owner == null || !owner.isOnline()) {
                        long lastSeen = getLastSeen(territory.getOwnerUUID());
                        long hoursOffline = (currentTime - lastSeen) / (1000 * 60 * 60);

                        if (hoursOffline >= decayTime) {
                            double decay = 0.1 * (hoursOffline - decayTime + 1);
                            territory.decayInfluence(decay);

                            if (territory.getInfluence() <= 0) {
                                removeTerritoryBorder(territory);
                                removeTerritoryFromDatabase(territory);
                                iterator.remove();
                                updatePlayerTerritoryCount(territory.getOwnerUUID());

                                Location beaconLoc = entry.getKey();
                                if (beaconLoc.getBlock().getType() == Material.BEACON) {
                                    beaconLoc.getBlock().setType(Material.AIR);
                                    beaconLoc.getWorld().dropItemNaturally(beaconLoc,
                                            new ItemStack(Material.BEACON));
                                }

                                Bukkit.broadcastMessage(ChatColor.RED +
                                        "The territory of " + territory.getOwnerName() +
                                        " has decayed due to inactivity!");
                            }
                        }
                    } else {
                        territory.restoreInfluence(0.05);
                    }
                }
            }
        }.runTaskTimer(this, 20 * 60 * 60, 20 * 60 * 60);
    }

    private void startSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveTerritories();
            }
        }.runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5);
    }

    private void startTerritoryCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerTerritory(player);
                }
            }
        }.runTaskTimer(this, 0, 10);
    }

    private void checkPlayerTerritory(Player player) {
        UUID playerUUID = player.getUniqueId();
        Location playerLoc = player.getLocation();

        Territory currentTerritory = getTerritoryAt(playerLoc);
        Territory previousTerritory = playerCurrentTerritory.get(playerUUID);

        if (currentTerritory != null && !currentTerritory.equals(previousTerritory)) {
            playerCurrentTerritory.put(playerUUID, currentTerritory);

            String title = ChatColor.AQUA + "Territory of";
            String subtitle = ChatColor.GOLD + currentTerritory.getOwnerName();

            if (currentTerritory.getOwnerUUID().equals(playerUUID)) {
                title = ChatColor.GREEN + "Your territory";
                subtitle = ChatColor.GRAY + "You are safe here";
            } else if (currentTerritory.isTrusted(playerUUID)) {
                subtitle = ChatColor.GREEN + currentTerritory.getOwnerName() + ChatColor.GRAY + " (Trusted)";
            }

            player.sendTitle(title, subtitle, 10, 40, 10);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
        }
        else if (currentTerritory == null && previousTerritory != null) {
            playerCurrentTerritory.remove(playerUUID);
            player.sendTitle(ChatColor.GRAY + "Free Territory",
                    ChatColor.DARK_GRAY + "You have left the protected territory", 10, 30, 10);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            checkPlayerTerritory(player);
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        playerCurrentTerritory.remove(event.getPlayer().getUniqueId());
    }

    private long getLastSeen(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            return System.currentTimeMillis();
        }
        return System.currentTimeMillis() - (decayTime * 60 * 60 * 1000);
    }

    private void loadTerritories() {
        try {
            territories.clear();
            playerTerritoryCount.clear();

            PreparedStatement stmt = database.prepareStatement(
                    "SELECT * FROM territories"
            );

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
                String ownerName = rs.getString("owner_name");
                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    getLogger().warning("World " + worldName + " not found, skipping territory");
                    continue;
                }

                Location loc = new Location(
                        world,
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                );

                int radius = rs.getInt("radius");
                int tier = rs.getInt("tier");
                double influence = rs.getDouble("influence");

                Territory territory = new Territory(ownerUUID, ownerName, loc, radius, tier);
                territory.setInfluence(influence);
                loadTrustedPlayers(territory, rs.getInt("id"));
                territories.put(loc, territory);
                createTerritoryBorder(loc, territory);
            }

            rs.close();
            stmt.close();

            for (Territory territory : territories.values()) {
                updatePlayerTerritoryCount(territory.getOwnerUUID());
            }

            getLogger().info("Loaded " + territories.size() + " territories from database");

        } catch (SQLException e) {
            getLogger().severe("Error loading territories: " + e.getMessage());
        }
    }

    private void loadTrustedPlayers(Territory territory, int territoryId) throws SQLException {
        PreparedStatement stmt = database.prepareStatement(
                "SELECT player_uuid FROM trusted_players WHERE territory_id = ?"
        );
        stmt.setInt(1, territoryId);

        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
            territory.addTrustedPlayer(playerUUID);
        }

        rs.close();
        stmt.close();
    }

    private void saveTerritories() {
        try {
            for (Map.Entry<Location, Territory> entry : territories.entrySet()) {
                Territory territory = entry.getValue();
                updateTerritoryInDatabase(territory);
            }
        } catch (Exception e) {
            getLogger().severe("Error saving territories: " + e.getMessage());
        }
    }

    private void saveTerritoryToDatabase(Territory territory) {
        try {
            PreparedStatement stmt = database.prepareStatement(
                    "INSERT INTO territories (owner_uuid, owner_name, world, x, y, z, radius, tier, influence, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            );

            Location loc = territory.getBeaconLocation();
            stmt.setString(1, territory.getOwnerUUID().toString());
            stmt.setString(2, territory.getOwnerName());
            stmt.setString(3, loc.getWorld().getName());
            stmt.setInt(4, loc.getBlockX());
            stmt.setInt(5, loc.getBlockY());
            stmt.setInt(6, loc.getBlockZ());
            stmt.setInt(7, territory.getRadius());
            stmt.setInt(8, territory.getTier());
            stmt.setDouble(9, territory.getInfluence());
            stmt.setLong(10, System.currentTimeMillis());

            stmt.executeUpdate();
            stmt.close();

        } catch (SQLException e) {
            getLogger().severe("Error saving territory to database: " + e.getMessage());
        }
    }

    private void updateTerritoryInDatabase(Territory territory) {
        try {
            PreparedStatement stmt = database.prepareStatement(
                    "UPDATE territories SET influence = ? WHERE owner_uuid = ? AND world = ? AND x = ? AND y = ? AND z = ?"
            );

            stmt.setDouble(1, territory.getInfluence());
            stmt.setString(2, territory.getOwnerUUID().toString());

            Location loc = territory.getBeaconLocation();
            stmt.setString(3, loc.getWorld().getName());
            stmt.setInt(4, loc.getBlockX());
            stmt.setInt(5, loc.getBlockY());
            stmt.setInt(6, loc.getBlockZ());

            stmt.executeUpdate();
            stmt.close();

            int territoryId = getTerritoryId(territory);
            if (territoryId != -1) {
                updateTrustedPlayers(territory, territoryId);
            }

        } catch (SQLException e) {
            getLogger().severe("Error updating territory in database: " + e.getMessage());
        }
    }

    private int getTerritoryId(Territory territory) {
        try {
            PreparedStatement stmt = database.prepareStatement(
                    "SELECT id FROM territories WHERE owner_uuid = ? AND world = ? AND x = ? AND y = ? AND z = ?"
            );

            stmt.setString(1, territory.getOwnerUUID().toString());
            Location loc = territory.getBeaconLocation();
            stmt.setString(2, loc.getWorld().getName());
            stmt.setInt(3, loc.getBlockX());
            stmt.setInt(4, loc.getBlockY());
            stmt.setInt(5, loc.getBlockZ());

            ResultSet rs = stmt.executeQuery();
            int id = -1;
            if (rs.next()) {
                id = rs.getInt("id");
            }

            rs.close();
            stmt.close();

            return id;

        } catch (SQLException e) {
            getLogger().severe("Error getting territory ID: " + e.getMessage());
            return -1;
        }
    }

    private void updateTrustedPlayers(Territory territory, int territoryId) throws SQLException {
        PreparedStatement deleteStmt = database.prepareStatement(
                "DELETE FROM trusted_players WHERE territory_id = ?"
        );
        deleteStmt.setInt(1, territoryId);
        deleteStmt.executeUpdate();
        deleteStmt.close();

        PreparedStatement insertStmt = database.prepareStatement(
                "INSERT INTO trusted_players (territory_id, player_uuid) VALUES (?, ?)"
        );

        for (UUID trustedUUID : territory.getTrustedPlayers()) {
            insertStmt.setInt(1, territoryId);
            insertStmt.setString(2, trustedUUID.toString());
            insertStmt.executeUpdate();
        }

        insertStmt.close();
    }

    private void removeTerritoryFromDatabase(Territory territory) {
        try {
            PreparedStatement stmt = database.prepareStatement(
                    "DELETE FROM territories WHERE owner_uuid = ? AND world = ? AND x = ? AND y = ? AND z = ?"
            );

            stmt.setString(1, territory.getOwnerUUID().toString());
            Location loc = territory.getBeaconLocation();
            stmt.setString(2, loc.getWorld().getName());
            stmt.setInt(3, loc.getBlockX());
            stmt.setInt(4, loc.getBlockY());
            stmt.setInt(5, loc.getBlockZ());

            stmt.executeUpdate();
            stmt.close();

        } catch (SQLException e) {
            getLogger().severe("Error removing territory from database: " + e.getMessage());
        }
    }

    public Map<Location, Territory> getTerritories() {
        return new HashMap<>(territories);
    }

    public Territory getTerritoryByOwner(UUID ownerUUID) {
        for (Territory territory : territories.values()) {
            if (territory.getOwnerUUID().equals(ownerUUID)) {
                return territory;
            }
        }
        return null;
    }

    public int getPlayerTerritoryCount(Player player) {
        return getPlayerTerritoryCount(player.getUniqueId());
    }

    public int getMaxTerritoriesPerPlayer() {
        return maxTerritoriesPerPlayer;
    }
}
