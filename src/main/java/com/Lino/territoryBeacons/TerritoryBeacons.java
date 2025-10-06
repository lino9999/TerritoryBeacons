package com.Lino.territoryBeacons;

import com.Lino.territoryBeacons.commands.TerritoryCommand;
import com.Lino.territoryBeacons.gui.TerritoryGUI;
import com.Lino.territoryBeacons.listeners.TerritoryListener;
import com.Lino.territoryBeacons.managers.ConfigManager;
import com.Lino.territoryBeacons.managers.DatabaseManager;
import com.Lino.territoryBeacons.managers.PlayerManager;
import com.Lino.territoryBeacons.managers.TerritoryManager;
import com.Lino.territoryBeacons.managers.Pl3xMapManager;
import com.Lino.territoryBeacons.tasks.PluginTaskManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TerritoryBeacons extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerManager playerManager;
    private TerritoryManager territoryManager;
    private PluginTaskManager taskManager;
    private TerritoryGUI territoryGUI;
    private Pl3xMapManager pl3xMapManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.playerManager = new PlayerManager(this);
        this.territoryManager = new TerritoryManager(this);
        this.territoryGUI = new TerritoryGUI(this);

        databaseManager.initDatabase();
        territoryManager.loadTerritories();
        playerManager.loadPlayerData();

        this.taskManager = new PluginTaskManager(this);
        taskManager.startAllTasks();

        if (getServer().getPluginManager().isPluginEnabled("Pl3xMap")) {
            this.pl3xMapManager = new Pl3xMapManager(this);
        }

        getServer().getPluginManager().registerEvents(new TerritoryListener(this), this);
        getCommand("territory").setExecutor(new TerritoryCommand(this));
        getCommand("territory").setTabCompleter(new TerritoryCommand(this));

        getLogger().info("TerritoryBeacons has been enabled!");
    }

    @Override
    public void onDisable() {
        if (taskManager != null) {
            taskManager.cancelAllTasks();
        }
        if (territoryManager != null) {
            territoryManager.saveAndClearTerritories();
        }
        if (playerManager != null) {
            playerManager.saveAndClearPlayerData();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        if (pl3xMapManager != null) {
            pl3xMapManager.disable();
        }

        getLogger().info("TerritoryBeacons has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public TerritoryManager getTerritoryManager() {
        return territoryManager;
    }

    public TerritoryGUI getTerritoryGUI() {
        return territoryGUI;
    }

    public Pl3xMapManager getPl3xMapManager() {
        return pl3xMapManager;
    }
}