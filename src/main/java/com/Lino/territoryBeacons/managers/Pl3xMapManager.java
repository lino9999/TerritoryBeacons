package com.Lino.territoryBeacons.managers;

import com.Lino.territoryBeacons.Territory;
import com.Lino.territoryBeacons.TerritoryBeacons;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventHandler;
import net.pl3x.map.core.event.EventListener;
import net.pl3x.map.core.event.server.Pl3xMapEnabledEvent;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.util.Colors;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import java.util.Collection;

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

    private void loadTerritoriesOnAllMaps() {
        if (!isMapEnabled) {
            return;
        }

        Collection<World> worlds = Pl3xMap.api().getWorldRegistry().values();
        if (worlds.isEmpty()) {
            return;
        }

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

        plugin.getTerritoryManager().getAllTerritories().forEach(this::addTerritoryMarker);
    }

    public void addTerritoryMarker(Territory territory) {
        if (!isMapEnabled) return;
        World world = Pl3xMap.api().getWorldRegistry().get(territory.getBeaconLocation().getWorld().getName());
        if (world == null) return;

        SimpleLayer layer = (SimpleLayer) world.getLayerRegistry().get(LAYER_KEY);
        if (layer == null) return;

        String markerKey = "territory_" + territory.getBeaconLocation().getBlockX() + "_" + territory.getBeaconLocation().getBlockZ();
        layer.removeMarker(markerKey);

        Marker<?> marker = Marker.circle(markerKey,
                territory.getBeaconLocation().getBlockX(),
                territory.getBeaconLocation().getBlockZ(),
                territory.getRadius());

        String tooltipContent = "Owner: " + territory.getOwnerName() + "<br/>" + "Radius: " + territory.getRadius();

        Options options = new Options.Builder()
                .stroke(true)
                .strokeColor(Colors.fromHex("#00FFFF")) // Cyan
                .strokeWeight(2)
                .fill(true)
                .fillColor(Colors.fromHex("#3300FFFF")) // Cyan with alpha
                .tooltipContent(tooltipContent)
                .tooltipDirection(Tooltip.Direction.TOP)
                .build();

        marker.setOptions(options);
        layer.addMarker(marker);
    }

    public void removeTerritoryMarker(Territory territory) {
        if (!isMapEnabled) return;
        World world = Pl3xMap.api().getWorldRegistry().get(territory.getBeaconLocation().getWorld().getName());
        if (world == null) return;

        SimpleLayer layer = (SimpleLayer) world.getLayerRegistry().get(LAYER_KEY);
        if (layer == null) return;

        String markerKey = "territory_" + territory.getBeaconLocation().getBlockX() + "_" + territory.getBeaconLocation().getBlockZ();
        layer.removeMarker(markerKey);
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
        // A
    }
}