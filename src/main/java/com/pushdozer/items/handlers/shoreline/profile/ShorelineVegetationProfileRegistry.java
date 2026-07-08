package com.pushdozer.items.handlers.shoreline.profile;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.vegetation.PlantPools;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.pushdozer.items.handlers.shoreline.profile.WeightedPlantPicker.WeightedPlantChoice.block;
import static com.pushdozer.items.handlers.shoreline.profile.WeightedPlantPicker.WeightedPlantChoice.none;
import static com.pushdozer.items.handlers.shoreline.profile.WeightedPlantPicker.WeightedPlantChoice.pool;

/**
 * 岸线植被 profile 注册表：按水岸类型 + 生物群系 tag + 地面类型匹配加权植物池。
 */
public final class ShorelineVegetationProfileRegistry {

    private static final Map<PushdozerConfig.ShorelineType, List<ShorelineVegetationRule>> RULES = new EnumMap<>(PushdozerConfig.ShorelineType.class);

    static {
        RULES.put(PushdozerConfig.ShorelineType.BEACH, buildBeachRules());
        RULES.put(PushdozerConfig.ShorelineType.EMBANKMENT, buildEmbankmentRules());
        RULES.put(PushdozerConfig.ShorelineType.MUDDY, buildMuddyRules());
        RULES.put(PushdozerConfig.ShorelineType.ROCKY, buildRockyRules());
        RULES.put(PushdozerConfig.ShorelineType.ADAPTIVE, buildAdaptiveRules());
    }

    private ShorelineVegetationProfileRegistry() {
    }

    public static BlockState resolve(
        PushdozerConfig.ShorelineType shorelineType,
        World world,
        BlockPos pos,
        RegistryEntry<Biome> biome,
        BlockState groundBlock,
        Random random
    ) {
        List<ShorelineVegetationRule> rules = RULES.get(shorelineType);
        if (rules == null) {
            return null;
        }
        for (ShorelineVegetationRule rule : rules) {
            if (rule.matches(biome, groundBlock)) {
                return rule.pick(random, world);
            }
        }
        return null;
    }

    private static List<ShorelineVegetationRule> buildBeachRules() {
        List<ShorelineVegetationRule> rules = new ArrayList<>();

        // SAND
        rules.add(rule(ShorelineBiomeGroup.OCEAN_OR_BEACH, ShorelineGroundType.SAND, pool(PlantPools.Pool.AQUATIC, 1f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.SAND,
            block(Blocks.CACTUS, 0.8f), block(Blocks.DEAD_BUSH, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.SAND,
            block(Blocks.SUGAR_CANE, 0.6f), pool(PlantPools.Pool.AQUATIC, 0.4f)));
        rules.add(rule(ShorelineBiomeGroup.JUNGLE_OR_FOREST, ShorelineGroundType.SAND,
            block(Blocks.BAMBOO, 0.4f), block(Blocks.CACTUS, 0.3f), block(Blocks.SUGAR_CANE, 0.3f)));
        rules.add(rule(ShorelineBiomeGroup.SAVANNA_OR_PLAINS, ShorelineGroundType.SAND,
            block(Blocks.CACTUS, 0.5f), block(Blocks.DEAD_BUSH, 0.3f), block(Blocks.SUGAR_CANE, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.BADLANDS, ShorelineGroundType.SAND,
            block(Blocks.DEAD_BUSH, 0.8f), block(Blocks.CACTUS, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.SAND,
            block(Blocks.CACTUS, 0.4f), block(Blocks.SUGAR_CANE, 0.3f), block(Blocks.DEAD_BUSH, 0.3f)));

        // DIRT
        rules.add(rule(ShorelineBiomeGroup.SNOWY, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.1f), none(0.9f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.8f), block(Blocks.DEAD_BUSH, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.DIRT,
            block(Blocks.SUGAR_CANE, 0.7f), pool(PlantPools.Pool.FERN, 0.3f)));
        rules.add(rule(ShorelineBiomeGroup.JUNGLE_OR_FOREST, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.2f), block(Blocks.SHORT_GRASS, 0.2f),
            pool(PlantPools.Pool.FERN, 0.2f), block(Blocks.TALL_GRASS, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.SAVANNA_OR_PLAINS, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.4f), block(Blocks.SHORT_GRASS, 0.3f),
            block(Blocks.TALL_GRASS, 0.2f), pool(PlantPools.Pool.FLOWER, 0.1f)));
        rules.add(rule(ShorelineBiomeGroup.BADLANDS, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.6f), block(Blocks.DEAD_BUSH, 0.4f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.3f), block(Blocks.SHORT_GRASS, 0.3f),
            block(Blocks.TALL_GRASS, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));

        // SANDSTONE
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.SANDSTONE,
            block(Blocks.CACTUS, 0.6f), block(Blocks.DEAD_BUSH, 0.4f)));
        rules.add(rule(ShorelineBiomeGroup.SAVANNA_OR_PLAINS, ShorelineGroundType.SANDSTONE,
            block(Blocks.CACTUS, 0.5f), block(Blocks.DEAD_BUSH, 0.5f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.SANDSTONE,
            block(Blocks.CACTUS, 0.4f), block(Blocks.DEAD_BUSH, 0.6f)));

        return rules;
    }

    private static List<ShorelineVegetationRule> buildEmbankmentRules() {
        List<ShorelineVegetationRule> rules = new ArrayList<>();

        rules.add(rule(ShorelineBiomeGroup.SNOWY, ShorelineGroundType.DIRT, none(1f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.7f), block(Blocks.DEAD_BUSH, 0.3f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.DIRT,
            block(Blocks.SUGAR_CANE, 0.5f), pool(PlantPools.Pool.FERN, 0.3f), block(Blocks.BLUE_ORCHID, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.JUNGLE_OR_FOREST, ShorelineGroundType.DIRT,
            pool(PlantPools.Pool.FERN, 0.4f), block(Blocks.SHORT_GRASS, 0.2f),
            block(Blocks.TALL_GRASS, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.MOUNTAIN_OR_HILL, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.3f), block(Blocks.TALL_GRASS, 0.2f),
            pool(PlantPools.Pool.FERN, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f), block(Blocks.OXEYE_DAISY, 0.1f)));
        rules.add(rule(ShorelineBiomeGroup.NETHER, ShorelineGroundType.DIRT,
            block(Blocks.CRIMSON_ROOTS, 0.5f), block(Blocks.WARPED_ROOTS, 0.2f), none(0.3f)));
        rules.add(rule(ShorelineBiomeGroup.END, ShorelineGroundType.DIRT,
            block(Blocks.CHORUS_PLANT, 0.3f), none(0.7f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.4f), block(Blocks.TALL_GRASS, 0.2f),
            pool(PlantPools.Pool.FERN, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));

        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.SAND,
            block(Blocks.SUGAR_CANE, 0.8f), block(Blocks.KELP, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.SAND,
            block(Blocks.DEAD_BUSH, 0.6f), block(Blocks.CACTUS, 0.4f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.SAND,
            block(Blocks.SHORT_GRASS, 0.6f), block(Blocks.TALL_GRASS, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));

        rules.add(rule(ShorelineBiomeGroup.MOUNTAIN_OR_HILL, ShorelineGroundType.STONE,
            pool(PlantPools.Pool.MOSS, 0.5f), block(Blocks.OXEYE_DAISY, 0.3f), pool(PlantPools.Pool.FLOWER, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.FOREST, ShorelineGroundType.STONE,
            block(Blocks.GLOW_LICHEN, 0.3f), pool(PlantPools.Pool.MOSS, 0.3f), pool(PlantPools.Pool.FERN, 0.4f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.STONE,
            block(Blocks.SUGAR_CANE, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.STONE,
            pool(PlantPools.Pool.MOSS, 0.4f), pool(PlantPools.Pool.FERN, 0.3f), pool(PlantPools.Pool.FLOWER, 0.3f)));

        return rules;
    }

    private static List<ShorelineVegetationRule> buildMuddyRules() {
        List<ShorelineVegetationRule> rules = new ArrayList<>();

        rules.add(rule(ShorelineBiomeGroup.SNOWY, ShorelineGroundType.DIRT, none(1f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.DIRT,
            block(Blocks.SUGAR_CANE, 0.3f), pool(PlantPools.Pool.FERN, 0.2f), block(Blocks.SHORT_GRASS, 0.2f),
            block(Blocks.TALL_GRASS, 0.15f), block(Blocks.BLUE_ORCHID, 0.1f), pool(PlantPools.Pool.FLOWER, 0.05f)));
        rules.add(rule(ShorelineBiomeGroup.JUNGLE, ShorelineGroundType.DIRT,
            pool(PlantPools.Pool.FERN, 0.4f), block(Blocks.SUGAR_CANE, 0.2f), block(Blocks.SHORT_GRASS, 0.15f),
            block(Blocks.TALL_GRASS, 0.15f), pool(PlantPools.Pool.FLOWER, 0.1f)));
        rules.add(rule(ShorelineBiomeGroup.JUNGLE_OR_FOREST, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.3f), pool(PlantPools.Pool.FERN, 0.2f), block(Blocks.TALL_GRASS, 0.2f),
            pool(PlantPools.Pool.FLOWER, 0.15f), block(Blocks.BLUE_ORCHID, 0.15f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.DIRT,
            block(Blocks.DEAD_BUSH, 0.4f), block(Blocks.SHORT_GRASS, 0.3f), block(Blocks.TALL_GRASS, 0.2f),
            pool(PlantPools.Pool.FLOWER, 0.1f)));
        rules.add(rule(ShorelineBiomeGroup.NETHER, ShorelineGroundType.DIRT,
            block(Blocks.CRIMSON_ROOTS, 0.4f), none(0.6f)));
        rules.add(rule(ShorelineBiomeGroup.END, ShorelineGroundType.DIRT,
            block(Blocks.CHORUS_PLANT, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.DIRT,
            pool(PlantPools.Pool.FERN, 0.4f), block(Blocks.SHORT_GRASS, 0.2f),
            block(Blocks.TALL_GRASS, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));

        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.SAND, block(Blocks.SUGAR_CANE, 1f)));
        rules.add(rule(ShorelineBiomeGroup.OCEAN_OR_BEACH, ShorelineGroundType.SAND,
            pool(PlantPools.Pool.AQUATIC, 0.6f), block(Blocks.SEAGRASS, 0.4f)));

        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.STONE,
            block(Blocks.MOSS_CARPET, 0.4f), none(0.6f)));

        return rules;
    }

    private static List<ShorelineVegetationRule> buildRockyRules() {
        List<ShorelineVegetationRule> rules = new ArrayList<>();

        rules.add(rule(ShorelineBiomeGroup.MOUNTAIN_OR_HILL, ShorelineGroundType.STONE,
            pool(PlantPools.Pool.MOSS, 0.5f), block(Blocks.OXEYE_DAISY, 0.3f), pool(PlantPools.Pool.FLOWER, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.STONE,
            block(Blocks.CACTUS, 0.5f), block(Blocks.DEAD_BUSH, 0.5f)));
        rules.add(rule(ShorelineBiomeGroup.OCEAN_OR_BEACH, ShorelineGroundType.STONE,
            pool(PlantPools.Pool.AQUATIC, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.FOREST, ShorelineGroundType.STONE,
            pool(PlantPools.Pool.FERN, 0.3f), none(0.7f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.STONE,
            block(Blocks.SUGAR_CANE, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.NETHER, ShorelineGroundType.STONE,
            block(Blocks.WARPED_ROOTS, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.END, ShorelineGroundType.STONE,
            block(Blocks.CHORUS_PLANT, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.STONE, none(1f)));

        rules.add(rule(ShorelineBiomeGroup.MOUNTAIN_OR_HILL, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.6f), block(Blocks.FERN, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.3f), none(0.7f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.4f), block(Blocks.TALL_GRASS, 0.2f),
            pool(PlantPools.Pool.FERN, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));

        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.SAND, block(Blocks.DEAD_BUSH, 1f)));
        rules.add(rule(ShorelineBiomeGroup.OCEAN_OR_BEACH, ShorelineGroundType.SAND,
            pool(PlantPools.Pool.AQUATIC, 0.5f), block(Blocks.DEAD_BUSH, 0.5f)));

        return rules;
    }

    private static List<ShorelineVegetationRule> buildAdaptiveRules() {
        List<ShorelineVegetationRule> rules = new ArrayList<>();

        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.SAND, block(Blocks.CACTUS, 1f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.SAND, block(Blocks.SUGAR_CANE, 1f)));
        rules.add(rule(ShorelineBiomeGroup.OCEAN_OR_BEACH, ShorelineGroundType.SAND, pool(PlantPools.Pool.AQUATIC, 1f)));
        rules.add(rule(ShorelineBiomeGroup.BADLANDS, ShorelineGroundType.SAND,
            block(Blocks.DEAD_BUSH, 0.4f), block(Blocks.CACTUS, 0.6f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.SAND, block(Blocks.SUGAR_CANE, 1f)));

        rules.add(rule(ShorelineBiomeGroup.SNOWY, ShorelineGroundType.DIRT, none(1f)));
        rules.add(rule(ShorelineBiomeGroup.DESERT, ShorelineGroundType.DIRT,
            block(Blocks.CACTUS, 0.7f), block(Blocks.DEAD_BUSH, 0.3f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.DIRT,
            block(Blocks.SUGAR_CANE, 0.4f), pool(PlantPools.Pool.FERN, 0.3f),
            block(Blocks.BLUE_ORCHID, 0.2f), pool(PlantPools.Pool.FLOWER, 0.1f)));
        rules.add(rule(ShorelineBiomeGroup.JUNGLE_OR_FOREST, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.4f), block(Blocks.TALL_GRASS, 0.2f),
            pool(PlantPools.Pool.FERN, 0.2f), pool(PlantPools.Pool.FLOWER, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.SAVANNA_OR_PLAINS, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.7f), pool(PlantPools.Pool.FLOWER, 0.3f)));
        rules.add(rule(ShorelineBiomeGroup.TAIGA_OR_COLD, ShorelineGroundType.DIRT,
            block(Blocks.FERN, 0.6f), none(0.4f)));
        rules.add(rule(ShorelineBiomeGroup.OCEAN_OR_BEACH, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.8f), pool(PlantPools.Pool.AQUATIC, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.END, ShorelineGroundType.DIRT,
            block(Blocks.CHORUS_PLANT, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.DIRT,
            block(Blocks.SHORT_GRASS, 0.5f), block(Blocks.TALL_GRASS, 0.2f),
            pool(PlantPools.Pool.FERN, 0.15f), pool(PlantPools.Pool.FLOWER, 0.15f)));

        rules.add(rule(ShorelineBiomeGroup.MOUNTAIN_OR_HILL, ShorelineGroundType.STONE,
            pool(PlantPools.Pool.MOSS, 0.5f), block(Blocks.OXEYE_DAISY, 0.3f), pool(PlantPools.Pool.FLOWER, 0.2f)));
        rules.add(rule(ShorelineBiomeGroup.OCEAN_OR_BEACH, ShorelineGroundType.STONE,
            pool(PlantPools.Pool.AQUATIC, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.RIVER_OR_SWAMP, ShorelineGroundType.STONE,
            block(Blocks.SUGAR_CANE, 0.3f), none(0.7f)));
        rules.add(rule(ShorelineBiomeGroup.FOREST, ShorelineGroundType.STONE,
            block(Blocks.FERN, 0.2f), none(0.8f)));
        rules.add(rule(ShorelineBiomeGroup.ANY, ShorelineGroundType.STONE, none(1f)));

        return rules;
    }

    @SafeVarargs
    private static ShorelineVegetationRule rule(
        ShorelineBiomeGroup biomeGroup,
        ShorelineGroundType groundType,
        WeightedPlantPicker.WeightedPlantChoice... choices
    ) {
        return new ShorelineVegetationRule(biomeGroup, groundType, List.of(choices));
    }
}
