package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.TerritoryBeacons;
import com.Lino.territoryBeacons.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public class MessageManager {

    private final TerritoryBeacons plugin;
    private FileConfiguration messagesConfig;

    public MessageManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String get(String key, String... placeholderPairs) {
        String message = messagesConfig.getString(key, "<#FF0000>Message not found: " + key);

        for (int i = 0; i < placeholderPairs.length; i += 2) {
            if (i + 1 < placeholderPairs.length) {
                message = message.replace(placeholderPairs[i], placeholderPairs[i + 1]);
            }
        }
        return ColorUtil.format(message);
    }
}