package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventHandler;
import net.pl3x.map.core.event.EventListener;
import net.pl3x.map.core.event.server.Pl3xMapEnabledEvent;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.util.Colors;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class Pl3xMapManager implements EventListener {

    private final TerritoryBeacons plugin;
    private static final String LAYER_KEY = "territorybeacons_territories";
    private boolean isMapEnabled = false;

    public Pl3xMapManager(TerritoryBeacons plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("Pl3xMap")) {
            Pl3xMap.api().getEventRegistry().register(this);
            if (Pl3xMap.api().isEnabled()) {
                isMapEnabled = true;
                loadTerritoriesOnAllMaps();
            }
        }
    }

    @EventHandler
    public void onPl3xMapEnabled(Pl3xMapEnabledEvent event) {
        isMapEnabled = true;
        loadTerritoriesOnAllMaps();
    }

    public void loadTerritoriesOnAllMaps() {
        if (!isMapEnabled) return;

        Collection<World> worlds = Pl3xMap.api().getWorldRegistry().values();
        if (worlds.isEmpty()) return;

        for (World mapWorld : worlds) {
            if (mapWorld.getLayerRegistry().has(LAYER_KEY)) {
                mapWorld.getLayerRegistry().unregister(LAYER_KEY);
            }
            SimpleLayer layer = new SimpleLayer(LAYER_KEY, () -> "Territories");
            layer.setDefaultHidden(false);
            layer.setUpdateInterval(30);
            layer.setPriority(10);
            layer.setZIndex(10);
            mapWorld.getLayerRegistry().register(layer);
        }

        plugin.getTerritoryManager().getAllTerritories().forEach(this::addOrUpdateTerritoryMarker);
    }

    public void addOrUpdateTerritoryMarker(Territory territory) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!isMapEnabled) return;
            World world = Pl3xMap.api().getWorldRegistry().get(territory.getBeaconLocation().getWorld().getName());
            if (world == null) return;

            SimpleLayer layer = (SimpleLayer) world.getLayerRegistry().get(LAYER_KEY);
            if (layer == null) return;

            String circleKey = "territory_circle_" + territory.getOwnerUUID();
            String iconKey = "territory_icon_" + territory.getOwnerUUID();
            layer.removeMarker(circleKey);
            layer.removeMarker(iconKey);

            StringBuilder tooltip = new StringBuilder();
            tooltip.append("<strong>").append(territory.getTerritoryName()).append("</strong><br/>");
            tooltip.append("Owner: ").append(territory.getOwnerName()).append("<br/>");
            tooltip.append("Tier: ").append(territory.getTier()).append("<br/>");
            tooltip.append("Radius: ").append(territory.getRadius()).append(" blocks<br/>");
            tooltip.append("Trusted: ").append(territory.getTrustedPlayers().size()).append(" players<br/>");
            tooltip.append("Influence: ").append(String.format("%.1f%%", territory.getInfluence() * 100));

            int strokeColor;
            int fillColor;

            Player owner = Bukkit.getPlayer(territory.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                tooltip.append("<br/><span style='color: #66FF66;'>Status: Active (Owner Online)</span>");
                strokeColor = Colors.fromHex("#00FFFF");
                fillColor = Colors.fromHex("#3300FFFF");
            } else {
                long decayStartMillis = TimeUnit.HOURS.toMillis(plugin.getConfigManager().getDecayTime());
                long offlineMillis = System.currentTimeMillis() - plugin.getPlayerManager().getPlayerLastSeen(territory.getOwnerUUID());
                if (offlineMillis < decayStartMillis) {
                    long remainingMillis = decayStartMillis - offlineMillis;
                    long hours = TimeUnit.MILLISECONDS.toHours(remainingMillis);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60;
                    tooltip.append("<br/><span style='color: #FFFF55;'>Status: Decay in ").append(hours).append("h ").append(minutes).append("m</span>");
                    strokeColor = Colors.fromHex("#FFFF55");
                    fillColor = Colors.fromHex("#33FFFF55");
                } else {
                    tooltip.append("<br/><span style='color: #FF5555;'>Status: Decaying!</span>");
                    strokeColor = Colors.fromHex("#FF5555");
                    fillColor = Colors.fromHex("#33FF5555");
                }
            }

            Options.Builder optionsBuilder = new Options.Builder()
                    .stroke(true)
                    .strokeColor(strokeColor)
                    .strokeWeight(2)
                    .fill(true)
                    .fillColor(fillColor)
                    .tooltipContent(tooltip.toString())
                    .tooltipDirection(Tooltip.Direction.TOP);

            Marker<?> circle = Marker.circle(circleKey, territory.getBeaconLocation().getBlockX(), territory.getBeaconLocation().getBlockZ(), territory.getRadius());
            circle.setOptions(optionsBuilder.build());
            layer.addMarker(circle);

            String headIconKey = "player_head_" + territory.getOwnerUUID();
            if (!Pl3xMap.api().getIconRegistry().has(headIconKey)) {
                try {
                    String headUrl = "https://cravatar.eu/helmavatar/" + territory.getOwnerUUID() + "/32.png";
                    BufferedImage image = ImageIO.read(new URL(headUrl));
                    Pl3xMap.api().getIconRegistry().register(new IconImage(headIconKey, image, "png"));
                } catch (Exception e) {
                }
            }

            if(Pl3xMap.api().getIconRegistry().has(headIconKey)) {
                Marker<?> icon = Marker.icon(iconKey, territory.getBeaconLocation().getBlockX(), territory.getBeaconLocation().getBlockZ(), headIconKey, 16);
                icon.setOptions(new Options.Builder().tooltipContent(tooltip.toString()).build());
                layer.addMarker(icon);
            }
        });
    }

    public void removeTerritoryMarker(Territory territory) {
        if (!isMapEnabled) return;
        World world = Pl3xMap.api().getWorldRegistry().get(territory.getBeaconLocation().getWorld().getName());
        if (world == null) return;

        SimpleLayer layer = (SimpleLayer) world.getLayerRegistry().get(LAYER_KEY);
        if (layer == null) return;

        layer.removeMarker("territory_circle_" + territory.getOwnerUUID());
        layer.removeMarker("territory_icon_" + territory.getOwnerUUID());
    }

    public void disable() {
        if (isMapEnabled) {
            Collection<World> worlds = Pl3xMap.api().getWorldRegistry().values();
            for (World mapWorld : worlds) {
                if (mapWorld.getLayerRegistry().has(LAYER_KEY)) {
                    mapWorld.getLayerRegistry().unregister(LAYER_KEY);
                }
            }
        }
    }
}