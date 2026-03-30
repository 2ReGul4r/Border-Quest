package net.borderquest.map;

import net.borderquest.BorderQuest;
import net.borderquest.Localization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Intégration JourneyMap via réflexion pure (aucune dépendance à la compilation).
 * Fonctionne en mode solo/LAN où client et serveur partagent la même JVM.
 * Sur un serveur dédié, JourneyMap n'est pas chargé — le hook est inactif.
 *
 * Cible JourneyMap 5.9+ / 1.21 :
 *   journeymap.client.waypoint.WaypointStore.INSTANCE  -> sauvegarde des waypoints
 *   journeymap.client.waypoint.Waypoint                -> modèle de waypoint
 */
public class JourneyMapHook {

    private static final String DIM_OVERWORLD = "minecraft:overworld";

    @SuppressWarnings("unused")
    private final MinecraftServer server;

    public JourneyMapHook(MinecraftServer server) {
        this.server = server;
    }

    public void register() {
        BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.register", "JourneyMap"));
    }

    // -----------------------------------------------------------------------

    public void addAltarMarker(BlockPos pos, String name) {
        try {
            Object store = getWaypointStore();
            if (store == null) return;

            String label = (name == null || name.isBlank()) ? Localization.translate("borderquest.general.altar") : name;

            // Supprimer un éventuel waypoint existant à cette position
            removeByPos(store, pos);

            // Créer et sauvegarder le waypoint
            Object wp = buildWaypoint(label, pos.getX(), pos.getY(), pos.getZ());
            if (wp == null) return;

            invoke1(store, "save", wp.getClass().getInterfaces().length > 0
                    ? wp.getClass().getInterfaces()[0] : wp.getClass(), wp);

        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall",
                    "JourneyMap", "addAltarMarker", e.getMessage()));
        }
    }

    public void removeAltarMarker(BlockPos pos) {
        try {
            Object store = getWaypointStore();
            if (store == null) return;
            removeByPos(store, pos);
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall",
                    "JourneyMap", "removeAltarMarker", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Tente de récupérer WaypointStore.INSTANCE via réflexion.
     */
    private Object getWaypointStore() {
        try {
            Class<?> storeClass = Class.forName("journeymap.client.waypoint.WaypointStore");
            // Chercher un champ statique INSTANCE ou getInstance()
            try {
                Field instanceField = storeClass.getField("INSTANCE");
                return instanceField.get(null);
            } catch (NoSuchFieldException ignored) {}
            try {
                Method m = storeClass.getMethod("getInstance");
                return m.invoke(null);
            } catch (NoSuchMethodException ignored) {}
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall",
                    "JourneyMap", "getWaypointStore", e.getMessage()));
            return null;
        }
    }

    /**
     * Crée un objet Waypoint JourneyMap avec plusieurs tentatives de constructeur.
     */
    private Object buildWaypoint(String name, int x, int y, int z) {
        try {
            Class<?> wpClass = Class.forName("journeymap.client.waypoint.Waypoint");

            // Tentative 1 : Waypoint(String name, int x, int y, int z, RegistryKey dim)
            try {
                Class<?> rkClass = Class.forName("net.minecraft.registry.RegistryKey");
                // On passe la dimension sous forme de String et on cherche via createWaypoint si disponible
                return wpClass.getDeclaredConstructor(String.class, int.class, int.class, int.class)
                        .newInstance(name, x, y, z);
            } catch (NoSuchMethodException ignored) {}

            // Tentative 2 : Waypoint(String name, String dim, int x, int y, int z)
            try {
                return wpClass.getDeclaredConstructor(String.class, String.class,
                        int.class, int.class, int.class)
                        .newInstance(name, DIM_OVERWORLD, x, y, z);
            } catch (NoSuchMethodException ignored) {}

            // Tentative 3 : Waypoint(String name, int x, int y, int z, int color,
            //               java.util.EnumSet type, String dim)
            try {
                Class<?> typeEnum = Class.forName("journeymap.client.waypoint.Waypoint$Type");
                Object allOf = typeEnum.getMethod("values").invoke(null);
                Object[] vals = (Object[]) allOf;
                java.util.EnumSet<?>  types = buildEnumSet(typeEnum, vals);
                return wpClass.getDeclaredConstructor(String.class, int.class, int.class, int.class,
                        int.class, java.util.EnumSet.class, String.class)
                        .newInstance(name, x, y, z, 0xFFAA00, types, DIM_OVERWORLD);
            } catch (NoSuchMethodException | ClassNotFoundException ignored) {}

            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall",
                    "JourneyMap", "buildWaypoint", e.getMessage()));
            return null;
        }
    }

    /**
     * Supprime les waypoints BorderQuest à la position donnée.
     */
    private void removeByPos(Object store, BlockPos pos) {
        try {
            // Essayer getAllWaypoints() ou getAll()
            java.util.Collection<?> all = null;
            try {
                all = (java.util.Collection<?>) store.getClass().getMethod("getAll").invoke(store);
            } catch (NoSuchMethodException ignored) {}
            if (all == null) {
                try {
                    all = (java.util.Collection<?>) store.getClass()
                            .getMethod("getAllWaypoints").invoke(store);
                } catch (NoSuchMethodException ignored) {}
            }
            if (all == null) return;

            for (Object wp : new java.util.ArrayList<>(all)) {
                try {
                    int wx = (int) wp.getClass().getMethod("getX").invoke(wp);
                    int wy = (int) wp.getClass().getMethod("getY").invoke(wp);
                    int wz = (int) wp.getClass().getMethod("getZ").invoke(wp);
                    if (wx == pos.getX() && wy == pos.getY() && wz == pos.getZ()) {
                        invoke1(store, "remove", wp.getClass(), wp);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall",
                    "JourneyMap", "removeByPos", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private java.util.EnumSet<?> buildEnumSet(Class<?> enumClass, Object[] vals) {
        java.util.EnumSet set = java.util.EnumSet.noneOf((Class<Enum>) enumClass);
        for (Object v : vals) set.add((Enum) v);
        return set;
    }

    private Object invoke1(Object target, String method, Class<?> paramType, Object arg)
            throws Exception {
        try {
            return target.getClass().getMethod(method, paramType).invoke(target, arg);
        } catch (NoSuchMethodException e) {
            // Chercher dans les super-classes / interfaces
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == 1) {
                    return m.invoke(target, arg);
                }
            }
            throw e;
        }
    }
}

