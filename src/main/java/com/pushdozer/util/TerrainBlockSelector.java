package com.pushdozer.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 地形方块选择器
 * 根据给定位置的地形和生物群系，获取最合适的自然填充方块
 */
public final class TerrainBlockSelector {
    private static final Logger LOGGER = LogManager.getLogger(TerrainBlockSelector.class);

    // 私有构造函数，防止工具类被实例化
    private TerrainBlockSelector() {}

    /**
     * 根据给定位置的地形和生物群系，获取一个自然的填充方块。
     * 
     * @param pos 目标位置
     * @param world 所在世界
     * @return 一个合适的自然方块
     */
    public static Block getNaturalTerrainBlock(BlockPos pos, World world) {
        // 1. 寻找坚实的地面位置
        BlockPos groundPos = findSolidGround(pos, world);
        // 如果找不到地面（例如在虚空中），返回一个安全的默认值
        if (groundPos == null) {
            LOGGER.debug("在位置 {} 找不到坚实地面，使用默认方块 STONE", pos);
            return Blocks.STONE;
        }

        BlockState groundState = world.getBlockState(groundPos);
        Block groundBlock = groundState.getBlock();

        // 2. 特殊逻辑处理
        // 如果下方是草方块/泥土，且当前位置暴露在空气中，则使用草方块，否则用泥土
        if (groundState.isIn(BlockTags.DIRT)) { // 使用标签来覆盖 DIRT, GRASS_BLOCK, PODZOL, MYCELIUM 等
            return world.getBlockState(pos.up()).isAir() ? Blocks.GRASS_BLOCK : Blocks.DIRT;
        }
        // 对于沙子和石头，保留原样
        if (groundState.isIn(BlockTags.SAND) || groundState.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
            return groundBlock;
        }
        // 下界方块特殊处理
        if (groundState.isIn(BlockTags.NYLIUM) || groundBlock == Blocks.NETHERRACK) {
            return Blocks.NETHERRACK;
        }

        // 3. 默认情况：使用生物群系的默认顶层方块
        // 这种方法可以自动适应原版和模组添加的生物群系
        return getBiomeTopBlock(pos, world);
    }

    /**
     * 从起始位置向下寻找第一个非空气、非流体的方块。
     * 
     * @param startPos 起始位置
     * @param world 所在世界
     * @return 地面方块的位置，如果未找到则返回 null
     */
    private static BlockPos findSolidGround(BlockPos startPos, World world) {
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(startPos.getX(), startPos.getY(), startPos.getZ());

        // 使用 world.getBottomY() 来适应带有负高度的世界
        while (mutablePos.getY() > world.getBottomY()) {
            mutablePos.move(0, -1, 0);
            BlockState state = world.getBlockState(mutablePos);
            // 寻找第一个不是空气且不是流体的方块
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return mutablePos.toImmutable();
            }
        }
        return null; // 如果循环结束仍未找到，说明到达世界底部
    }

    /**
     * 获取指定位置所在生物群系的默认地表方块。
     * 这种方法比硬编码的Map更健壮且具有更好的兼容性。
     * 
     * @param pos 目标位置
     * @param world 所在世界
     * @return 生物群系的顶层方块
     */
    private static Block getBiomeTopBlock(BlockPos pos, World world) {
        try {
            Biome biome = world.getBiome(pos).value();
            // 获取生物群系的ID
            var biomeId = world.getRegistryManager()
                    .getOrThrow(RegistryKeys.BIOME)
                    .getId(biome);
            
            if (biomeId != null) {
                String biomeName = biomeId.toString();
                // 根据生物群系名称返回合适的表面方块
                return getBlockForBiome(biomeName);
            }
            
            // 如果无法获取生物群系ID，返回默认值
            return Blocks.GRASS_BLOCK;
        } catch (Exception e) {
            // 如果获取生物群系信息失败，记录警告并返回默认值
            LOGGER.warn("无法获取位置 {} 的生物群系信息，使用默认方块 GRASS_BLOCK", pos, e);
            return Blocks.GRASS_BLOCK;
        }
    }
    
    /**
     * 根据生物群系名称返回合适的表面方块
     * 
     * @param biomeName 生物群系名称
     * @return 合适的表面方块
     */
    private static Block getBlockForBiome(String biomeName) {
        // 使用 switch 表达式，更现代和高效
        return switch (biomeName) {
            case "minecraft:ocean", "minecraft:deep_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean" -> 
                // 海洋生物群系：返回海底的固体方块，而不是水
                getOceanFloorBlock(biomeName);
            case "minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna", "minecraft:savanna_plateau" -> Blocks.GRASS_BLOCK;
            case "minecraft:desert", "minecraft:desert_hills", "minecraft:beach" -> Blocks.SAND;
            case "minecraft:mountains", "minecraft:mountain_edge", "minecraft:wooded_mountains", "minecraft:stone_shore" -> Blocks.STONE;
            case "minecraft:forest", "minecraft:wooded_hills", "minecraft:birch_forest", "minecraft:birch_forest_hills" -> Blocks.DIRT;
            case "minecraft:taiga", "minecraft:taiga_hills", "minecraft:snowy_taiga", "minecraft:snowy_taiga_hills" -> Blocks.PODZOL;
            case "minecraft:swamp", "minecraft:swamp_hills" -> Blocks.MUD;
            case "minecraft:badlands", "minecraft:wooded_badlands_plateau", "minecraft:badlands_plateau" -> Blocks.RED_SAND;
            case "minecraft:snowy_tundra", "minecraft:snowy_mountains" -> Blocks.SNOW_BLOCK;
            case "minecraft:mushroom_fields", "minecraft:mushroom_field_shore" -> Blocks.MYCELIUM;
            case "minecraft:nether_wastes" -> Blocks.NETHERRACK;
            case "minecraft:the_end" -> Blocks.END_STONE;
            default -> {
                // 对于未知的生物群系，根据名称模式推断
                if (biomeName.contains("ocean") || biomeName.contains("sea")) {
                    // 未知的海洋生物群系：返回海底方块
                    yield getOceanFloorBlock(biomeName);
                } else if (biomeName.contains("snowy") || biomeName.contains("ice")) {
                    yield Blocks.SNOW_BLOCK;
                } else if (biomeName.contains("desert") || biomeName.contains("badlands")) {
                    yield Blocks.SAND;
                } else if (biomeName.contains("mountains") || biomeName.contains("hills")) {
                    yield Blocks.STONE;
                } else if (biomeName.contains("forest") || biomeName.contains("taiga")) {
                    yield Blocks.DIRT;
                } else if (biomeName.contains("swamp")) {
                    yield Blocks.MUD;
                } else if (biomeName.contains("nether")) {
                    yield Blocks.NETHERRACK;
                } else if (biomeName.contains("end")) {
                    yield Blocks.END_STONE;
                } else {
                    // 默认返回草方块
                    yield Blocks.GRASS_BLOCK;
                }
            }
        };
    }
    
    /**
     * 获取海洋生物群系的海底方块
     * 根据不同的海洋类型返回合适的海底固体方块
     * 
     * @param biomeName 生物群系名称
     * @return 海底的固体方块
     */
    private static Block getOceanFloorBlock(String biomeName) {
        return switch (biomeName) {
            case "minecraft:ocean" -> Blocks.STONE; // 普通海洋：石头
            case "minecraft:deep_ocean" -> Blocks.STONE; // 深海：石头
            case "minecraft:frozen_ocean" -> Blocks.STONE; // 冰冻海洋：石头
            case "minecraft:deep_frozen_ocean" -> Blocks.STONE; // 深冰冻海洋：石头
            case "minecraft:warm_ocean" -> Blocks.SAND; // 暖水海洋：沙子
            case "minecraft:lukewarm_ocean" -> Blocks.SAND; // 温水海洋：沙子
            case "minecraft:cold_ocean" -> Blocks.GRAVEL; // 冷水海洋：沙砾
            case "minecraft:deep_cold_ocean" -> Blocks.GRAVEL; // 深冷水海洋：沙砾
            case "minecraft:deep_lukewarm_ocean" -> Blocks.SAND; // 深温水海洋：沙子
            case "minecraft:deep_warm_ocean" -> Blocks.SAND; // 深暖水海洋：沙子
            default -> {
                // 对于未知的海洋生物群系，根据名称推断
                if (biomeName.contains("warm") || biomeName.contains("lukewarm")) {
                    yield Blocks.SAND; // 暖水/温水：沙子
                } else if (biomeName.contains("cold")) {
                    yield Blocks.GRAVEL; // 冷水：沙砾
                } else if (biomeName.contains("frozen")) {
                    yield Blocks.STONE; // 冰冻：石头
                } else {
                    yield Blocks.STONE; // 默认：石头
                }
            }
        };
    }
}