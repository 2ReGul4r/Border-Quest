package net.borderquest;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.*;

/**
 * Scanne les biomes dans le rayon de la barrière et résout les catégories
 * de ressources en items concrets disponibles localement.
 */
public class BiomeResourceResolver {

    /**
     * Mapping biome -> item pour chaque catégorie.
     * Clé : nom de catégorie (ex: "logs").
     * Valeur : liste ordonnée de (BiomeKey[], itemId).
     */
    private static final Map<String, List<BiomeMapping>> CATEGORY_MAPPINGS = new HashMap<>();

    /** Fallback par catégorie si aucun biome ne correspond. */
    private static final Map<String, String> CATEGORY_FALLBACKS = new HashMap<>();

    static {
        // ---- Catégorie "logs" ----
        List<BiomeMapping> logMappings = new ArrayList<>();

        logMappings.add(new BiomeMapping("minecraft:oak_log",
            BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS,
            BiomeKeys.FOREST, BiomeKeys.FLOWER_FOREST,
            BiomeKeys.SWAMP, BiomeKeys.MEADOW,
            BiomeKeys.RIVER, BiomeKeys.WINDSWEPT_HILLS,
            BiomeKeys.WINDSWEPT_GRAVELLY_HILLS,
            BiomeKeys.WINDSWEPT_FOREST,
            BiomeKeys.STONY_PEAKS, BiomeKeys.STONY_SHORE));

        logMappings.add(new BiomeMapping("minecraft:spruce_log",
            BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA,
            BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA,
            BiomeKeys.SNOWY_PLAINS, BiomeKeys.GROVE, BiomeKeys.SNOWY_SLOPES));

        logMappings.add(new BiomeMapping("minecraft:birch_log",
            BiomeKeys.BIRCH_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST));

        logMappings.add(new BiomeMapping("minecraft:acacia_log",
            BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.WINDSWEPT_SAVANNA));

        logMappings.add(new BiomeMapping("minecraft:jungle_log",
            BiomeKeys.JUNGLE, BiomeKeys.SPARSE_JUNGLE, BiomeKeys.BAMBOO_JUNGLE));

        logMappings.add(new BiomeMapping("minecraft:dark_oak_log",
            BiomeKeys.DARK_FOREST, BiomeKeys.PALE_GARDEN));

        logMappings.add(new BiomeMapping("minecraft:mangrove_log",
            BiomeKeys.MANGROVE_SWAMP));

        logMappings.add(new BiomeMapping("minecraft:cherry_log",
            BiomeKeys.CHERRY_GROVE));

        CATEGORY_MAPPINGS.put("logs", logMappings);
        CATEGORY_FALLBACKS.put("logs", "minecraft:sandstone");
    }

    // -----------------------------------------------------------------------

    /**
     * Scanne les biomes dans un carré de rayon donné autour du centre (0, 0).
     */
    public static Set<RegistryKey<Biome>> scanBiomes(ServerWorld world, double radius) {
        Set<RegistryKey<Biome>> found = new HashSet<>();
        int step = Math.max(4, (int) (radius / 10)); // pas adaptatif
        step = Math.min(step, 16);
        int r = (int) radius;

        for (int x = -r; x <= r; x += step) {
            for (int z = -r; z <= r; z += step) {
                RegistryEntry<Biome> biomeEntry = world.getBiomeAccess().getBiome(new BlockPos(x, 64, z));
                biomeEntry.getKey().ifPresent(found::add);
            }
        }

        return found;
    }

    /**
     * Résout une catégorie en un ItemReq concret en fonction des biomes détectés.
     */
    public static StageDefinition.ItemReq resolveCategory(String category, int count,
                                                           Set<RegistryKey<Biome>> presentBiomes) {
        List<BiomeMapping> mappings = CATEGORY_MAPPINGS.get(category);
        if (mappings == null) {
            BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.unknownCategory", category));
            return new StageDefinition.ItemReq("minecraft:cobblestone", count);
        }

        // Chercher le premier mapping dont un biome est présent
        for (BiomeMapping mapping : mappings) {
            for (RegistryKey<Biome> biome : mapping.biomes) {
                if (presentBiomes.contains(biome)) {
                    return new StageDefinition.ItemReq(mapping.itemId, count);
                }
            }
        }

        // Fallback
        String fallback = CATEGORY_FALLBACKS.getOrDefault(category, "minecraft:cobblestone");
        BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.noBiomeMatch", category, fallback));
        return new StageDefinition.ItemReq(fallback, count);
    }

    // -----------------------------------------------------------------------

    private static class BiomeMapping {
        final String itemId;
        final List<RegistryKey<Biome>> biomes;

        @SafeVarargs
        BiomeMapping(String itemId, RegistryKey<Biome>... biomes) {
            this.itemId = itemId;
            this.biomes = List.of(biomes);
        }
    }
}

