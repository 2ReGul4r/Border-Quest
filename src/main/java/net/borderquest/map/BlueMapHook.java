package net.borderquest.map;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.borderquest.BorderQuest;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Intégration BlueMap : affiche les autels (POI markers) et la frontière (ShapeMarker).
 * Nécessite BlueMap API 2.7.7+ (de.bluecolored:bluemap-api).
 */
public class BlueMapHook {

    private static final String MARKER_SET_ID = "borderquest";
    private static final String BORDER_MARKER_ID = "bq_border";

    private final MinecraftServer server;
    private volatile BlueMapAPI blueMapAPI;

    // Cache des données pour reconstruire les marqueurs après un rechargement BlueMap
    private final Map<String, double[]> altarCache  = new HashMap<>(); // key -> [x,y,z]
    private final Map<String, String>   altarNames  = new HashMap<>(); // key -> name
    private double cachedCX, cachedCZ, cachedRadius;

    public BlueMapHook(MinecraftServer server) {
        this.server = server;
    }

    public void register() {
        BlueMapAPI.onEnable(api -> {
            this.blueMapAPI = api;
            rebuildAllMarkers();
        });
        BlueMapAPI.onDisable(api -> {
            this.blueMapAPI = null;
        });
        BorderQuest.LOGGER.info(Text.translatable("borderquest.logger.register", "BlueMap").getString());
    }

    // -----------------------------------------------------------------------

    public void updateBorder(double centerX, double centerZ, double radius) {
        cachedCX     = centerX;
        cachedCZ     = centerZ;
        cachedRadius = radius;

        BlueMapAPI api = blueMapAPI;
        if (api == null) return;

        forEachOverworldMap(api, markerSet -> {
            Shape rect = Shape.createRect(
                centerX - radius, centerZ - radius,
                centerX + radius, centerZ + radius
            );
            ShapeMarker marker = ShapeMarker.builder()
                .label(Text.translatable("borderquest.general.border").getString())
                .shape(rect, 64f)
                .lineColor(new Color(0, 200, 40, 0.8f))
                .fillColor(new Color(0, 200, 40, 0.1f))
                .depthTestEnabled(false)
                .build();
            markerSet.put(BORDER_MARKER_ID, marker);
        });
    }

    public void addAltarMarker(BlockPos pos, String name) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        altarCache.put(key, new double[]{pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5});
        altarNames.put(key, name);

        BlueMapAPI api = blueMapAPI;
        if (api == null) return;

        forEachOverworldMap(api, markerSet -> {
            POIMarker marker = POIMarker.builder()
                .label(name.isBlank() ? Text.translatable("borderquest.general.altar").getString() : name)
                .position(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                .build();
            markerSet.put("altar_" + key, marker);
        });
    }

    public void removeAltarMarker(BlockPos pos) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        altarCache.remove(key);
        altarNames.remove(key);

        BlueMapAPI api = blueMapAPI;
        if (api == null) return;

        forEachOverworldMap(api, markerSet -> markerSet.remove("altar_" + key));
    }

    // -----------------------------------------------------------------------

    private void rebuildAllMarkers() {
        BlueMapAPI api = blueMapAPI;
        if (api == null) return;

        forEachOverworldMap(api, markerSet -> {
            markerSet.getMarkers().clear();
            // Recréer frontière
            if (cachedRadius > 0) {
                Shape rect = Shape.createRect(
                    cachedCX - cachedRadius, cachedCZ - cachedRadius,
                    cachedCX + cachedRadius, cachedCZ + cachedRadius
                );
                ShapeMarker borderMarker = ShapeMarker.builder()
                    .label(Text.translatable("borderquest.general.border").getString())
                    .shape(rect, 64f)
                    .lineColor(new Color(0, 200, 40, 0.8f))
                    .fillColor(new Color(0, 200, 40, 0.1f))
                    .depthTestEnabled(false)
                    .build();
                markerSet.put(BORDER_MARKER_ID, borderMarker);
            }
            // Recréer autels
            for (Map.Entry<String, double[]> e : altarCache.entrySet()) {
                double[] xyz  = e.getValue();
                String   name = altarNames.getOrDefault(e.getKey(), Text.translatable("borderquest.general.altar").getString());
                POIMarker poi = POIMarker.builder()
                    .label(name.isBlank() ? Text.translatable("borderquest.general.altar").getString() : name)
                    .position(xyz[0], xyz[1], xyz[2])
                    .build();
                markerSet.put("altar_" + e.getKey(), poi);
            }
        });
    }

    private void forEachOverworldMap(BlueMapAPI api, java.util.function.Consumer<MarkerSet> action) {
        try {
            Optional<de.bluecolored.bluemap.api.BlueMapWorld> worldOpt =
                api.getWorld(server.getOverworld().getRegistryKey().getValue().toString());
            worldOpt.ifPresent(world -> {
                Collection<BlueMapMap> maps = world.getMaps();
                for (BlueMapMap map : maps) {
                    MarkerSet ms = map.getMarkerSets().computeIfAbsent(
                        MARKER_SET_ID,
                        id -> MarkerSet.builder().label(Text.translatable("borderquest.general.borderQuest").getString()).build()
                    );
                    action.accept(ms);
                }
            });
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Text.translatable("borderquest.logger.functionCall", "BlueMap", "forEachOverworldMap", e.getMessage()).getString());
        }
    }
}
