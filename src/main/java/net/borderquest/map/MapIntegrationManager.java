package net.borderquest.map;

import net.borderquest.BorderQuest;
import net.borderquest.Localization;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/**
 * Façade vers les intégrations de cartes dynamiques (BlueMap, Dynmap, JourneyMap, Xaero).
 * Chaque hook n'est instancié que si le mod correspondant est présent au runtime.
 * La lazy class-loading de Java garantit qu'aucune ClassNotFoundException ne survient.
 *
 * JourneyMap et Xaero fonctionnent uniquement en solo/LAN (même JVM client+serveur).
 */
public class MapIntegrationManager {

    private BlueMapHook    blueMapHook;
    private DynmapHook     dynmapHook;
    private JourneyMapHook journeyMapHook;
    private XaeroMapHook   xaeroMinimapHook;
    private XaeroMapHook   xaeroWorldMapHook;

    public MapIntegrationManager(MinecraftServer server) {
        if (FabricLoader.getInstance().isModLoaded("bluemap")) {
            try {
                blueMapHook = new BlueMapHook(server);
                blueMapHook.register();
                BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.integration", "BlueMap"));
            } catch (Exception e) {
                BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.integrationFailed", "BlueMap", e.getMessage()));
                blueMapHook = null;
            }
        }

        if (FabricLoader.getInstance().isModLoaded("dynmap")
                || FabricLoader.getInstance().isModLoaded("dynmap-fabric")) {
            try {
                dynmapHook = new DynmapHook(server);
                dynmapHook.register();
                BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.integration", "Dynmap"));
            } catch (Exception e) {
                BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.integrationFailed", "Dynmap", e.getMessage()));
                dynmapHook = null;
            }
        }

        if (FabricLoader.getInstance().isModLoaded("journeymap")) {
            try {
                journeyMapHook = new JourneyMapHook(server);
                journeyMapHook.register();
                BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.integration", "JourneyMap"));
            } catch (Exception e) {
                BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.integrationFailed", "JourneyMap", e.getMessage()));
                journeyMapHook = null;
            }
        }

        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
            try {
                xaeroMinimapHook = new XaeroMapHook(server, false);
                xaeroMinimapHook.register();
                BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.integration", "Xaero Minimap"));
            } catch (Exception e) {
                BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.integrationFailed", "Xaero Minimap", e.getMessage()));
                xaeroMinimapHook = null;
            }
        }

        if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
            try {
                xaeroWorldMapHook = new XaeroMapHook(server, true);
                xaeroWorldMapHook.register();
                BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.integration", "Xaero World Map"));
            } catch (Exception e) {
                BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.integrationFailed", "Xaero World Map", e.getMessage()));
                xaeroWorldMapHook = null;
            }
        }
    }

    public void updateBorder(double centerX, double centerZ, double radius) {
        if (blueMapHook != null) safely(() -> blueMapHook.updateBorder(centerX, centerZ, radius), "BlueMap updateBorder");
        if (dynmapHook  != null) safely(() -> dynmapHook .updateBorder(centerX, centerZ, radius), "Dynmap updateBorder");
        // JourneyMap et Xaero ne supportent pas les zones/frontières, uniquement les waypoints
    }

    public void addAltarMarker(BlockPos pos, String name) {
        if (blueMapHook       != null) safely(() -> blueMapHook      .addAltarMarker(pos, name), "BlueMap addAltarMarker");
        if (dynmapHook        != null) safely(() -> dynmapHook       .addAltarMarker(pos, name), "Dynmap addAltarMarker");
        if (journeyMapHook    != null) safely(() -> journeyMapHook   .addAltarMarker(pos, name), "JourneyMap addAltarMarker");
        if (xaeroMinimapHook  != null) safely(() -> xaeroMinimapHook .addAltarMarker(pos, name), "Xaero Minimap addAltarMarker");
        if (xaeroWorldMapHook != null) safely(() -> xaeroWorldMapHook.addAltarMarker(pos, name), "Xaero WorldMap addAltarMarker");
    }

    public void removeAltarMarker(BlockPos pos) {
        if (blueMapHook       != null) safely(() -> blueMapHook      .removeAltarMarker(pos), "BlueMap removeAltarMarker");
        if (dynmapHook        != null) safely(() -> dynmapHook       .removeAltarMarker(pos), "Dynmap removeAltarMarker");
        if (journeyMapHook    != null) safely(() -> journeyMapHook   .removeAltarMarker(pos), "JourneyMap removeAltarMarker");
        if (xaeroMinimapHook  != null) safely(() -> xaeroMinimapHook .removeAltarMarker(pos), "Xaero Minimap removeAltarMarker");
        if (xaeroWorldMapHook != null) safely(() -> xaeroWorldMapHook.removeAltarMarker(pos), "Xaero WorldMap removeAltarMarker");
    }

    private void safely(Runnable action, String context) {
        try { action.run(); }
        catch (Exception e) {
            BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.unexpectedIntegrationError", context, e.getMessage()));
        }
    }
}

