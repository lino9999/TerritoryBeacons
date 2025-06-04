package com.Lino.territoryBeacons;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Beacon;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TerritoryBeacons extends JavaPlugin implements Listener {

    private Map<Location, Territory> territories = new ConcurrentHashMap<>();
    private Map<UUID, Territory> playerCurrentTerritory = new HashMap<>();
    private FileConfiguration territoriesConfig;
    private File territoriesFile;

    // Configuration values
    private int baseRadius = 16;
    private int radiusPerTier = 8;
    private int decayTime = 72; // hours

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        loadConfigValues();

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
        getLogger().info("TerritoryBeacons disabled!");
    }

    public void loadConfigValues() {
        FileConfiguration config = getConfig();
        baseRadius = config.getInt("base-radius", 16);
        radiusPerTier = config.getInt("radius-per-tier", 8);
        decayTime = config.getInt("decay-time-hours", 72);
    }

    @EventHandler
    public void onBeaconPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        // Check if location overlaps with existing territory
        for (Territory territory : territories.values()) {
            if (territory.overlaps(loc)) {
                player.sendMessage(ChatColor.RED + "Questa posizione è troppo vicina a un altro territorio!");
                event.setCancelled(true);
                return;
            }
        }

        // Schedule beacon check after placement with longer delay
        player.sendMessage(ChatColor.YELLOW + "Beacon piazzato! Attiva il beacon con un effetto per creare il territorio.");

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

                breaker.sendMessage(ChatColor.RED + "Non puoi distruggere questo beacon! Solo il proprietario può farlo.");
                event.setCancelled(true);
                return;
            }

            // Remove border blocks
            removeTerritoryBorder(territory);

            // Remove territory
            territories.remove(loc);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Il territorio di " +
                    territory.getOwnerName() + " è stato distrutto!");
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
                    player.sendMessage(ChatColor.RED + "Non puoi rompere i confini del territorio!");
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
            player.sendMessage(ChatColor.RED + "Non puoi distruggere nel territorio di " +
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
            player.sendMessage(ChatColor.RED + "Non puoi costruire nel territorio di " +
                    territory.getOwnerName() + "!");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Removed PvP vulnerability system
    }

    @EventHandler
    public void onExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
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
        if (event.getBlock().getType() != Material.TNT) return;

        Territory territory = getTerritoryAt(event.getBlock().getLocation());
        if (territory != null && !territory.canBuild(event.getPlayer())) {
            event.getPlayer().sendMessage(ChatColor.RED + "Non puoi piazzare TNT in questo territorio!");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {

            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();
                Territory territory = getTerritoryAt(player.getLocation());

                if (territory != null) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.BEACON) return;

        Location loc = event.getClickedBlock().getLocation();
        Territory territory = territories.get(loc);

        if (territory != null) {
            Player player = event.getPlayer();
            player.sendMessage(ChatColor.GREEN + "=== Informazioni Territorio ===");
            player.sendMessage(ChatColor.AQUA + "Proprietario: " + ChatColor.WHITE + territory.getOwnerName());
            player.sendMessage(ChatColor.AQUA + "Raggio: " + ChatColor.WHITE + territory.getRadius() + " blocchi");
            player.sendMessage(ChatColor.AQUA + "Livello: " + ChatColor.WHITE + territory.getTier());
            player.sendMessage(ChatColor.AQUA + "Influenza: " + ChatColor.WHITE +
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
                        player.sendMessage(ChatColor.GREEN + "Beacon attivato! Creazione territorio...");
                        checkAndCreateTerritory(loc, player);
                    }
                }
            }, 60L); // 3 seconds delay to allow GUI interaction
        }
    }

    private void checkAndCreateTerritory(Location loc, Player owner) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Beacon)) return;

        Beacon beacon = (Beacon) block.getState();
        int tier = beacon.getTier();

        if (tier == 0) {
            owner.sendMessage(ChatColor.RED + "Il beacon deve essere attivato con una piramide!");
            return;
        }

        // Check if beacon has an active effect
        if (beacon.getPrimaryEffect() == null) {
            owner.sendMessage(ChatColor.RED + "Il beacon deve avere un effetto attivo! Inserisci un lingotto e seleziona un potere.");
            return;
        }

        // Check if already a territory
        if (territories.containsKey(loc)) {
            owner.sendMessage(ChatColor.YELLOW + "Questo beacon è già un territorio!");
            return;
        }

        // Calculate radius (no bonus)
        int totalRadius = baseRadius + (tier * radiusPerTier);

        // Create territory
        Territory territory = new Territory(owner.getUniqueId(), owner.getName(),
                loc, totalRadius, tier);
        territories.put(loc, territory);

        owner.sendMessage(ChatColor.GREEN + "Territorio creato con successo!");
        owner.sendMessage(ChatColor.AQUA + "Raggio: " + totalRadius + " blocchi");
        owner.sendMessage(ChatColor.AQUA + "Livello beacon: " + tier);

        // Broadcast to nearby players
        for (Player p : loc.getWorld().getPlayers()) {
            if (p != owner && p.getLocation().distance(loc) < 100) {
                p.sendMessage(ChatColor.YELLOW + owner.getName() + " ha creato un nuovo territorio!");
            }
        }

        // Visual effect
        spawnCreationEffect(loc);

        // Create border ONCE
        createTerritoryBorder(loc, territory);
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

                                // Remove territory
                                iterator.remove();
                                Location beaconLoc = entry.getKey();
                                if (beaconLoc.getBlock().getType() == Material.BEACON) {
                                    beaconLoc.getBlock().setType(Material.AIR);
                                    beaconLoc.getWorld().dropItemNaturally(beaconLoc,
                                            new ItemStack(Material.BEACON));
                                }

                                Bukkit.broadcastMessage(ChatColor.RED +
                                        "Il territorio di " + territory.getOwnerName() +
                                        " è decaduto per inattività!");
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
            String title = ChatColor.AQUA + "Territorio di";
            String subtitle = ChatColor.GOLD + currentTerritory.getOwnerName();

            // Different color if it's their own territory
            if (currentTerritory.getOwnerUUID().equals(playerUUID)) {
                title = ChatColor.GREEN + "Il tuo territorio";
                subtitle = ChatColor.GRAY + "Sei al sicuro qui";
            } else if (currentTerritory.isTrusted(playerUUID)) {
                subtitle = ChatColor.GREEN + currentTerritory.getOwnerName() + ChatColor.GRAY + " (Fidato)";
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
            player.sendTitle(ChatColor.GRAY + "Territorio Libero",
                    ChatColor.DARK_GRAY + "Hai lasciato il territorio protetto", 10, 30, 10);

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
        // This would need to track player login/logout times
        // For now, return current time if online, or a default offline time
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            return System.currentTimeMillis();
        }
        // In a real implementation, this would check a stored last logout time
        return System.currentTimeMillis() - (decayTime * 60 * 60 * 1000);
    }

    private void loadTerritories() {
        territoriesFile = new File(getDataFolder(), "territories.yml");
        if (!territoriesFile.exists()) {
            return;
        }

        territoriesConfig = YamlConfiguration.loadConfiguration(territoriesFile);

        for (String key : territoriesConfig.getKeys(false)) {
            String path = key + ".";
            UUID ownerUUID = UUID.fromString(territoriesConfig.getString(path + "owner-uuid"));
            String ownerName = territoriesConfig.getString(path + "owner-name");

            Location loc = new Location(
                    Bukkit.getWorld(territoriesConfig.getString(path + "world")),
                    territoriesConfig.getInt(path + "x"),
                    territoriesConfig.getInt(path + "y"),
                    territoriesConfig.getInt(path + "z")
            );

            int radius = territoriesConfig.getInt(path + "radius");
            int tier = territoriesConfig.getInt(path + "tier");
            double influence = territoriesConfig.getDouble(path + "influence", 1.0);

            Territory territory = new Territory(ownerUUID, ownerName, loc, radius, tier);
            territory.setInfluence(influence);
            territories.put(loc, territory);
        }

        getLogger().info("Loaded " + territories.size() + " territories");
    }

    private void saveTerritories() {
        territoriesFile = new File(getDataFolder(), "territories.yml");
        territoriesConfig = new YamlConfiguration();

        int index = 0;
        for (Map.Entry<Location, Territory> entry : territories.entrySet()) {
            Location loc = entry.getKey();
            Territory territory = entry.getValue();

            String path = "territory" + index + ".";
            territoriesConfig.set(path + "owner-uuid", territory.getOwnerUUID().toString());
            territoriesConfig.set(path + "owner-name", territory.getOwnerName());
            territoriesConfig.set(path + "world", loc.getWorld().getName());
            territoriesConfig.set(path + "x", loc.getBlockX());
            territoriesConfig.set(path + "y", loc.getBlockY());
            territoriesConfig.set(path + "z", loc.getBlockZ());
            territoriesConfig.set(path + "radius", territory.getRadius());
            territoriesConfig.set(path + "tier", territory.getTier());
            territoriesConfig.set(path + "influence", territory.getInfluence());

            index++;
        }

        try {
            territoriesConfig.save(territoriesFile);
        } catch (IOException e) {
            getLogger().severe("Could not save territories: " + e.getMessage());
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
}