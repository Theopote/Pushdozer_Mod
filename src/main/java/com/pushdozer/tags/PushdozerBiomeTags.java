package com.pushdozer.tags;

import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Pushdozer模组的生物群系标签
 * 用于数据驱动的水岸处理规则
 */
public class PushdozerBiomeTags {
    
    /**
     * 具有沙滩特征的生物群系
     * 适用于沙滩模式的水岸处理
     */
    public static final TagKey<Biome> HAS_SANDY_BEACHES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_sandy_beaches"));
    
    /**
     * 具有河流特征的生物群系
     * 适用于河岸模式的水岸处理
     */
    public static final TagKey<Biome> HAS_RIVER_SHORES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_river_shores"));
    
    /**
     * 具有沼泽特征的生物群系
     * 适用于沼泽模式的水岸处理
     */
    public static final TagKey<Biome> HAS_SWAMP_SHORES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_swamp_shores"));
    
    /**
     * 具有沙漠特征的生物群系
     * 适用于沙漠模式的水岸处理
     */
    public static final TagKey<Biome> HAS_DESERT_SHORES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_desert_shores"));
    
    /**
     * 具有沙漠沙滩特征的生物群系
     * 适用于沙漠沙滩模式的水岸处理
     */
    public static final TagKey<Biome> HAS_DESERT_BEACHES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_desert_beaches"));
    
    /**
     * 具有雪地特征的生物群系
     * 适用于雪地模式的水岸处理
     */
    public static final TagKey<Biome> HAS_SNOWY_SHORES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_snowy_shores"));

    /**
     * 具有岩石特征的生物群系
     * 适用于岩石模式的水岸处理
     */
    public static final TagKey<Biome> HAS_ROCKY_SHORES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_rocky_shores"));

    /**
     * 具有植被丰富特征的生物群系
     * 适用于植被丰富模式的水岸处理
     */
    public static final TagKey<Biome> HAS_LUSH_SHORES = TagKey.of(RegistryKeys.BIOME, 
        Identifier.of("pushdozer", "has_lush_shores"));

    public static final TagKey<Biome> HAS_JUNGLE_TREES = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_jungle_trees"));
    public static final TagKey<Biome> HAS_TAIGA_TREES = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_taiga_trees"));
    public static final TagKey<Biome> HAS_BIRCH_TREES = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_birch_trees"));
    public static final TagKey<Biome> HAS_OAK_TREES = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_oak_trees"));
    public static final TagKey<Biome> HAS_DESERT_FLOWERS = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_desert_flowers"));
    public static final TagKey<Biome> HAS_PLAINS_FLOWERS = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_plains_flowers"));
    public static final TagKey<Biome> HAS_FOREST_FLOWERS = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_forest_flowers"));
    public static final TagKey<Biome> HAS_DESERT_GRASS = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_desert_grass"));
    public static final TagKey<Biome> HAS_PLAINS_GRASS = TagKey.of(RegistryKeys.BIOME, Identifier.of("pushdozer", "has_plains_grass"));
} 