package com.pushdozer.items.handlers.planting;

import com.pushdozer.PushdozerMod;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据驱动的植被注册表
 * 用于管理生物群系与植被之间的映射关系
 */
public final class BiomeVegetationRegistry {
    // 树木特性映射
    private static final Map<TagKey<Biome>, RegistryKey<ConfiguredFeature<?, ?>>> TREE_FEATURES = new HashMap<>();
    // 花朵方块映射
    private static final Map<TagKey<Biome>, List<Block>> FLOWER_BLOCKS = new HashMap<>();
    // 草方块映射
    private static final Map<TagKey<Biome>, List<Block>> GRASS_BLOCKS = new HashMap<>();

    // 使用Minecraft常量替代硬编码字符串
    public static final RegistryKey<ConfiguredFeature<?, ?>> DEFAULT_TREE =
            RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "oak"));

    public static final List<Block> DEFAULT_FLOWERS =
            Arrays.asList(Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY);

    public static final List<Block> DEFAULT_GRASS =
            Arrays.asList(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN);

    static {
        initializeTreeFeatures();
        initializeFlowerBlocks();
        initializeGrassBlocks();
    }

    private BiomeVegetationRegistry() {
    }

    private static void initializeTreeFeatures() {
        TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_JUNGLE_TREES,
                RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "jungle_tree")));
        TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_TAIGA_TREES,
                RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "spruce")));
        TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_BIRCH_TREES,
                RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "birch")));
        TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_OAK_TREES,
                RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "oak")));
    }

    private static void initializeFlowerBlocks() {
        // 沙漠花朵
        FLOWER_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_DESERT_FLOWERS,
                Arrays.asList(Blocks.DEAD_BUSH, Blocks.CACTUS));

        // 平原花朵
        FLOWER_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_PLAINS_FLOWERS,
                Arrays.asList(Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER));

        // 森林花朵
        FLOWER_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_FOREST_FLOWERS,
                Arrays.asList(Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM, Blocks.AZURE_BLUET,
                        Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP));

        // 海洋花朵（海草等）
        FLOWER_BLOCKS.put(BiomeTags.IS_OCEAN,
                Arrays.asList(Blocks.SEAGRASS, Blocks.TALL_SEAGRASS));
    }

    private static void initializeGrassBlocks() {
        // 沙漠草
        GRASS_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_DESERT_GRASS,
                Arrays.asList(Blocks.DEAD_BUSH, Blocks.CACTUS));

        // 平原草
        GRASS_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_PLAINS_GRASS,
                Arrays.asList(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN));

        // 海洋草（海草等）
        GRASS_BLOCKS.put(BiomeTags.IS_OCEAN,
                Arrays.asList(Blocks.SEAGRASS, Blocks.TALL_SEAGRASS));
    }

    /**
     * 根据生物群系获取树木特性 (更健壮的版本)
     */
    public static Optional<RegistryKey<ConfiguredFeature<?, ?>>> getTreeFeature(RegistryEntry<Biome> biomeEntry) {
        // 1. 优先通过Tag匹配
        for (Map.Entry<TagKey<Biome>, RegistryKey<ConfiguredFeature<?, ?>>> entry : TREE_FEATURES.entrySet()) {
            if (biomeEntry.isIn(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        // 2. 如果Tag未匹配，提供一个绝对安全的默认值
        PushdozerMod.LOGGER.debug("Biome not matched for tree feature, using default oak");
        return Optional.of(DEFAULT_TREE);
    }

    /**
     * 根据生物群系获取花朵方块 (更健壮的版本)
     */
    public static List<Block> getFlowerBlocks(RegistryEntry<Biome> biomeEntry) {
        for (Map.Entry<TagKey<Biome>, List<Block>> entry : FLOWER_BLOCKS.entrySet()) {
            if (biomeEntry.isIn(entry.getKey())) {
                // ⭐ 添加防御性检查：确保返回的不是 null
                return entry.getValue() != null ? entry.getValue() : DEFAULT_FLOWERS;
            }
        }
        return DEFAULT_FLOWERS;
    }

    /**
     * 根据生物群系获取草方块 (更健壮的版本)
     */
    public static List<Block> getGrassBlocks(RegistryEntry<Biome> biomeEntry) {
        for (Map.Entry<TagKey<Biome>, List<Block>> entry : GRASS_BLOCKS.entrySet()) {
            if (biomeEntry.isIn(entry.getKey())) {
                // ⭐ 添加防御性检查：确保返回的不是 null
                return entry.getValue() != null ? entry.getValue() : DEFAULT_GRASS;
            }
        }
        return DEFAULT_GRASS;
    }
}
