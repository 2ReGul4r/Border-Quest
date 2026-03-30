package net.borderquest.map;

import net.borderquest.BorderQuest;
import net.borderquest.Localization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;

/**
 * Intégration Dynmap via réflexion pure (aucune dépendance à la compilation).
 * Compatible avec Dynmap 3.x si le mod est présent au runtime.
 * Aucun import Dynmap n'est nécessaire : toutes les classes sont chargées dynamiquement.
 */
public class DynmapHook {

    private static final String MARKER_SET_ID    = "borderquest";
    private static final String MARKER_SET_LABEL = "Border Quest";
    private static final String WORLD_NAME       = "world";

    @SuppressWarnings("unused")
    private final MinecraftServer server;

    public DynmapHook(MinecraftServer server) {
        this.server = server;
    }

    public void register() {
        BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.register", "Dynmap"));
    }

    public void updateBorder(double centerX, double centerZ, double radius) {
        try {
            Object mapi = getMarkerAPI();
            if (mapi == null) return;
            Object ms = getOrCreateMarkerSet(mapi);
            if (ms == null) return;

            Object existing = invoke1(ms, "findAreaMarker", String.class, "bq_border");
            if (existing != null) invoke0(existing, "deleteMarker");

            Object marker = invokeN(ms, "createAreaMarker",
                new Class[]{String.class, String.class, boolean.class, String.class,
                    double[].class, double[].class, boolean.class},
                "bq_border", "Frontiere Border Quest", false, WORLD_NAME,
                new double[]{centerX - radius, centerX + radius},
                new double[]{centerZ - radius, centerZ + radius},
                false);

            if (marker != null) {
                invokeN(marker, "setLineStyle",
                    new Class[]{int.class, double.class, int.class}, 2, 0.8, 0x00CC28);
                invokeN(marker, "setFillStyle",
                    new Class[]{double.class, int.class}, 0.1, 0x00CC28);
            }
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "Dynmap", "updateBorder", e.getMessage()));
        }
    }

    public void addAltarMarker(BlockPos pos, String name) {
        try {
            Object mapi = getMarkerAPI();
            if (mapi == null) return;
            Object ms = getOrCreateMarkerSet(mapi);
            if (ms == null) return;

            String id    = markerId(pos);
            String label = name.isBlank() ? "Autel" : name;

            Object ex = invoke1(ms, "findMarker", String.class, id);
            if (ex != null) invoke0(ex, "deleteMarker");

            Object icon = invoke1(mapi, "getMarkerIcon", String.class, "sign");

            // us.dynmap.markers.MarkerSet#createMarker
            Class<?> iconClass = Class.forName("us.dynmap.markers.MarkerIcon");
            invokeN(ms, "createMarker",
                new Class[]{String.class, String.class, String.class,
                    double.class, double.class, double.class, iconClass, boolean.class},
                id, label, WORLD_NAME,
                (double)(pos.getX()) + 0.5, (double) pos.getY(), (double)(pos.getZ()) + 0.5,
                icon, false);
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "Dynmap", "addAltarMarker", e.getMessage()));
        }
    }

    public void removeAltarMarker(BlockPos pos) {
        try {
            Object mapi = getMarkerAPI();
            if (mapi == null) return;
            Object ms = invoke1(mapi, "getMarkerSet", String.class, MARKER_SET_ID);
            if (ms == null) return;
            Object m = invoke1(ms, "findMarker", String.class, markerId(pos));
            if (m != null) invoke0(m, "deleteMarker");
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "Dynmap", "removeAltarMarker", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------

    private Object getMarkerAPI() {
        try {
            Class<?> apiClass = Class.forName("us.dynmap.DynmapCommonAPI");
            // Dynmap 3.x n'expose pas d'instance statique simple ; retour null si inaccessible.
            try {
                Method getInstance = apiClass.getMethod("getInstance");
                Object instance = getInstance.invoke(null);
                if (instance != null) return instance.getClass().getMethod("getMarkerAPI").invoke(instance);
            } catch (Exception ignored) {}
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "Dynmap", "getMarkerAPI", e.getMessage()));
            return null;
        }
    }

    private Object getOrCreateMarkerSet(Object mapi) throws Exception {
        Object ms = invoke1(mapi, "getMarkerSet", String.class, MARKER_SET_ID);
        if (ms == null) {
            ms = invokeN(mapi, "createMarkerSet",
                new Class[]{String.class, String.class, java.util.Set.class, boolean.class},
                MARKER_SET_ID, MARKER_SET_LABEL, null, false);
        }
        return ms;
    }

    private Object invoke0(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private Object invoke1(Object target, String method, Class<?> p, Object arg) throws Exception {
        return target.getClass().getMethod(method, p).invoke(target, arg);
    }

    private Object invokeN(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getMethod(method, types);
        return m.invoke(target, args);
    }

    private static String markerId(BlockPos pos) {
        return "altar_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }
}

