package net.borderquest.map;

import net.borderquest.BorderQuest;
import net.borderquest.Localization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Intégration Xaero's Minimap & World Map via réflexion pure.
 * Fonctionne en mode solo/LAN où client et serveur partagent la même JVM.
 * Sur un serveur dédié ces mods ne sont pas chargés — le hook est inactif.
 *
 * Cible Xaero's Minimap 24.x / World Map 1.39+ pour 1.21 :
 *   xaero.minimap.XaeroMinimap.instance
 *     .getModMain().getWaypointManager()
 *       .getWorld(dim) -> WaypointWorld
 *         .getCurrentSet()  -> WaypointSet
 *           .getList()       -> List<Waypoint>
 *   xaero.minimap.waypoints.Waypoint(x,y,z,name,initials,color,type,false)
 */
public class XaeroMapHook {

    /** Couleur orange pour les autels (RGB 0xFFAA00). */
    private static final int ALTAR_COLOR = 0; // index couleur Xaero (0 = blanc, 6 = orange)
    private static final String DIM_OVERWORLD = "minecraft/overworld";

    @SuppressWarnings("unused")
    private final MinecraftServer server;
    private final boolean useWorldMap; // true = World Map, false = Minimap

    public XaeroMapHook(MinecraftServer server, boolean useWorldMap) {
        this.server = server;
        this.useWorldMap = useWorldMap;
    }

    public void register() {
        if (useWorldMap) {
            BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.register", "Xaero World Map"));
        } else {
            BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.register", "Xaero Minimap"));
        }
    }

    // -----------------------------------------------------------------------

    public void addAltarMarker(BlockPos pos, String name) {
        try {
            Object wpm = getWaypointManager();
            if (wpm == null) return;

            Object world = getOrCreateWorld(wpm);
            if (world == null) return;

            Object set = getCurrentSet(world);
            if (set == null) return;

            List<Object> list = getWaypointList(set);
            if (list == null) return;

            // Supprimer un waypoint existant à la même position
            removeFromList(list, pos);

            String label    = (name == null || name.isBlank()) ? Localization.translate("borderquest.general.altar") : name;
            String initials = label.substring(0, Math.min(2, label.length())).toUpperCase();
            Object wp       = buildWaypoint(pos.getX(), pos.getY(), pos.getZ(), label, initials);
            if (wp == null) return;

            list.add(wp);
            saveWaypoints(wpm, world);

        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "XaeroMapHook", "addAltarMarker", e.getMessage()));
        }
    }

    public void removeAltarMarker(BlockPos pos) {
        try {
            Object wpm = getWaypointManager();
            if (wpm == null) return;

            Object world = getOrCreateWorld(wpm);
            if (world == null) return;

            Object set = getCurrentSet(world);
            if (set == null) return;

            List<Object> list = getWaypointList(set);
            if (list == null) return;

            boolean removed = removeFromList(list, pos);
            if (removed) saveWaypoints(wpm, world);

        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "XaeroMapHook", "removeAltarMarker", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Récupère le WaypointManager de Xaero (Minimap ou World Map).
     */
    private Object getWaypointManager() {
        try {
            String mainClass = useWorldMap
                    ? "xaero.worldmap.XaeroWorldMap"
                    : "xaero.minimap.XaeroMinimap";

            Class<?> clazz = Class.forName(mainClass);

            // Chercher le champ statique instance / INSTANCE
            Object instance = null;
            for (String fn : new String[]{"instance", "INSTANCE"}) {
                try {
                    Field f = clazz.getField(fn);
                    instance = f.get(null);
                    if (instance != null) break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (instance == null) return null;

            // getModMain() ou getModule()
            Object modMain = null;
            for (String mn : new String[]{"getModMain", "getModule", "getMain"}) {
                try {
                    modMain = clazz.getMethod(mn).invoke(instance);
                    if (modMain != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (modMain == null) return null;

            // getWaypointManager()
            for (String mn : new String[]{"getWaypointManager", "getWaypoints"}) {
                try {
                    Object wpm = modMain.getClass().getMethod(mn).invoke(modMain);
                    if (wpm != null) return wpm;
                } catch (NoSuchMethodException ignored) {}
            }
            return null;

        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "XaeroMapHook", "getWaypointManager", e.getMessage()));
            return null;
        }
    }

    /**
     * Récupère ou crée le WaypointWorld pour l'overworld.
     */
    private Object getOrCreateWorld(Object wpm) throws Exception {
        // Essayer getWorld(String) avec plusieurs noms de dimension
        for (String dim : new String[]{DIM_OVERWORLD, "minecraft:overworld", "overworld", "world"}) {
            try {
                Object w = wpm.getClass().getMethod("getWorld", String.class).invoke(wpm, dim);
                if (w != null) return w;
            } catch (NoSuchMethodException ignored) {}
        }
        // Essayer getCurrentWorld()
        try {
            Object w = wpm.getClass().getMethod("getCurrentWorld").invoke(wpm);
            if (w != null) return w;
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    /**
     * Récupère le WaypointSet courant du WaypointWorld.
     */
    private Object getCurrentSet(Object world) throws Exception {
        for (String mn : new String[]{"getCurrentSet", "getSelectedSet", "getMainSet"}) {
            try {
                Object s = world.getClass().getMethod(mn).invoke(world);
                if (s != null) return s;
            } catch (NoSuchMethodException ignored) {}
        }
        // Essayer getSets() et prendre le premier
        try {
            Object sets = world.getClass().getMethod("getSets").invoke(world);
            if (sets instanceof Iterable<?> it) {
                java.util.Iterator<?> iter = it.iterator();
                if (iter.hasNext()) return iter.next();
            }
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    /**
     * Récupère la liste de waypoints du WaypointSet.
     */
    @SuppressWarnings("unchecked")
    private List<Object> getWaypointList(Object set) throws Exception {
        for (String mn : new String[]{"getList", "getWaypoints", "waypoints"}) {
            try {
                Object list = set.getClass().getMethod(mn).invoke(set);
                if (list instanceof List) return (List<Object>) list;
            } catch (NoSuchMethodException ignored) {}
        }
        // Essayer en tant que champ public
        for (String fn : new String[]{"list", "waypoints"}) {
            try {
                Field f = set.getClass().getField(fn);
                Object val = f.get(set);
                if (val instanceof List) return (List<Object>) val;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /**
     * Crée un objet Waypoint Xaero avec plusieurs tentatives de constructeur.
     */
    private Object buildWaypoint(int x, int y, int z, String name, String initials) {
        try {
            Class<?> wpClass = Class.forName(useWorldMap
                    ? "xaero.worldmap.waypoints.Waypoint"
                    : "xaero.minimap.waypoints.Waypoint");

            // Tentative 1 : Waypoint(int x, int y, int z, String name, String initials,
            //                        int color, int icon, boolean disabled)
            try {
                return wpClass.getDeclaredConstructor(
                        int.class, int.class, int.class,
                        String.class, String.class,
                        int.class, int.class, boolean.class)
                        .newInstance(x, y, z, name, initials, ALTAR_COLOR, 0, false);
            } catch (NoSuchMethodException ignored) {}

            // Tentative 2 : Waypoint(String name, String initials,
            //                        int x, int y, int z, int color, int icon, boolean disabled)
            try {
                return wpClass.getDeclaredConstructor(
                        String.class, String.class,
                        int.class, int.class, int.class,
                        int.class, int.class, boolean.class)
                        .newInstance(name, initials, x, y, z, ALTAR_COLOR, 0, false);
            } catch (NoSuchMethodException ignored) {}

            // Tentative 3 : Waypoint(String name, int x, int y, int z, int color)
            try {
                return wpClass.getDeclaredConstructor(
                        String.class, int.class, int.class, int.class, int.class)
                        .newInstance(name, x, y, z, ALTAR_COLOR);
            } catch (NoSuchMethodException ignored) {}

            // Tentative 4 : chercher un constructeur qui prend (int,int,int,...) en premier
            for (java.lang.reflect.Constructor<?> c : wpClass.getDeclaredConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length >= 3 && pt[0] == int.class && pt[1] == int.class && pt[2] == int.class) {
                    try {
                        Object[] args = new Object[pt.length];
                        args[0] = x; args[1] = y; args[2] = z;
                        for (int i = 3; i < pt.length; i++) {
                            if (pt[i] == String.class)  args[i] = i == 3 ? name : initials;
                            else if (pt[i] == int.class)     args[i] = 0;
                            else if (pt[i] == boolean.class) args[i] = false;
                        }
                        c.setAccessible(true);
                        return c.newInstance(args);
                    } catch (Exception ignored) {}
                }
            }

            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "XaeroMapHook", "buildWaypoint", e.getMessage()));
            return null;
        }
    }

    /**
     * Supprime les waypoints à la position donnée et retourne true si au moins un a été supprimé.
     */
    private boolean removeFromList(List<Object> list, BlockPos pos) {
        boolean removed = false;
        for (Object wp : new java.util.ArrayList<>(list)) {
            try {
                int wx = getIntField(wp, new String[]{"x", "getX"}, pos.getX());
                int wz = getIntField(wp, new String[]{"z", "getZ"}, pos.getZ());
                if (wx == pos.getX() && wz == pos.getZ()) {
                    list.remove(wp);
                    removed = true;
                }
            } catch (Exception ignored) {}
        }
        return removed;
    }

    /**
     * Appelle saveWaypoints(world) ou save() sur le WaypointManager.
     */
    private void saveWaypoints(Object wpm, Object world) {
        try {
            try {
                wpm.getClass().getMethod("saveWaypoints", world.getClass()).invoke(wpm, world);
                return;
            } catch (NoSuchMethodException ignored) {}
            // Chercher saveWaypoints avec n'importe quel type
            for (Method m : wpm.getClass().getMethods()) {
                if (m.getName().equals("saveWaypoints") && m.getParameterCount() == 1) {
                    m.invoke(wpm, world);
                    return;
                }
            }
            // Fallback : save() sans argument
            try { wpm.getClass().getMethod("save").invoke(wpm); } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            BorderQuest.LOGGER.debug(Localization.translate("borderquest.logger.functionCall", "XaeroMapHook", "saveWaypoints", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Lit un champ ou appelle un getter pour obtenir un int, retourne defaultVal si échec.
     */
    private int getIntField(Object obj, String[] names, int defaultVal) {
        for (String n : names) {
            // Essayer comme getter
            try {
                Object val = obj.getClass().getMethod(n).invoke(obj);
                if (val instanceof Integer) return (Integer) val;
            } catch (Exception ignored) {}
            // Essayer comme champ public
            try {
                Field f = obj.getClass().getField(n);
                Object val = f.get(obj);
                if (val instanceof Integer) return (Integer) val;
            } catch (Exception ignored) {}
        }
        return defaultVal;
    }
}

