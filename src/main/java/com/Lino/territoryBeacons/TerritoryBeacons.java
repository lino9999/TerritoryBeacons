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

    // Configuration values
    private int baseRadius = 16;
    private int radiusPerTier = 8;
    private int decayTime = 72; // hours
    private int minimumBeaconDistance = 32;
    private int maxTerritoriesPerPlayer = 1;
    private boolean protectContainers = true;
    private boolean preventExplosions = true;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        loadConfigValues();

        // Initialize database
        initDatabase();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Load territories
        loadTerritories();

        // Start tasks
        startDecayTask();
        startSaveTask();
        startTerritoryCheckTask();

        // Register commands
        getCommand("territory").setExecutor(new TerritoryCommand(this));

        getLogger().info("TerritoryBeacons enabled!");
    }

    @Override
    public void onDisable() {
        saveTerritories();

        // Close database connection
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

            // Create tables
            Statement stmt = database.createStatement();

            // Territories table
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

            // Trusted players table
            stmt.execute("CREATE TABLE IF NOT EXISTS trusted_players (" +
                    "territory_id INTEGER NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "FOREIGN KEY(territory_id) REFERENCES territories(id) ON DELETE CASCADE," +
                    "PRIMARY KEY(territory_id, player_uuid)" +
                    ")");

            // Enable foreign keys
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

        // Load advanced config
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

        // Check max territories per player
        int currentTerritories = getPlayerTerritoryCount(player.getUniqueId());
        if (currentTerritories >= maxTerritoriesPerPlayer) {
            player.sendMessage(ChatColor.RED + "You have reached the maximum number of territories (" +
                    maxTerritoriesPerPlayer + ")!");
            event.setCancelled(true);
            return;
        }

        // Check minimum distance from other beacons
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

        // Check if location overlaps with existing territory
        for (Territory territory : territories.values()) {
            if (territory.overlaps(loc)) {
                player.sendMessage(ChatColor.RED + "This location is too close to another territory!");
                event.setCancelled(true);
                return;
            }
        }

        // Schedule beacon check after placement
        player.sendMessage(ChatColor.YELLOW + "Beacon placed! Activate the beacon with an effect to create the territory.");

        // Start checking for activation
        new BukkitRunnable() {
            int attempts = 0;

            @Override
            public void run() {
                if (attempts >= 60) { // Stop after 1 minute
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

                    // Check if beacon is activated (has a primary effect)
                    if (beacon.getTier() > 0 && beacon.getPrimaryEffect() != null) {
                        checkAndCreateTerritory(loc, player);
                        cancel();
                        return;
                    }
                }

                attempts++;
            }
        }.runTaskTimer(this, 20L, 20L); // Check every second
    }

    @EventHandler
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Location loc = event.getBlock().getLocation();
        Territory territory = territories.get(loc);

        if (territory != null) {
            Player breaker = event.getPlayer();

            // Check ownership
            if (!territory.getOwnerUUID().equals(breaker.getUniqueId()) &&
                    !breaker.hasPermission("territory.admin")) {

                breaker.sendMessage(ChatColor.RED + "You cannot destroy this beacon! Only the owner can do it.");
                event.setCancelled(true);
                return;
            }

            // Remove border blocks
            removeTerritoryBorder(territory);

            // Remove from database
            removeTerritoryFromDatabase(territory);

            // Remove territory
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

        // Check if it's a border torch
        if (block.getType() == Material.TORCH) {
            // Check all territories to see if this is a border block
            for (Territory territory : territories.values()) {
                if (territory.getBorderBlocks().contains(blockLoc)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot break the territory boundaries!");
                    return;
                }
            }
        }

        // Special handling for beacons
        if (block.getType() == Material.BEACON) {
            // This is handled in onBeaconBreak
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

        // Special handling for beacons - don't check territory for beacon placement
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

        // Handle beacon interactions separately
        if (block.getType() == Material.BEACON) {
            onBeaconInteract(event);
            return;
        }

        // Protect containers if enabled
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
            // Not a territory yet, check if it can become one after this interaction
            Player player = event.getPlayer();

            // Schedule a check after the inventory closes
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Block block = loc.getBlock();
                if (block.getType() == Material.BEACON && block.getState() instanceof Beacon) {
                    Beacon beacon = (Beacon) block.getState();

                    if (beacon.getTier() > 0 && beacon.getPrimaryEffect() != null && !territories.containsKey(loc)) {
                        // Ask player if they want to create territory
                        player.sendMessage(ChatColor.GREEN + "Beacon activated! Creating territory...");
                        checkAndCreateTerritory(loc, player);
                    }
                }
            }, 60L); // 3 seconds delay to allow GUI interaction
        }
    }

    @EventHandler
    public void onExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (!preventExplosions) return;

        // Check if explosion is from TNT or other sources
        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();
            Territory territory = getTerritoryAt(block.getLocation());

            if (territory != null) {
                // Remove block from explosion list if in territory
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

        // Check if beacon has an active effect
        if (beacon.getPrimaryEffect() == null) {
            owner.sendMessage(ChatColor.RED + "The beacon must have an active effect! Insert an ingot and select a power.");
            return;
        }

        // Check if already a territory
        if (territories.containsKey(loc)) {
            owner.sendMessage(ChatColor.YELLOW + "This beacon is already a territory!");
            return;
        }

        // Check max territories again
        int currentTerritories = getPlayerTerritoryCount(owner.getUniqueId());
        if (currentTerritories >= maxTerritoriesPerPlayer) {
            owner.sendMessage(ChatColor.RED + "You have reached the maximum number of territories (" +
                    maxTerritoriesPerPlayer + ")!");
            return;
        }

        // Check minimum distance again
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

        // Calculate radius
        int totalRadius = baseRadius + (tier * radiusPerTier);

        // Create territory
        Territory territory = new Territory(owner.getUniqueId(), owner.getName(),
                loc, totalRadius, tier);
        territories.put(loc, territory);
        updatePlayerTerritoryCount(owner.getUniqueId());

        // Save to database
        saveTerritoryToDatabase(territory);

        owner.sendMessage(ChatColor.GREEN + "Territory created successfully!");
        owner.sendMessage(ChatColor.AQUA + "Radius: " + totalRadius + " blocks");
        owner.sendMessage(ChatColor.AQUA + "Beacon level: " + tier);

        // Broadcast to nearby players
        for (Player p : loc.getWorld().getPlayers()) {
            if (p != owner && p.getLocation().distance(loc) < 100) {
                p.sendMessage(ChatColor.YELLOW + owner.getName() + " has created a new territory!");
            }
        }

        // Visual effect
        spawnCreationEffect(loc);

        // Create border
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

        // Clear any existing border first
        removeTerritoryBorder(territory);

        // Place torches at border
        for (int angle = 0; angle < 360; angle += 10) { // Every 10 degrees for torches
            double rad = Math.toRadians(angle);
            double x = center.getX() + radius * Math.cos(rad);
            double z = center.getZ() + radius * Math.sin(rad);

            // Find the highest block at this location
            Location borderLoc = new Location(world, x, world.getMaxHeight() - 1, z);
            Block highestBlock = world.getHighestBlockAt(borderLoc);
            Location placeLoc = highestBlock.getLocation().add(0, 1, 0);

            // Check if we should place a torch
            Block targetBlock = placeLoc.getBlock();

            // Only place if air and on border
            if (targetBlock.getType() == Material.AIR && isOnBorder(placeLoc, center, radius)) {
                // Check if the block below can support a torch
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

        // Check if within 1 block of the exact radius
        return Math.abs(distance - radius) < 1.5;
    }

    private void removeTerritoryBorder(Territory territory) {
        // Remove all border blocks when territory is removed
        for (Location loc : territory.getBorderBlocks()) {
            Block block = loc.getBlock();
            if (block.getType() == Material.TORCH) {
                block.setType(Material.AIR);
            }
        }
    }

    private void spawnCreationEffect(Location loc) {
        World world = loc.getWorld();

        // Expanding ring effect
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
        // Run every hour
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<Location, Territory>> iterator = territories.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<Location, Territory> entry = iterator.next();
                    Territory territory = entry.getValue();

                    // Check if owner has been offline too long
                    Player owner = Bukkit.getPlayer(territory.getOwnerUUID());
                    if (owner == null || !owner.isOnline()) {
                        long lastSeen = getLastSeen(territory.getOwnerUUID());
                        long hoursOffline = (currentTime - lastSeen) / (1000 * 60 * 60);

                        if (hoursOffline >= decayTime) {
                            // Decay influence
                            double decay = 0.1 * (hoursOffline - decayTime + 1);
                            territory.decayInfluence(decay);

                            if (territory.getInfluence() <= 0) {
                                // Remove border blocks first
                                removeTerritoryBorder(territory);

                                // Remove from database
                                removeTerritoryFromDatabase(territory);

                                // Remove territory
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
                        // Owner is online, restore influence slowly
                        territory.restoreInfluence(0.05);
                    }
                }
            }
        }.runTaskTimer(this, 20 * 60 * 60, 20 * 60 * 60); // Every hour
    }

    private void startSaveTask() {
        // Auto-save every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                saveTerritories();
            }
        }.runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5);
    }

    private void startTerritoryCheckTask() {
        // Check player positions every 10 ticks (0.5 seconds)
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

        // Player entered a new territory
        if (currentTerritory != null && !currentTerritory.equals(previousTerritory)) {
            playerCurrentTerritory.put(playerUUID, currentTerritory);

            // Show title
            String title = ChatColor.AQUA + "Territory of";
            String subtitle = ChatColor.GOLD + currentTerritory.getOwnerName();

            // Different color if it's their own territory
            if (currentTerritory.getOwnerUUID().equals(playerUUID)) {
                title = ChatColor.GREEN + "Your territory";
                subtitle = ChatColor.GRAY + "You are safe here";
            } else if (currentTerritory.isTrusted(playerUUID)) {
                subtitle = ChatColor.GREEN + currentTerritory.getOwnerName() + ChatColor.GRAY + " (Trusted)";
            }

            player.sendTitle(title, subtitle, 10, 40, 10);

            // Play sound
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
        }
        // Player left a territory
        else if (currentTerritory == null && previousTerritory != null) {
            playerCurrentTerritory.remove(playerUUID);

            // Show exit title
            player.sendTitle(ChatColor.GRAY + "Free Territory",
                    ChatColor.DARK_GRAY + "You have left the protected territory", 10, 30, 10);

            // Play sound
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        // Check territory on join
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            checkPlayerTerritory(player);
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        // Clean up on quit
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

                // Load trusted players
                loadTrustedPlayers(territory, rs.getInt("id"));

                territories.put(loc, territory);

                // Recreate border
                createTerritoryBorder(loc, territory);
            }

            rs.close();
            stmt.close();

            // Update player territory counts
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
            // Update all territories
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

            // Get territory ID for trusted players update
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
        // Clear existing trusted players
        PreparedStatement deleteStmt = database.prepareStatement(
                "DELETE FROM trusted_players WHERE territory_id = ?"
        );
        deleteStmt.setInt(1, territoryId);
        deleteStmt.executeUpdate();
        deleteStmt.close();

        // Insert current trusted players
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

    // Getters for other classes
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