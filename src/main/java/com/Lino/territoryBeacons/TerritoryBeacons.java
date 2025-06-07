package com.Lino.territoryBeacons;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private Map<UUID, Long> playerLastSeen = new HashMap<>();
    private Map<UUID, Location> pendingTerritoryCreation = new HashMap<>();
    private Map<UUID, Territory> pendingUpgrade = new HashMap<>();
    private Connection database;
    private TerritoryGUI territoryGUI;

    private int baseRadius = 16;
    private int radiusPerTier = 8;
    private int decayTime = 160;
    private int minimumBeaconDistance = 120;
    private int maxTerritoriesPerPlayer = 1;
    private boolean protectContainers = true;
    private boolean preventExplosions = true;
    private Map<Integer, Integer> tierRadii = new HashMap<>();
    private Map<String, Integer> upgradeCosts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        initDatabase();
        territoryGUI = new TerritoryGUI(this);
        getServer().getPluginManager().registerEvents(this, this);
        loadTerritories();
        loadPlayerData();
        startDecayTask();
        startSaveTask();
        startTerritoryCheckTask();
        getCommand("territory").setExecutor(new TerritoryCommand(this));
        getLogger().info("TerritoryBeacons enabled!");
    }

    @Override
    public void onDisable() {
        saveTerritories();
        savePlayerData();
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

            stmt.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "last_seen INTEGER NOT NULL" +
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

        for (int i = 1; i <= 6; i++) {
            int radius = config.getInt("tiers.tier-" + i + ".radius", baseRadius + (i * radiusPerTier));
            tierRadii.put(i, radius);

            if (i > 1) {
                int cost = config.getInt("tiers.tier-" + i + ".upgrade-cost", i * 10);
                upgradeCosts.put((i-1) + "-" + i, cost);
            }
        }
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

        player.sendMessage(ChatColor.YELLOW + "Beacon placed! Right-click to create the territory.");
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

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
            }
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
        event.setCancelled(true);

        Location loc = event.getClickedBlock().getLocation();
        Territory territory = territories.get(loc);
        Player player = event.getPlayer();

        if (territory != null) {
            territoryGUI.openTerritoryInfoGUI(player, territory, loc);
        } else {
            Block block = loc.getBlock();
            if (block.getState() instanceof Beacon) {
                Beacon beacon = (Beacon) block.getState();

                if (beacon.getTier() > 0 && beacon.getPrimaryEffect() != null) {
                    territoryGUI.openCreationGUI(player, loc);
                } else {
                    player.sendMessage(ChatColor.RED + "The beacon must be activated with a pyramid and have an active effect!");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.equals(ChatColor.DARK_GREEN + "Create Territory")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;

            if (event.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
                Location beaconLoc = findNearbyBeacon(player);
                if (beaconLoc != null && !territories.containsKey(beaconLoc)) {
                    player.closeInventory();
                    checkAndCreateTerritory(beaconLoc, player);
                }
            } else if (event.getCurrentItem().getType() == Material.BARRIER) {
                player.closeInventory();
            }
        } else if (title.equals(ChatColor.DARK_GREEN + "Territory Management")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;

            if (event.getCurrentItem().getType() == Material.DIAMOND) {
                Location beaconLoc = findNearbyBeacon(player);
                Territory territory = territories.get(beaconLoc);
                if (territory != null && territory.getOwnerUUID().equals(player.getUniqueId())) {
                    territoryGUI.openUpgradeGUI(player, territory, beaconLoc);
                }
            } else if (event.getCurrentItem().getType() == Material.BARRIER) {
                player.closeInventory();
            }
        } else if (title.equals(ChatColor.DARK_PURPLE + "Upgrade Territory")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;

            if (event.getCurrentItem().getType() == Material.YELLOW_STAINED_GLASS_PANE) {
                String itemName = event.getCurrentItem().getItemMeta().getDisplayName();
                if (itemName.contains("Available")) {
                    int targetTier = Character.getNumericValue(itemName.charAt(itemName.indexOf("Tier ") + 5));
                    Location beaconLoc = findNearbyBeacon(player);
                    Territory territory = territories.get(beaconLoc);

                    if (territory != null && territory.getOwnerUUID().equals(player.getUniqueId())) {
                        performUpgrade(player, territory, targetTier);
                    }
                }
            } else if (event.getCurrentItem().getType() == Material.ARROW) {
                Location beaconLoc = findNearbyBeacon(player);
                Territory territory = territories.get(beaconLoc);
                if (territory != null) {
                    territoryGUI.openTerritoryInfoGUI(player, territory, beaconLoc);
                }
            }
        }
    }

    private Location findNearbyBeacon(Player player) {
        Location playerLoc = player.getLocation();
        for (Location beaconLoc : territories.keySet()) {
            if (beaconLoc.distance(playerLoc) < 10) {
                return beaconLoc;
            }
        }

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    Block block = playerLoc.getBlock().getRelative(x, y, z);
                    if (block.getType() == Material.BEACON) {
                        return block.getLocation();
                    }
                }
            }
        }
        return null;
    }

    private void performUpgrade(Player player, Territory territory, int targetTier) {
        if (targetTier <= territory.getTier()) {
            player.sendMessage(ChatColor.RED + "You already have this tier or higher!");
            return;
        }

        if (targetTier != territory.getTier() + 1) {
            player.sendMessage(ChatColor.RED + "You must upgrade tiers in order!");
            return;
        }

        int cost = getUpgradeCost(territory.getTier(), targetTier);

        if (!player.getInventory().contains(Material.DIAMOND, cost)) {
            player.sendMessage(ChatColor.RED + "You need " + cost + " diamonds to upgrade!");
            player.closeInventory();
            return;
        }

        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, cost));

        int newRadius = getRadiusForTier(targetTier);
        Location beaconLoc = territory.getBeaconLocation();

        removeTerritoryBorder(territory);
        territories.remove(beaconLoc);

        Territory newTerritory = new Territory(territory.getOwnerUUID(), territory.getOwnerName(),
                beaconLoc, newRadius, targetTier);
        newTerritory.setInfluence(territory.getInfluence());

        for (UUID trusted : territory.getTrustedPlayers()) {
            newTerritory.addTrustedPlayer(trusted);
        }

        territories.put(beaconLoc, newTerritory);
        createTerritoryBorder(beaconLoc, newTerritory);
        updateTerritoryInDatabase(newTerritory);

        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Territory upgraded to tier " + targetTier + "!");
        player.sendMessage(ChatColor.AQUA + "New radius: " + newRadius + " blocks");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
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

        int totalRadius = getRadiusForTier(tier);

        Territory territory = new Territory(owner.getUniqueId(), owner.getName(),
                loc, totalRadius, tier);
        territories.put(loc, territory);
        updatePlayerTerritoryCount(owner.getUniqueId());
        saveTerritoryToDatabase(territory);

        owner.sendMessage(ChatColor.GREEN + "Territory created successfully!");
        owner.sendMessage(ChatColor.AQUA + "Radius: " + totalRadius + " blocks");
        owner.sendMessage(ChatColor.AQUA + "Beacon level: " + tier);

        Bukkit.broadcastMessage(ChatColor.YELLOW + owner.getName() + " has created a new territory!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
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
                        long lastSeen = getPlayerLastSeen(territory.getOwnerUUID());
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

                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f);
                                }
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
                savePlayerData();
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
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
        }
        else if (currentTerritory == null && previousTerritory != null) {
            playerCurrentTerritory.remove(playerUUID);
            player.sendTitle(ChatColor.GRAY + "Free Territory",
                    ChatColor.DARK_GRAY + "You have left the protected territory", 10, 30, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerLastSeen.put(player.getUniqueId(), System.currentTimeMillis());
        updatePlayerLastSeen(player.getUniqueId());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            checkPlayerTerritory(player);
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerCurrentTerritory.remove(player.getUniqueId());
        playerLastSeen.put(player.getUniqueId(), System.currentTimeMillis());
        updatePlayerLastSeen(player.getUniqueId());
    }

    public long getPlayerLastSeen(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            return System.currentTimeMillis();
        }
        return playerLastSeen.getOrDefault(playerUUID, System.currentTimeMillis());
    }

    private void loadPlayerData() {
        try {
            PreparedStatement stmt = database.prepareStatement(
                    "SELECT * FROM player_data"
            );

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                long lastSeen = rs.getLong("last_seen");
                playerLastSeen.put(playerUUID, lastSeen);
            }

            rs.close();
            stmt.close();

        } catch (SQLException e) {
            getLogger().severe("Error loading player data: " + e.getMessage());
        }
    }

    private void savePlayerData() {
        try {
            for (Map.Entry<UUID, Long> entry : playerLastSeen.entrySet()) {
                updatePlayerLastSeen(entry.getKey());
            }
        } catch (Exception e) {
            getLogger().severe("Error saving player data: " + e.getMessage());
        }
    }

    private void updatePlayerLastSeen(UUID playerUUID) {
        try {
            PreparedStatement stmt = database.prepareStatement(
                    "INSERT OR REPLACE INTO player_data (player_uuid, last_seen) VALUES (?, ?)"
            );

            stmt.setString(1, playerUUID.toString());
            stmt.setLong(2, playerLastSeen.getOrDefault(playerUUID, System.currentTimeMillis()));

            stmt.executeUpdate();
            stmt.close();

        } catch (SQLException e) {
            getLogger().severe("Error updating player last seen: " + e.getMessage());
        }
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
                    "UPDATE territories SET influence = ?, radius = ?, tier = ? WHERE owner_uuid = ? AND world = ? AND x = ? AND y = ? AND z = ?"
            );

            stmt.setDouble(1, territory.getInfluence());
            stmt.setInt(2, territory.getRadius());
            stmt.setInt(3, territory.getTier());
            stmt.setString(4, territory.getOwnerUUID().toString());

            Location loc = territory.getBeaconLocation();
            stmt.setString(5, loc.getWorld().getName());
            stmt.setInt(6, loc.getBlockX());
            stmt.setInt(7, loc.getBlockY());
            stmt.setInt(8, loc.getBlockZ());

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

    public int getBaseRadius() {
        return baseRadius;
    }

    public int getRadiusPerTier() {
        return radiusPerTier;
    }

    public int getDecayTime() {
        return decayTime;
    }

    public int getRadiusForTier(int tier) {
        return tierRadii.getOrDefault(tier, baseRadius + (tier * radiusPerTier));
    }

    public int getUpgradeCost(int fromTier, int toTier) {
        return upgradeCosts.getOrDefault(fromTier + "-" + toTier, toTier * 10);
    }
}